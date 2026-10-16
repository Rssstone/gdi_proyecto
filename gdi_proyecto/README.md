# GDI – Gestión Dental Integral · Módulo de Citas

Sistema web para **Clínica Imagen** con arquitectura de 3 capas (RNF-07):

| Capa | Tecnología | Carpeta |
|---|---|---|
| Presentación | HTML5, CSS3, JavaScript | `frontend/` |
| Negocio | Java 17 + Spring Boot (API REST) | `backend/` |
| Datos | MySQL 8 | `init.sql` |

Requerimientos cubiertos en este avance: **RF-06** (registrar y consultar citas), **RF-07** (disponibilidad de la
agenda), **RF-08** (confirmación y recordatorio por WhatsApp) y una base de **RF-11/RF-12** (inicio de sesión y
opciones según el rol).

## Cómo ejecutarlo

```bash
docker compose up --build
```

- Interfaz: http://localhost
- API: http://localhost:8080/api

Usuarios de demostración (contraseña `12345`; se cifra con BCrypt en el primer inicio de sesión):

| Correo | Rol | Puede |
|---|---|---|
| recepcion@clinica.com | Recepcionista | Agendar, reprogramar, confirmar, cancelar, registrar pacientes |
| ana@clinica.com | Odontólogo | Consultar agenda y marcar citas como completadas / inasistencia |
| admin@clinica.com | Administrador | Todo lo anterior y eliminar citas |

> Si ya habías levantado la base de datos antes, ejecuta `docker compose down -v` para que `init.sql` vuelva a
> cargarse con los datos nuevos. (Si no, Spring agrega solo la columna `duracion_minutos` con valor 30.)

Sin Docker: levanta MySQL en `localhost:3306` (usuario/contraseña `root`), ejecuta `init.sql`, corre
`./mvnw spring-boot:run` dentro de `backend/` y abre `frontend/index.html` (o con Live Server).

## Reglas de negocio de la agenda

- No se pueden agendar citas en **fechas pasadas** ni en **horas que ya pasaron** (se usa la hora de la Ciudad de
  México del servidor, no la de la computadora).
- Solo se agenda dentro del **horario de atención**: lunes a viernes 09:00–19:00, sábado 09:00–14:00, domingo
  cerrado, en intervalos de 30 minutos (configurable en `application.properties`, prefijo `clinica.`).
- **Cada cita tiene duración** (30 min a 4 h, en bloques de 30). Una extracción de 1 h ocupa dos bloques; la
  interfaz sugiere la duración según el motivo (limpieza 1 h, endodoncia 1 h 30 min, etc.) y se puede ajustar.
- La cita completa debe **terminar antes del cierre** (una cita de 1 h no puede empezar a las 18:30).
- **No se permiten empalmes**: el rango completo de la cita (inicio + duración) no puede encimarse con otra cita
  activa del mismo odontólogo o del mismo paciente. Cancelar una cita libera todos sus bloques.
- Estados: `Programada → Confirmada → Completada / Inasistencia`, o `Cancelada`.
  - Confirmar o cancelar solo antes de la hora de la cita; completada o inasistencia solo después.
  - Reprogramar (cambiar fecha, hora u odontólogo) regresa la cita a `Programada` para volver a confirmarla.
  - Cancelada, completada e inasistencia son estados finales.
  - Las citas completadas no se pueden eliminar (forman parte del historial del paciente).

En la interfaz, la agenda lo muestra con colores: **verde** disponible, **rojo** ocupado, **gris tachado** hora
pasada y **rayado** día cerrado; los bloques que ocupa una cita larga aparecen con borde punteado (“continúa”).
En el formulario solo se eligen horarios en verde; en **ámbar** aparecen los que están libres pero no alcanzan
para la duración elegida.

## API REST

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/citas?desde&hasta&estado&odontologoId&pacienteId&q` | Lista y filtra citas |
| GET | `/api/citas/{id}` | Consulta una cita |
| POST | `/api/citas` | Agenda una cita (201) |
| PUT | `/api/citas/{id}` | Edita o reprograma una cita |
| PATCH | `/api/citas/{id}/estado` | Cambia el estado `{ "estado": "Confirmada", "nota": "..." }` |
| DELETE | `/api/citas/{id}` | Elimina una cita (204) |
| GET | `/api/agenda/disponibilidad?fecha&odontologoId&duracion` | Horarios del día: `DISPONIBLE`, `OCUPADO`, `PASADO`, `INSUFICIENTE` (no cabe la duración) |
| GET | `/api/agenda/resumen?desde&hasta&odontologoId` | Estado de cada día: `DISPONIBLE`, `LLENO`, `PASADO`, `CERRADO` |
| GET | `/api/agenda/configuracion` | Horario de la clínica y hora actual del servidor |
| GET / POST / PUT | `/api/pacientes`, `/api/pacientes/{id}` | Búsqueda, alta y edición de pacientes |
| GET | `/api/usuarios/odontologos` | Lista de odontólogos |
| POST | `/api/auth/login` | Inicio de sesión |

Cuerpo de `POST/PUT /api/citas`:

```json
{ "pacienteId": 1, "odontologoId": 1, "fecha": "2026-10-20", "hora": "10:30", "duracionMinutos": 60, "motivo": "Limpieza dental", "notasRecepcion": "" }
```

Los errores regresan JSON uniforme con el código HTTP correspondiente (400 datos inválidos, 404 no encontrado,
409 horario ocupado, 422 regla de negocio):

```json
{ "status": 409, "mensaje": "El horario 10:30 del 2026-10-20 ya está ocupado…", "campos": null }
```

## Pruebas

```bash
cd backend && ./mvnw test
```

Las pruebas usan H2 en memoria y un reloj fijo para validar fechas/horas pasadas, horario de atención, empalmes,
transiciones de estado, disponibilidad e inicio de sesión.

## Pendiente para siguientes entregas

- Autorización en el backend con Spring Security + tokens: por ahora los permisos por rol se aplican en la
  interfaz, pero la API todavía no valida quién hace cada petición.
- Envío automático de recordatorios (hoy se abre WhatsApp con el mensaje ya redactado).
