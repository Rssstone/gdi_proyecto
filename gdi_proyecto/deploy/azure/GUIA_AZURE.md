# Despliegue de GDI en Azure (2 máquinas virtuales)

| Capa | Dónde corre | Detalle |
|---|---|---|
| Presentación (HTML, CSS, JS) | Laptop del equipo | Live Server o el navegador |
| Negocio (Spring Boot, puerto 8080) | VM **vm-gdi-backend** | Ubuntu 24.04, servicio `gdi-backend` |
| Datos (MySQL 8, puerto 3306) | VM **gdi-mysql-omegatech** | Ubuntu 24.04, servicio `mysql`. Solo acepta la red privada; respaldo diario. |

```
Laptop ──HTTP :8080──▶ vm-gdi-backend ──JDBC (TLS) por IP privada :3306──▶ gdi-mysql-omegatech
                         └──────────── misma red virtual de Azure ────────────┘
```

> ¿Por qué MySQL en una VM y no en *Azure Database for MySQL*? Con **Azure for Students** el servicio administrado
> no se pudo crear en la región permitida (Chile Central). En una VM funciona igual y no cambia nada del código.

Nadie del equipo debe compartir contraseñas por chat. Las dos VMs van en el grupo **rg-gdi** y en la región
**Chile Central**, o la que permita la directiva de la suscripción.

> **Costo:** las 750 horas gratis al mes cubren una sola VM B1s encendida todo el mes. Con dos VMs, la segunda
> cuesta alrededor de US$8 al mes, o menos si las **detienen** cuando no las usan.

---

## Paso 1. VM de la base de datos: `gdi-mysql-omegatech`

1. **Máquinas virtuales → Crear → Máquina virtual de Azure**.
   - Grupo `rg-gdi`, región **Chile Central**.
   - Imagen **Ubuntu Server 24.04 LTS**, tamaño **Standard_B1s**.
   - Autenticación con **contraseña**, usuario `azureuser`.
   - Puertos de entrada: **SSH (22)**.
2. En la pestaña **Redes** se crea una red virtual nueva, por ejemplo `gdi-mysql-omegatech-vnet`. Anota su nombre.
3. **No abras el puerto 3306** a internet. Dentro de la red virtual Azure ya permite el tráfico entre VMs.

## Paso 2. VM del backend: `vm-gdi-backend`

1. Igual que la anterior: grupo `rg-gdi`, **Chile Central**, Ubuntu 24.04, B1s, usuario `azureuser`, **SSH (22)**.
2. **⚠️ Pestaña Redes → Red virtual: elige la MISMA red del paso 1** (`gdi-mysql-omegatech-vnet`) y su subred
   `default`. No crees otra.
3. Ya creada:
   - **Redes → Configuración de red → Crear regla de puerto → Regla de puerto de entrada**: puerto `8080`, TCP,
     Permitir, nombre `Backend-8080`.
   - **Información general → IP pública → Configuración → Etiqueta de nombre DNS**: `gdi-clinica-omegatech`.
     Queda `gdi-clinica-omegatech.chilecentral.cloudapp.azure.com`.

## Paso 3. Instalar MySQL (en `gdi-mysql-omegatech`)

Desde la terminal de la laptop (PowerShell en Windows):

```bash
ssh azureuser@IP-PUBLICA-DE-LA-VM-MYSQL
git clone -b claude/sleepy-ptolemy-9z8bjy https://github.com/Rssstone/gdi_proyecto.git
cd gdi_proyecto/gdi_proyecto
sudo bash deploy/azure/instalar-mysql.sh
```

> Si los cambios ya están en `main`, clona sin el `-b …`. Si el repositorio es privado, en la contraseña de GitHub
> se pega un **token personal** (GitHub → Settings → Developer settings → Personal access tokens).

El script hace lo siguiente:

1. Pone la hora de la Ciudad de México.
2. Instala MySQL 8 ajustado para 1 GB de RAM.
3. Crea la base `gdi_db` y el usuario `gdi`, con una contraseña aleatoria que solo puede usarse desde la red
   privada `10.x`.
4. Carga `init.sql`.
5. Programa un respaldo diario.

**Al final muestra tres datos. Anótalos para el paso 4:** la **IP privada** (por ejemplo `10.0.0.4`), el
**usuario** (`gdi`) y la **contraseña**. Se pueden volver a ver con `sudo cat /etc/gdi/mysql.env` y `hostname -I`.

## Paso 4. Instalar el backend (en `vm-gdi-backend`)

```bash
ssh azureuser@IP-PUBLICA-DE-LA-VM-BACKEND
git clone -b claude/sleepy-ptolemy-9z8bjy https://github.com/Rssstone/gdi_proyecto.git
cd gdi_proyecto/gdi_proyecto
sudo bash deploy/azure/instalar-backend.sh
```

El script pregunta:

- **IP privada de la VM de MySQL:** la del paso 3, por ejemplo `10.0.0.4`. Si la dejas vacía, instala MySQL en esta
  misma VM.
- **Usuario** (`gdi`) y **contraseña:** los del paso 3.

Después instala Java 17, revisa la conexión con MySQL, compila el backend y lo deja como servicio `gdi-backend`, que
arranca solo. Para comprobarlo, abre en el navegador
`http://gdi-clinica-omegatech.chilecentral.cloudapp.azure.com:8080/api/agenda/configuracion`: debe mostrar un JSON.

## Paso 5. Interfaz en la laptop

En `frontend/js/config.js`:

```js
window.GDI_API_URL = 'http://gdi-clinica-omegatech.chilecentral.cloudapp.azure.com:8080/api';
```

Abre `index.html` con **Live Server** y entra con `recepcion@clinica.com` / `12345`. Cada integrante hace lo mismo
en su laptop; **no hay que registrar la IP de nadie**.

## Mantenimiento

| Qué | Dónde | Cómo |
|---|---|---|
| Subir cambios del backend | VM backend | `cd gdi_proyecto/gdi_proyecto && sudo bash deploy/azure/actualizar-backend.sh` |
| Ver en vivo lo que hace el backend (cada petición, citas agendadas, rechazos) | VM backend | `sudo journalctl -u gdi-backend -f` (salir con Ctrl + C) |
| Estado / errores del backend | VM backend | `sudo systemctl status gdi-backend` · `sudo journalctl -u gdi-backend -n 80` |
| Entrar a la base de datos | VM MySQL | `sudo mysql gdi_db` |
| Respaldo manual | VM MySQL | `sudo /etc/cron.daily/gdi-respaldo` (queda en `/var/backups/gdi`) |
| Ahorrar crédito | Portal | **Detener** las dos VMs cuando no se usen. Al encenderlas, enciende primero la de MySQL. |

## Problemas comunes

| Síntoma | Solución |
|---|---|
| Las dos VMs muestran la misma IP privada (por ejemplo `172.16.0.4`) | Quedaron en redes virtuales distintas. En la VM de MySQL abre el 3306 **solo** para la IP pública del backend (regla de entrada con origen = esa IP), corre `sudo DB_RED=IP-PUBLICA-BACKEND bash deploy/azure/instalar-mysql.sh` y en el backend usa la IP **pública** de MySQL. |
| El script del backend dice "No hay conexión con MySQL" | Las VMs no están en la misma red virtual, la IP privada es incorrecta o MySQL está apagado (`sudo systemctl status mysql` en la VM de MySQL). |
| *"policy violation"* al crear una VM | La región no está permitida: usa **Chile Central** u otra de la lista de la directiva. |
| La página dice "No hay conexión con el servidor" | Falta la regla del puerto **8080** en la VM del backend, la VM está apagada o `config.js` tiene otra dirección. |
| Ver o consultar la base de datos | Entra por SSH a la VM de MySQL y usa `sudo mysql gdi_db` (por ejemplo `SELECT * FROM citas;`). No se abre el 3306 a internet. |
