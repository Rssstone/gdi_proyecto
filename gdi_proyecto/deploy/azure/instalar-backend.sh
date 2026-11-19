#!/usr/bin/env bash
# Instala el backend de GDI en una máquina virtual Ubuntu de Azure y lo deja corriendo como servicio.
# Uso (dentro de la VM, en la carpeta gdi_proyecto/gdi_proyecto del repositorio):
#   sudo bash deploy/azure/instalar-backend.sh
# Pide el servidor de Azure Database for MySQL, el usuario y la contraseña. Se puede volver a ejecutar.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
DESTINO=/opt/gdi
CONFIG=/etc/gdi/backend.env

if [ "$(id -u)" -ne 0 ]; then
    echo "Ejecuta el script con sudo: sudo bash deploy/azure/instalar-backend.sh" >&2
    exit 1
fi

echo "== 1/6 Memoria de intercambio (la VM B1s solo tiene 1 GB de RAM) =="
if ! swapon --show | grep -q .; then
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 2/6 Java 17 y cliente de MySQL =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk-headless mysql-client curl >/dev/null

echo "== 3/6 Datos de la base de datos en Azure =="
if [ -f "$CONFIG" ]; then
    # Reutiliza lo capturado en una instalación anterior.
    set -a; . "$CONFIG"; set +a
fi
DB_HOST="${DB_HOST:-}"; DB_USER="${DB_USER:-}"; DB_PASS="${DB_PASS:-}"
[ -n "$DB_HOST" ] || read -rp "Servidor MySQL (ej. gdi-mysql.mysql.database.azure.com): " DB_HOST
[ -n "$DB_USER" ] || read -rp "Usuario administrador de MySQL: " DB_USER
[ -n "$DB_PASS" ] || { read -rsp "Contraseña de MySQL: " DB_PASS; echo; }

export MYSQL_PWD="$DB_PASS"   # así la contraseña no aparece en la lista de procesos
MYSQL=(mysql --ssl-mode=REQUIRED -h "$DB_HOST" -u "$DB_USER")
if ! "${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1; then
    echo "No hay conexión con MySQL. Revisa el nombre del servidor, el usuario, la contraseña y que en" >&2
    echo "'Redes' del servidor MySQL esté permitida la IP de esta máquina virtual." >&2
    exit 1
fi

TABLAS=$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='gdi_db'" 2>/dev/null)
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

echo "== 5/6 Configuración del servicio =="
mkdir -p "$(dirname "$CONFIG")"
{
    printf 'DB_HOST=%q\nDB_USER=%q\nDB_PASS=%q\n' "$DB_HOST" "$DB_USER" "$DB_PASS"
    printf 'SPRING_DATASOURCE_URL=%q\n' "jdbc:mysql://$DB_HOST:3306/gdi_db?sslMode=REQUIRED"
    printf 'SPRING_DATASOURCE_USERNAME=%q\nSPRING_DATASOURCE_PASSWORD=%q\n' "$DB_USER" "$DB_PASS"
    printf 'TZ=America/Mexico_City\n'
} > "$CONFIG"
chmod 600 "$CONFIG"

cat > /etc/systemd/system/gdi-backend.service <<UNIT
[Unit]
Description=GDI - Backend Spring Boot (Clinica Imagen)
After=network-online.target
Wants=network-online.target

[Service]
EnvironmentFile=$CONFIG
ExecStart=/usr/bin/java -Xmx512m -jar $DESTINO/gdi-backend.jar
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable gdi-backend >/dev/null 2>&1
systemctl restart gdi-backend

echo "== 6/6 Esperando a que el backend responda =="
for _ in $(seq 1 60); do
    if curl -sf http://localhost:8080/api/agenda/configuracion >/dev/null; then
        IP=$(curl -s --max-time 5 https://ifconfig.me || true)
        echo
        echo "Listo. El backend está corriendo en el puerto 8080."
        echo "En la laptop, en frontend/js/config.js pon:  window.GDI_API_URL = 'http://${IP:-IP-PUBLICA-DE-LA-VM}:8080/api';"
        exit 0
    fi
    sleep 3
done
echo "El backend no respondió. Revisa el registro con:  sudo journalctl -u gdi-backend -n 80" >&2
exit 1
