/* Comunicación con la API REST de Spring Boot. */

// Servido por nginx (Docker, puerto 80) o por el mismo backend: rutas relativas.
// Abierto como archivo o con otro servidor de desarrollo (p. ej. Live Server): backend local en 8080.
const API_BASE = location.protocol.startsWith('http') && ['', '80', '443', '8080'].includes(location.port)
    ? '/api'
    : 'http://localhost:8080/api';

class ErrorApi extends Error {
    constructor(mensaje, status, campos) {
        super(mensaje);
        this.status = status;
        this.campos = campos || {};
    }
}

async function peticion(ruta, { metodo = 'GET', cuerpo, params } = {}) {
    const url = new URL(API_BASE + ruta, location.href);
    Object.entries(params || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v); });

    let res;
    try {
        res = await fetch(url, {
            method: metodo,
            headers: cuerpo ? { 'Content-Type': 'application/json' } : {},
            body: cuerpo ? JSON.stringify(cuerpo) : undefined,
        });
    } catch {
        throw new ErrorApi('No hay conexión con el servidor. Verifica que el backend esté encendido.', 0);
    }
    if (res.status === 204) return null;
    const datos = await res.json().catch(() => null);
    if (!res.ok) throw new ErrorApi(datos?.mensaje || `Error del servidor (${res.status})`, res.status, datos?.campos);
    return datos;
}

const Api = {
    login: (email, password) => peticion('/auth/login', { metodo: 'POST', cuerpo: { email, password } }),
    odontologos: () => peticion('/usuarios/odontologos'),

    configuracion: () => peticion('/agenda/configuracion'),
    disponibilidad: (fecha, odontologoId, excluirCitaId) =>
        peticion('/agenda/disponibilidad', { params: { fecha, odontologoId, excluirCitaId } }),
    resumen: (desde, hasta, odontologoId) => peticion('/agenda/resumen', { params: { desde, hasta, odontologoId } }),

    citas: (filtros) => peticion('/citas', { params: filtros }),
    cita: (id) => peticion(`/citas/${id}`),
    crearCita: (datos) => peticion('/citas', { metodo: 'POST', cuerpo: datos }),
    actualizarCita: (id, datos) => peticion(`/citas/${id}`, { metodo: 'PUT', cuerpo: datos }),
    cambiarEstado: (id, estado, nota) => peticion(`/citas/${id}/estado`, { metodo: 'PATCH', cuerpo: { estado, nota } }),
    eliminarCita: (id) => peticion(`/citas/${id}`, { metodo: 'DELETE' }),

    pacientes: (q) => peticion('/pacientes', { params: { q } }),
    crearPaciente: (datos) => peticion('/pacientes', { metodo: 'POST', cuerpo: datos }),
    actualizarPaciente: (id, datos) => peticion(`/pacientes/${id}`, { metodo: 'PUT', cuerpo: datos }),
};
