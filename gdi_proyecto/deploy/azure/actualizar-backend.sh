#!/usr/bin/env bash
# Descarga los últimos cambios del repositorio, recompila el backend y lo reinicia.
# Uso (dentro de la VM, en la carpeta gdi_proyecto/gdi_proyecto):  sudo bash deploy/azure/actualizar-backend.sh
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
git -C "$RAIZ" pull --ff-only
(cd "$RAIZ/backend" && ./mvnw -q -DskipTests clean package)
cp "$RAIZ"/backend/target/citas-*.jar /opt/gdi/gdi-backend.jar
systemctl restart gdi-backend
# Si el registro legible ya se activó, también se actualiza el visor.
[ -f /usr/local/bin/gdi-registro ] && install -m 755 "$RAIZ/deploy/azure/gdi-registro.py" /usr/local/bin/gdi-registro
echo "Backend actualizado. Estado: $(systemctl is-active gdi-backend)"
