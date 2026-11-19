#!/usr/bin/env bash
# Deja listo el registro legible de GDI en esta VM y hace que se muestre SOLO al entrar por SSH.
#   VM del backend: muestra cada petición (qué se hizo, ruta, resultado) y lo que cambió.
#   VM de MySQL:   activa el registro de consultas de MySQL y lo traduce (NUEVO / MODIFICADO / BORRADO / CONSULTA).
# Uso (una sola vez en cada VM, en la carpeta gdi_proyecto/gdi_proyecto):  sudo bash deploy/azure/activar-registro.sh
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
[ "$(id -u)" -eq 0 ] || { echo "Ejecuta con sudo: sudo bash deploy/azure/activar-registro.sh" >&2; exit 1; }

install -m 755 "$RAIZ/deploy/azure/gdi-registro.py" /usr/local/bin/gdi-registro

if [ -f /etc/systemd/system/gdi-backend.service ]; then
    QUE="backend"
elif command -v mysqld >/dev/null; then
    QUE="MySQL"
    cat > /etc/mysql/mysql.conf.d/zz-gdi-registro.cnf <<'CNF'
[mysqld]
# Registro de todas las consultas para verlas con 'sudo gdi-registro'.
general_log = ON
general_log_file = /var/log/mysql/gdi-sql.log
log_timestamps = SYSTEM
CNF
    cat > /etc/logrotate.d/gdi-sql <<'ROT'
/var/log/mysql/gdi-sql.log {
    daily
    rotate 3
    compress
    missingok
    notifempty
    copytruncate
}
ROT
    systemctl restart mysql
else
    echo "Esta VM no tiene ni el backend de GDI ni MySQL instalados." >&2
    exit 1
fi

# Al entrar por SSH se abre el registro; con Ctrl + C se sale a la terminal normal.
USUARIO="${SUDO_USER:-azureuser}"
BASHRC="$(getent passwd "$USUARIO" | cut -d: -f6)/.bashrc"
if ! grep -q '>>> GDI registro >>>' "$BASHRC" 2>/dev/null; then
    cat >> "$BASHRC" <<'BRC'
# >>> GDI registro >>>
# Muestra el registro de GDI al entrar por SSH. Para entrar sin verlo:  ssh -t usuario@ip "GDI_SIN_REGISTRO=1 bash"
if [[ $- == *i* && -n "${SSH_TTY:-}" && -z "${GDI_SIN_REGISTRO:-}" ]]; then
    export GDI_SIN_REGISTRO=1
    sudo /usr/local/bin/gdi-registro
fi
# <<< GDI registro <<<
BRC
    chown "$USUARIO" "$BASHRC"
fi
echo "Listo: registro de $QUE activado. Se mostrará solo cada vez que entres por SSH (Ctrl + C para salir)."
echo "Para verlo ahora:  sudo gdi-registro      Solo cambios:  sudo gdi-registro cambios"
