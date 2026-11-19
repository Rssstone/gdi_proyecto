#!/usr/bin/env python3
"""Muestra en vivo, en español y con colores, lo que pasa en GDI.

En la VM del backend lee el registro del servicio gdi-backend (cada petición, qué cambió y por qué se rechazó).
En la VM de MySQL lee el registro de consultas de MySQL y traduce el SQL: NUEVO / MODIFICADO / BORRADO / CONSULTA.

Uso:  sudo gdi-registro            (todo)
      sudo gdi-registro cambios    (oculta las consultas de solo lectura)
Salir: Ctrl + C
"""
import os
import re
import shutil
import subprocess
import sys

COLOR = sys.stdout.isatty()
def c(codigo, texto):
    return f'\033[{codigo}m{texto}\033[0m' if COLOR else texto
GRIS, VERDE, AMARILLO, ROJO, CIAN, AZUL, NEGRITA = '90', '32', '33', '31', '36', '34', '1'

SOLO_CAMBIOS = 'cambios' in sys.argv[1:]
LOG_SQL = '/var/log/mysql/gdi-sql.log'


def seguir(comando):
    proc = subprocess.Popen(comando, stdout=subprocess.PIPE, text=True, errors='replace')
    try:
        for linea in proc.stdout:
            yield linea.rstrip('\n')
    finally:
        proc.terminate()


# ---------------------------------------------------------------- backend
def backend():
    print(c(NEGRITA, 'Registro del BACKEND de GDI (en vivo)') + '   ·   Ctrl + C para salir')
    print(c(GRIS, 'Columnas: hora | TIPO | qué se hizo | método y ruta | resultado | tiempo | IP de quien lo pidió'))
    print(c(GRIS, 'CONSULTA = solo lee · CAMBIO = guarda/modifica/borra · SESION = inicio de sesión · RECHAZO = error (debajo, el motivo)'))
    print()
    ocultar_detalle = False
    for linea in seguir(['journalctl', '-u', 'gdi-backend', '-f', '-n', '40', '-o', 'cat']):
        if linea.startswith('           -> '):
            if not ocultar_detalle:
                print(c(CIAN, linea))
            continue
        partes = linea.split(' ', 1)
        cuerpo = partes[1] if len(partes) > 1 else linea
        ocultar_detalle = False
        if cuerpo.startswith('CONSULTA'):
            if SOLO_CAMBIOS:
                ocultar_detalle = True
                continue
            print(c(GRIS, linea))
        elif cuerpo.startswith('CAMBIO'):
            print(c(VERDE, linea))
        elif cuerpo.startswith('SESION'):
            print(c(AZUL, linea))
        elif cuerpo.startswith('RECHAZO'):
            print(c(AMARILLO, linea))
        elif re.search(r'\b(ERROR|Exception|Caused by)\b', linea):
            print(c(ROJO, linea))
        elif 'Started CitasApplication' in linea:
            print(c(VERDE, partes[0] + ' BACKEND ENCENDIDO y listo para recibir peticiones'))
        elif SOLO_CAMBIOS:
            continue
        else:
            print(c(GRIS, linea))


# ---------------------------------------------------------------- MySQL
def valores(texto):
    """Separa 'a,'b,c',3' respetando comillas."""
    salida, actual, comilla, i = [], '', False, 0
    while i < len(texto):
        ch = texto[i]
        if ch == '\\' and comilla and i + 1 < len(texto):
            actual += texto[i:i + 2]; i += 2; continue
        if ch == "'":
            comilla = not comilla
        if ch == ',' and not comilla:
            salida.append(actual.strip()); actual = ''
        else:
            actual += ch
        i += 1
    salida.append(actual.strip())
    return salida


def corto(v, n=60):
    v = v.strip()
    return v if len(v) <= n else v[:n] + f'… ({len(v)} caracteres)'


def sin_alias(t):
    return re.sub(r'\b\w+_\d+\.', '', t)


def traducir(sql):
    s = ' '.join(sql.split())
    b = s.lower()
    m = re.match(r'insert into (\w+) \((.*?)\) values \((.*)\)$', s, re.I)
    if m:
        cols, vals = [x.strip() for x in m.group(2).split(',')], valores(m.group(3))
        pares = ', '.join(f'{col}={corto(val)}' for col, val in zip(cols, vals) if val.lower() != 'null')
        return VERDE, 'NUEVO', f'registro en {m.group(1)}: {pares}'
    m = re.match(r'update (\w+) set (.*?) where (.*)$', s, re.I)
    if m:
        pares = ', '.join(corto(p) for p in valores(m.group(2)))
        return AMARILLO, 'MODIFICADO', f'{m.group(1)} donde {sin_alias(m.group(3))}: {pares}'
    m = re.match(r'delete from (\w+)(?: \w+)? where (.*)$', s, re.I)
    if m:
        return ROJO, 'BORRADO', f'{m.group(1)} donde {sin_alias(m.group(2))}'
    if b.startswith('select'):
        tablas = list(dict.fromkeys(re.findall(r'\b(?:from|join)\s+(\w+)', s, re.I)))
        if not tablas or any(t.lower() in ('information_schema', 'mysql', 'performance_schema') for t in tablas):
            return None
        donde = re.search(r'\bwhere (.*?)(?: order by| group by| limit|$)', s, re.I)
        filtro = f'  donde {corto(sin_alias(donde.group(1)), 160)}' if donde else ''
        return GRIS, 'CONSULTA', f'lee {", ".join(tablas)}{filtro}'
    return None


def mysql():
    if not os.path.exists(LOG_SQL):
        sys.exit(f'No existe {LOG_SQL}. Ejecuta antes:  sudo bash deploy/azure/activar-registro.sh')
    print(c(NEGRITA, 'Registro de MySQL de GDI (en vivo)') + '   ·   Ctrl + C para salir')
    print(c(GRIS, 'NUEVO = INSERT · MODIFICADO = UPDATE · BORRADO = DELETE · CONSULTA = SELECT · CONEXIÓN = el backend se conectó'))
    print()
    entrada = re.compile(r'^(\d{4}-\d\d-\d\dT(\d\d:\d\d:\d\d))\S*\s+\d+\s+(\w+)\s*(.*)$')
    ultima_conexion = None
    for linea in seguir(['tail', '-n', '60', '-F', LOG_SQL]):
        m = entrada.match(linea)
        if not m:
            continue
        hora, tipo, texto = m.group(2), m.group(3), m.group(4)
        if tipo == 'Connect':
            m2 = re.match(r'(\w[\w.-]*)@(\S+) on (\S*) using (.*)', texto)
            if m2 and m2.group(4) == 'Socket':
                continue                      # conexiones internas de la propia VM (mantenimiento de Ubuntu)
            desc = (f'usuario {m2.group(1)} desde {m2.group(2)} a la base {m2.group(3)} ({m2.group(4)})' if m2 else texto)
            if (hora[:5], desc) == ultima_conexion:
                continue                      # el backend abre varias conexiones a la vez: se muestra una sola
            ultima_conexion = (hora[:5], desc)
            print(c(AZUL, f'{hora} CONEXIÓN   {desc}'))
            continue
        if tipo != 'Query':
            continue
        r = traducir(texto)
        if not r or (SOLO_CAMBIOS and r[1] == 'CONSULTA'):
            continue
        color, etiqueta, desc = r
        ancho = shutil.get_terminal_size((160, 20)).columns
        print(c(color, f'{hora} {etiqueta:<10} {desc}'[:max(ancho * 3, 200)]))


if __name__ == '__main__':
    try:
        if shutil.which('mysqld') and not os.path.exists('/etc/systemd/system/gdi-backend.service'):
            mysql()
        else:
            backend()
    except KeyboardInterrupt:
        print('\nSaliste del registro. Para volver a verlo:  sudo gdi-registro   (o  sudo gdi-registro cambios)')
