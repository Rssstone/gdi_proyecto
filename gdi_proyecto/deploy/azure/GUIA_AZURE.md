# Despliegue de GDI en Azure

| Pieza | Dónde corre | Servicio de Azure |
|---|---|---|
| Interfaz (HTML, CSS, JS) | Laptop del equipo | — (se abre con Live Server o el navegador) |
| Backend (Spring Boot, puerto 8080) | Nube | **Máquina virtual** Ubuntu 24.04, tamaño B1s |
| Base de datos (MySQL 8) | Nube | **Azure Database for MySQL – Servidor flexible**, tamaño B1ms |

```
Laptop (navegador) ──HTTP :8080──▶ VM de Azure (Spring Boot) ──MySQL :3306 con TLS──▶ Azure Database for MySQL
```

> Nadie del equipo debe compartir contraseñas por chat. Cada paso se hace desde el portal con la cuenta del IPN
> (suscripción **Azure for Students**).

---

## Paso 1. Grupo de recursos

1. En el buscador del portal escribe **Grupos de recursos** → **Crear**.
2. Suscripción: **Azure for Students**. Nombre: `rg-gdi`. Región: **Chile Central**, o la que permita la suscripción.
   La suscripción de estudiante del IPN solo deja crear recursos en algunas regiones. Para ver la lista: buscador →
   **Directiva** → **Asignaciones** → *Allowed resource deployment regions* → **Parámetros**. Si al crear sale
   *"The template deployment failed because of policy violation"*, la región no está permitida.
3. **Revisar y crear** → **Crear**.

## Paso 2. Servidor de base de datos MySQL

1. Buscador → **Azure Database for MySQL** (es la pantalla "Servidores de Azure Database for MySQL") → **Crear**.
2. Elige **Servidor flexible**. El "servidor único" ya está retirado; no lo uses.
3. Pestaña **Aspectos básicos**:
   - Grupo de recursos: `rg-gdi`.
   - Nombre del servidor: `gdi-mysql-omegatech`. Debe ser único en todo Azure; si está ocupado, agrega un número.
   - Región: **Chile Central**, o la región permitida (la misma para MySQL y la VM).
   - Versión de MySQL: **8.0**.
   - Tipo de carga de trabajo: **Para proyectos de desarrollo o pasatiempos**.
   - Proceso y almacenamiento → **Configurar servidor**:
     - Nivel **Ampliable (Burstable)**, tamaño **Standard_B1ms**.
     - Almacenamiento **20 GiB**.
     - Alta disponibilidad **deshabilitada**.
     - Copia de seguridad: deja **7 días**.
   - Método de autenticación: **Solo autenticación de MySQL**.
   - Usuario administrador: `gdiadmin`. Contraseña: una fuerte; anótenla en un lugar seguro.
4. Pestaña **Redes**:
   - Método de conectividad: **Acceso público (direcciones IP permitidas)**.
   - Marca **Permitir el acceso público desde cualquier servicio de Azure dentro de Azure a este servidor**.
     Así la máquina virtual puede conectarse.
   - Opcional: **+ Agregar dirección IP del cliente actual**, si quieren abrir la base desde su laptop con MySQL
     Workbench.
5. **Revisar y crear** → **Crear**. Tarda de 5 a 10 minutos.
6. Cuando termine, entra al recurso y copia el **Nombre del servidor** de la página Información general. Se ve
   como `gdi-mysql-omegatech.mysql.database.azure.com`.

> No hace falta crear la base `gdi_db` a mano: el script del paso 5 la crea y carga `init.sql` la primera vez.

## Paso 3. Máquina virtual para el backend

1. Buscador → **Máquinas virtuales** → **Crear** → **Máquina virtual de Azure**.
2. **Aspectos básicos**:
   - Grupo de recursos: `rg-gdi`. Nombre: `vm-gdi-backend`. Región: la misma que el servidor MySQL (**Chile Central**).
   - Imagen: **Ubuntu Server 24.04 LTS – x64 Gen2**.
   - Tamaño: **Standard_B1s** (1 vCPU, 1 GiB). Si no aparece, usa **Standard_B2ats_v2** o **B1ms**.
   - Tipo de autenticación: **Contraseña**. Usuario: `azureuser`, más una contraseña fuerte.
   - Puertos de entrada públicos: **Permitir los puertos seleccionados** → **SSH (22)**.
3. **Revisar y crear** → **Crear**.
4. Al terminar, entra a la VM. En **Redes → Configuración de red** pulsa **Crear regla de puerto** → **Regla de
   puerto de entrada**:
   - Puerto de destino `8080`, protocolo **TCP**, acción **Permitir**, nombre `Backend-8080`.
5. Recomendado: en **Información general**, da clic en la **IP pública** → **Configuración**:
   - En **Etiqueta de nombre DNS** escribe `gdi-clinica-omegatech` y guarda.
   - Así la dirección será fija, por ejemplo `gdi-clinica-omegatech.chilecentral.cloudapp.azure.com`, aunque se apague
     la VM.

## Paso 4. Entrar a la máquina virtual

Desde la terminal de la laptop (PowerShell en Windows):

```bash
ssh azureuser@IP-PUBLICA-DE-LA-VM
```

Escribe `yes` la primera vez y luego la contraseña de la VM.

## Paso 5. Instalar el backend (un solo comando)

Dentro de la VM:

```bash
git clone https://github.com/Rssstone/gdi_proyecto.git
cd gdi_proyecto/gdi_proyecto
sudo bash deploy/azure/instalar-backend.sh
```

> Si el repositorio es privado, GitHub pedirá usuario y contraseña. En la contraseña se pega un **token
> personal**, que se crea en GitHub → Settings → Developer settings → Personal access tokens. También pueden
> hacer público el repositorio.

El script pregunta tres datos:

- **Servidor MySQL:** el nombre copiado en el paso 2, `…mysql.database.azure.com`.
- **Usuario:** `gdiadmin`.
- **Contraseña** de MySQL.

Después hace lo siguiente:

1. Instala Java 17 y el cliente de MySQL.
2. Activa 2 GB de memoria de intercambio.
3. Carga `init.sql` si la base está vacía.
4. Compila el backend.
5. Lo deja como servicio `gdi-backend`, que arranca solo aunque se reinicie la VM.

Al final muestra la línea que hay que poner en la laptop.

Para comprobarlo, abre esta dirección en el navegador. Debe mostrar un JSON con la hora de la clínica:
`http://IP-PUBLICA-DE-LA-VM:8080/api/agenda/configuracion`

## Paso 6. Interfaz en la laptop

1. Abre la carpeta `gdi_proyecto/frontend` en VS Code.
2. Edita `js/config.js` y pon la IP o el nombre DNS de la VM:

   ```js
   window.GDI_API_URL = 'http://gdi-clinica-omegatech.chilecentral.cloudapp.azure.com:8080/api';
   ```

3. Abre `index.html` con **Live Server** (o con doble clic). Entra con `recepcion@clinica.com` / `12345`.

Cada integrante hace lo mismo en su laptop: todos usan la misma base de datos en la nube.

## Mantenimiento

| Qué | Cómo |
|---|---|
| Subir cambios del backend | En la VM: `cd gdi_proyecto/gdi_proyecto && sudo bash deploy/azure/actualizar-backend.sh` |
| Ver si está corriendo | `sudo systemctl status gdi-backend` |
| Ver errores | `sudo journalctl -u gdi-backend -n 80` |
| Ahorrar crédito | Detén la VM y el servidor MySQL desde el portal (**Detener**) cuando no los usen. Al volver a encenderlos el backend arranca solo. |

## Problemas comunes

| Síntoma | Solución |
|---|---|
| "No hay conexión con MySQL" en el script | En el servidor MySQL → **Redes**, verifica la casilla de servicios de Azure o agrega la IP pública de la VM. |
| La página dice "No hay conexión con el servidor" | Falta la regla del puerto **8080** (paso 3.4), la VM está apagada o `config.js` tiene otra IP. |
| La IP de la VM cambió | Usa la etiqueta DNS del paso 3.5, o actualiza `config.js` con la IP nueva. |
| *"policy violation"* al crear | La región no está permitida para Azure for Students: usa **Chile Central** u otra de la lista de la directiva. |
| No aparece el tamaño B1s o B1ms | Esa región no lo ofrece: elige el tamaño más barato disponible (B2ats_v2, B1ms). |
