#!/usr/bin/env bash
# Instala GDI en una máquina virtual Ubuntu de Azure: MySQL 8 y el backend de Spring Boot en la misma VM,
# cada uno como servicio que arranca solo. La interfaz corre en la laptop y llama a http://IP-VM:8080/api.
#
# Uso (dentro de la VM, en la carpeta gdi_proyecto/gdi_proyecto del repositorio):
#   sudo bash deploy/azure/instalar-backend.sh
# Se puede volver a ejecutar: no borra datos y reutiliza la contraseña ya generada.
#
# Opcional: para usar un servidor MySQL externo (por ejemplo Azure Database for MySQL) en lugar del local:
#   sudo DB_HOST=servidor.mysql.database.azure.com DB_USER=gdiadmin DB_PASS='...' bash deploy/azure/instalar-backend.sh
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
DESTINO=/opt/gdi
CONFIG=/etc/gdi/backend.env

if [ "$(id -u)" -ne 0 ]; then
    echo "Ejecuta el script con sudo: sudo bash deploy/azure/instalar-backend.sh" >&2
    exit 1
fi

# Arranca un servicio con systemd (o con 'service' si la máquina no usa systemd).
iniciar_servicio() {
    systemctl enable "$1" >/dev/null 2>&1 || true
    systemctl restart "$1" 2>/dev/null || service "$1" restart
}

echo "== 1/6 Zona horaria y memoria de intercambio (la VM B1s solo tiene 1 GB de RAM) =="
timedatectl set-timezone America/Mexico_City 2>/dev/null || ln -sf /usr/share/zoneinfo/America/Mexico_City /etc/localtime
if ! swapon --show | grep -q .; then
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

if [ -f "$CONFIG" ]; then
    # Reutiliza lo configurado en una instalación anterior.
    set -a; . "$CONFIG"; set +a
fi
DB_HOST="${DB_HOST:-localhost}"
LOCAL=$([ "$DB_HOST" = "localhost" ] && echo si || echo no)

echo "== 2/6 Java 17 y MySQL =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
if [ "$LOCAL" = si ]; then
    apt-get install -y -qq openjdk-17-jdk-headless mysql-server curl openssl >/dev/null
    # MySQL con poca memoria para que quepa junto al backend; solo escucha dentro de la VM.
    cat > /etc/mysql/mysql.conf.d/gdi.cnf <<'CNF'
[mysqld]
bind-address = 127.0.0.1
innodb_buffer_pool_size = 128M
performance_schema = OFF
max_connections = 40
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
CNF
    # MySQL toma la hora del sistema, que ya quedó en America/Mexico_City.
    iniciar_servicio mysql
    for _ in $(seq 1 30); do mysqladmin -u root ping >/dev/null 2>&1 && break; sleep 2; done
else
    apt-get install -y -qq openjdk-17-jdk-headless mysql-client curl >/dev/null
fi

echo "== 3/6 Base de datos gdi_db =="
if [ "$LOCAL" = si ]; then
    DB_USER="${DB_USER:-gdi}"
    DB_PASS="${DB_PASS:-$(openssl rand -base64 18 | tr -d '/+=')}"
    # root entra por el socket de Ubuntu (sin contraseña) solo desde la propia VM.
    mysql -u root <<SQL
CREATE DATABASE IF NOT EXISTS gdi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON gdi_db.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL
    MYSQL=(mysql -u root)
    URL="jdbc:mysql://localhost:3306/gdi_db"
else
    DB_USER="${DB_USER:-}"; DB_PASS="${DB_PASS:-}"
    [ -n "$DB_USER" ] || read -rp "Usuario administrador de MySQL: " DB_USER
    [ -n "$DB_PASS" ] || { read -rsp "Contraseña de MySQL: " DB_PASS; echo; }
    export MYSQL_PWD="$DB_PASS"   # así la contraseña no aparece en la lista de procesos
    MYSQL=(mysql --ssl-mode=REQUIRED -h "$DB_HOST" -u "$DB_USER")
    URL="jdbc:mysql://$DB_HOST:3306/gdi_db?sslMode=REQUIRED"
fi
if ! "${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1; then
    echo "No hay conexión con MySQL en $DB_HOST. Revisa el servicio (sudo systemctl status mysql) o los datos de acceso." >&2
    exit 1
fi
TABLAS=$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='gdi_db'")
if [ "$TABLAS" = "0" ]; then
    echo "   La base gdi_db está vacía: se cargan las tablas y los datos de ejemplo (init.sql)."
    "${MYSQL[@]}" < "$RAIZ/init.sql"
else
    echo "   La base gdi_db ya tiene $TABLAS tablas: no se toca."
fi

echo "== 4/6 Compilando el backend (tarda unos minutos la primera vez) =="
(cd "$RAIZ/backend" && chmod +x mvnw && ./mvnw -q -DskipTests clean package)
mkdir -p "$DESTINO"
cp "$RAIZ"/backend/target/citas-*.jar "$DESTINO/gdi-backend.jar"

echo "== 5/6 Configuración de los servicios =="
mkdir -p "$(dirname "$CONFIG")"
{
    printf 'DB_HOST=%q\nDB_USER=%q\nDB_PASS=%q\n' "$DB_HOST" "$DB_USER" "$DB_PASS"
    printf 'SPRING_DATASOURCE_URL=%q\n' "$URL"
    printf 'SPRING_DATASOURCE_USERNAME=%q\nSPRING_DATASOURCE_PASSWORD=%q\n' "$DB_USER" "$DB_PASS"
    printf 'TZ=America/Mexico_City\n'
} > "$CONFIG"
chmod 600 "$CONFIG"

cat > /etc/systemd/system/gdi-backend.service <<UNIT
[Unit]
Description=GDI - Backend Spring Boot (Clinica Imagen)
After=network-online.target mysql.service
Wants=network-online.target

[Service]
EnvironmentFile=$CONFIG
ExecStart=/usr/bin/java -Xmx384m -jar $DESTINO/gdi-backend.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload 2>/dev/null || true
iniciar_servicio gdi-backend

if [ "$LOCAL" = si ]; then
    # Respaldo diario de la base de datos (se guardan los últimos 7 días en /var/backups/gdi).
    mkdir -p /var/backups/gdi
    cat > /etc/cron.daily/gdi-respaldo <<'CRON'
#!/bin/sh
mysqldump -u root --single-transaction gdi_db | gzip > /var/backups/gdi/gdi_db_$(date +%F).sql.gz
find /var/backups/gdi -name 'gdi_db_*.sql.gz' -mtime +7 -delete
CRON
    chmod +x /etc/cron.daily/gdi-respaldo
fi

echo "== 6/6 Esperando a que el backend responda =="
for _ in $(seq 1 80); do
    if curl -sf http://localhost:8080/api/agenda/configuracion >/dev/null; then
        IP=$(curl -s --max-time 5 https://ifconfig.me || true)
        echo
        echo "Listo. MySQL y el backend están corriendo; el backend responde en el puerto 8080."
        echo "En la laptop, en frontend/js/config.js pon:  window.GDI_API_URL = 'http://${IP:-IP-PUBLICA-DE-LA-VM}:8080/api';"
        exit 0
    fi
    sleep 3
done
echo "El backend no respondió. Revisa el registro con:  sudo journalctl -u gdi-backend -n 80" >&2
exit 1
