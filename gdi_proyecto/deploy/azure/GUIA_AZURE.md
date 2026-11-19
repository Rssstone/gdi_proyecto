# Despliegue de GDI en Azure

| Capa | Dónde corre | Detalle |
|---|---|---|
| Presentación (HTML, CSS, JS) | Laptop del equipo | Se abre con Live Server o con el navegador |
| Negocio (Spring Boot, puerto 8080) | **Máquina virtual de Azure** | Ubuntu 24.04, servicio `gdi-backend` |
| Datos (MySQL 8) | **La misma máquina virtual** | Servicio `mysql`. Solo acepta conexiones desde la propia VM y se respalda a diario. |

```
Laptop (navegador) ──HTTP :8080──▶ VM de Azure [ Spring Boot ──localhost:3306──▶ MySQL 8 ]
```

> ¿Por qué MySQL va dentro de la VM? Con la suscripción **Azure for Students**, el servicio administrado
> *Azure Database for MySQL* no se pudo crear en la región permitida (Chile Central). Así no cambia nada del código y
> todo cabe en las horas gratis de una VM. Las 3 capas siguen separadas: cada una es un proceso distinto.

Nadie del equipo debe compartir contraseñas por chat.

---

## Paso 1. Crear la máquina virtual

1. Buscador del portal → **Máquinas virtuales** → **Crear** → **Máquina virtual de Azure**.
2. **Aspectos básicos**:
   - Suscripción: **Azure for Students**.
   - Grupo de recursos: `rg-gdi`; si no existe, **Crear nuevo**.
   - Nombre: `vm-gdi`.
   - Región: **Chile Central**, o la que permita la suscripción. Para ver la lista: buscador → **Directiva** →
     **Asignaciones** → *Allowed resource deployment regions* → **Parámetros**.
   - Opciones de disponibilidad: **No se requiere redundancia de infraestructura**.
   - Imagen: **Ubuntu Server 24.04 LTS – x64 Gen2**.
   - Tamaño: **Standard_B1s** (1 vCPU, 1 GiB). Es el que entra en las 750 horas gratis. Si no aparece, usa
     **Standard_B1ms** o **Standard_B2ats_v2**.
   - Tipo de autenticación: **Contraseña**. Usuario: `azureuser`, más una contraseña fuerte.
   - Puertos de entrada públicos: **Permitir los puertos seleccionados** → **SSH (22)**.
3. **Discos**: deja **SSD estándar**.
4. **Revisar y crear** → **Crear**. Tarda unos 2 minutos.

## Paso 2. Abrir el puerto del backend y fijar la dirección

1. Entra a la VM. En **Redes → Configuración de red** pulsa **Crear regla de puerto** → **Regla de puerto de
   entrada**:
   - Puerto de destino `8080`, protocolo **TCP**, acción **Permitir**, nombre `Backend-8080`.
   - **No abras el puerto 3306**: MySQL no debe quedar expuesto a internet.
2. En **Información general**, da clic en la **IP pública** → **Configuración**. En **Etiqueta de nombre DNS**
   escribe `gdi-clinica-omegatech` y guarda.
   - La dirección queda fija aunque se apague la VM, por ejemplo
     `gdi-clinica-omegatech.chilecentral.cloudapp.azure.com`.

## Paso 3. Entrar a la máquina virtual

Desde la terminal de la laptop (PowerShell en Windows):

```bash
ssh azureuser@IP-PUBLICA-DE-LA-VM
```

Escribe `yes` la primera vez y luego la contraseña de la VM.

## Paso 4. Instalar todo (un solo comando)

Dentro de la VM:

```bash
git clone https://github.com/Rssstone/gdi_proyecto.git
cd gdi_proyecto/gdi_proyecto
sudo bash deploy/azure/instalar-backend.sh
```

> Si los cambios todavía no están en `main`, clona la rama así:
> `git clone -b claude/sleepy-ptolemy-9z8bjy https://github.com/Rssstone/gdi_proyecto.git`
>
> Si el repositorio es privado, GitHub pedirá usuario y contraseña. En la contraseña se pega un **token personal**
> (GitHub → Settings → Developer settings → Personal access tokens).

El script no pregunta nada. Tarda de 5 a 10 minutos y hace esto:

1. Pone la hora de la Ciudad de México y activa 2 GB de memoria de intercambio.
2. Instala **Java 17** y **MySQL 8**. MySQL queda ajustado para poca memoria y solo escucha dentro de la VM.
3. Crea la base `gdi_db` y un usuario `gdi` con una contraseña aleatoria, guardada en `/etc/gdi/backend.env`.
   Carga `init.sql` con las tablas y los datos de ejemplo.
4. Compila el backend y lo deja como servicio `gdi-backend`. Arranca solo aunque se reinicie la VM.
5. Programa un **respaldo diario** de la base en `/var/backups/gdi`; se guardan 7 días.

Al final muestra la línea que hay que poner en la laptop. Para comprobar que funciona, abre en el navegador
`http://IP-PUBLICA-DE-LA-VM:8080/api/agenda/configuracion`: debe mostrar un JSON con la hora de la clínica.

## Paso 5. Interfaz en la laptop

1. Abre la carpeta `gdi_proyecto/frontend` en VS Code.
2. Edita `js/config.js` y pon el nombre DNS (o la IP) de la VM:

   ```js
   window.GDI_API_URL = 'http://gdi-clinica-omegatech.chilecentral.cloudapp.azure.com:8080/api';
   ```

3. Abre `index.html` con **Live Server**. Entra con `recepcion@clinica.com` / `12345`.

Cada integrante hace lo mismo en su laptop: todos usan la misma base de datos en la nube. Para esto **no hay que
registrar la IP de nadie**.

## Mantenimiento

| Qué | Cómo (dentro de la VM) |
|---|---|
| Subir cambios del backend | `cd gdi_proyecto/gdi_proyecto && sudo bash deploy/azure/actualizar-backend.sh` |
| Ver si están corriendo | `sudo systemctl status gdi-backend mysql` |
| Ver errores del backend | `sudo journalctl -u gdi-backend -n 80` |
| Entrar a la base de datos | `sudo mysql gdi_db` |
| Respaldo manual | `sudo /etc/cron.daily/gdi-respaldo` (queda en `/var/backups/gdi`) |
| Ahorrar horas gratis | En el portal, **Detener** la VM cuando no la usen. Al encenderla, MySQL y el backend arrancan solos. |

## Problemas comunes

| Síntoma | Solución |
|---|---|
| *"policy violation"* al crear la VM | La región no está permitida: usa **Chile Central** u otra de la lista de la directiva. |
| No aparece el tamaño B1s | Elige el más barato disponible (B1ms o B2ats_v2). |
| La página dice "No hay conexión con el servidor" | Falta la regla del puerto **8080**, la VM está apagada o `config.js` tiene otra dirección. |
| El backend no arranca | `sudo journalctl -u gdi-backend -n 80` y `sudo systemctl status mysql`. Si dice memoria insuficiente, reinicia la VM. |
| Quieren ver la base con MySQL Workbench desde la laptop | Usa la opción **SSH** de Workbench ("Standard TCP/IP over SSH") con `azureuser` y la contraseña de la VM; en la parte de MySQL usa `127.0.0.1`, usuario `gdi` y la contraseña que aparece con `sudo cat /etc/gdi/backend.env`. Así no se abre el puerto 3306. |
