#!/usr/bin/env bash
# Instala MySQL 8 en la máquina virtual de base de datos de GDI y carga init.sql.
# Solo acepta conexiones de la red privada de Azure (la VM del backend), nunca desde internet.
#
# Uso (dentro de la VM de MySQL, en la carpeta gdi_proyecto/gdi_proyecto del repositorio):
#   sudo bash deploy/azure/instalar-mysql.sh
# Al final muestra la IP privada, el usuario y la contraseña que pide el script del backend.
# Se puede volver a ejecutar: no borra datos y reutiliza la contraseña ya generada.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
CONFIG=/etc/gdi/mysql.env
RED="${DB_RED:-10.%}"   # direcciones privadas de la red virtual de Azure

if [ "$(id -u)" -ne 0 ]; then
    echo "Ejecuta el script con sudo: sudo bash deploy/azure/instalar-mysql.sh" >&2
    exit 1
fi

echo "== 1/4 Zona horaria y memoria de intercambio =="
timedatectl set-timezone America/Mexico_City 2>/dev/null || ln -sf /usr/share/zoneinfo/America/Mexico_City /etc/localtime
if ! swapon --show | grep -q .; then
    fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 2/4 Instalando MySQL 8 =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq mysql-server openssl >/dev/null
cat > /etc/mysql/mysql.conf.d/zz-gdi.cnf <<'CNF'
[mysqld]
# Escucha en la red privada; el grupo de seguridad de Azure no expone el 3306 a internet.
bind-address = 0.0.0.0
innodb_buffer_pool_size = 256M
performance_schema = OFF
max_connections = 60
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
CNF
systemctl enable mysql >/dev/null 2>&1 || true
systemctl restart mysql 2>/dev/null || service mysql restart
for _ in $(seq 1 30); do mysqladmin -u root ping >/dev/null 2>&1 && break; sleep 2; done

echo "== 3/4 Base de datos gdi_db y usuario para el backend =="
[ -f "$CONFIG" ] && { set -a; . "$CONFIG"; set +a; }
DB_USER="${DB_USER:-gdi}"
DB_PASS="${DB_PASS:-$(openssl rand -base64 18 | tr -d '/+=')}"
mysql -u root <<SQL
CREATE DATABASE IF NOT EXISTS gdi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'$RED' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'$RED' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON gdi_db.* TO '$DB_USER'@'$RED';
FLUSH PRIVILEGES;
SQL
mkdir -p "$(dirname "$CONFIG")"
printf 'DB_USER=%q\nDB_PASS=%q\n' "$DB_USER" "$DB_PASS" > "$CONFIG"
chmod 600 "$CONFIG"

TABLAS=$(mysql -u root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='gdi_db'")
if [ "$TABLAS" = "0" ]; then
    echo "   La base gdi_db está vacía: se cargan las tablas y los datos de ejemplo (init.sql)."
    mysql -u root < "$RAIZ/init.sql"
else
    echo "   La base gdi_db ya tiene $TABLAS tablas: no se toca."
fi

echo "== 4/4 Respaldo diario =="
mkdir -p /var/backups/gdi
cat > /etc/cron.daily/gdi-respaldo <<'CRON'
#!/bin/sh
mysqldump -u root --single-transaction gdi_db | gzip > /var/backups/gdi/gdi_db_$(date +%F).sql.gz
find /var/backups/gdi -name 'gdi_db_*.sql.gz' -mtime +7 -delete
CRON
chmod +x /etc/cron.daily/gdi-respaldo

IP_PRIVADA=$(hostname -I | awk '{print $1}')
echo
echo "Listo. MySQL está corriendo. Anota estos datos para instalar el backend en la otra VM:"
echo "   IP privada de esta VM : $IP_PRIVADA"
echo "   Usuario               : $DB_USER"
echo "   Contraseña            : $DB_PASS"
echo "(Se pueden volver a consultar con:  sudo cat $CONFIG  y  hostname -I)"
