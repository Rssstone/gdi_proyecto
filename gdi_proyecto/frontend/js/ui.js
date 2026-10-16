/* Utilidades de interfaz: iconos, formato de fechas, notificaciones y ventana modal. */

const ICONOS = {
    calendario: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
    lista: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    usuarios: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
    mas: '<path d="M12 5v14M5 12h14"/>',
    izq: '<path d="m15 18-6-6 6-6"/>',
    der: '<path d="m9 18 6-6-6-6"/>',
    cerrar: '<path d="M18 6 6 18M6 6l12 12"/>',
    buscar: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>',
    reloj: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
    salir: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    x: '<circle cx="12" cy="12" r="10"/><path d="m15 9-6 6M9 9l6 6"/>',
    editar: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    basura: '<path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
    telefono: '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z"/>',
    mensaje: '<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5Z"/>',
    alerta: '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"/><path d="M12 9v4M12 17h.01"/>',
    info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
    candado: '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
    usuario: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    diente: '<path d="M12 5.5C10.5 4 8.5 3 6.5 3.5 4 4.2 3 6.8 3.6 9.5c.5 2.2 1.4 3.6 1.9 6 .5 2.6.8 5.5 2.5 5.5 1.6 0 1.7-3.5 2.5-5.3.4-.9.9-1.2 1.5-1.2s1.1.3 1.5 1.2c.8 1.8.9 5.3 2.5 5.3 1.7 0 2-2.9 2.5-5.5.5-2.4 1.4-3.8 1.9-6 .6-2.7-.4-5.3-2.9-6-2-.5-4 .5-5.5 2Z"/>',
    siguiente: '<path d="M5 12h14M12 5l7 7-7 7"/>',
};

function icono(nombre) {
    return `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">${ICONOS[nombre] || ''}</svg>`;
}

/** Inserta el icono indicado en data-icon al inicio de cada elemento. */
function pintarIconos(raiz = document) {
    raiz.querySelectorAll('[data-icon]').forEach(el => {
        if (!el.querySelector(':scope > svg.ic')) el.insertAdjacentHTML('afterbegin', icono(el.dataset.icon));
    });
}

function esc(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/* ---------- Fechas (se trabaja con cadenas AAAA-MM-DD para evitar desfases de zona horaria) ---------- */
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

const Fecha = {
    aDate(iso) { const [a, m, d] = iso.split('-').map(Number); return new Date(a, m - 1, d); },
    aIso(date) {
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    },
    sumarDias(iso, n) { const d = Fecha.aDate(iso); d.setDate(d.getDate() + n); return Fecha.aIso(d); },
    larga(iso) { const d = Fecha.aDate(iso); return `${DIAS[d.getDay()]} ${d.getDate()} de ${MESES[d.getMonth()]}`; },
    larguisima(iso) { return `${Fecha.larga(iso)} de ${iso.slice(0, 4)}`; },
    corta(iso) { const d = Fecha.aDate(iso); return `${d.getDate()} ${MESES[d.getMonth()].slice(0, 3)} ${d.getFullYear()}`; },
    relativa(iso, hoy) {
        if (iso === hoy) return 'Hoy';
        if (iso === Fecha.sumarDias(hoy, 1)) return 'Mañana';
        if (iso === Fecha.sumarDias(hoy, -1)) return 'Ayer';
        return null;
    },
    duracion(minutos) {
        const h = Math.floor(minutos / 60), m = minutos % 60;
        return [h ? `${h} h` : '', m ? `${m} min` : ''].filter(Boolean).join(' ');
    },
    hora12(hhmm) {
        const [h, m] = hhmm.split(':').map(Number);
        return `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')} ${h < 12 ? 'a. m.' : 'p. m.'}`;
    },
};

function sumarMinutos(hhmm, minutos) {
    const [h, m] = hhmm.split(':').map(Number);
    const total = h * 60 + m + minutos;
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

/* ---------- Notificaciones ---------- */
function toast(mensaje, tipo = 'ok') {
    const el = document.createElement('div');
    el.className = `toast toast--${tipo}`;
    el.innerHTML = `${icono(tipo === 'error' ? 'alerta' : tipo === 'ok' ? 'check' : 'info')}<span>${esc(mensaje)}</span>`;
    document.getElementById('toasts').appendChild(el);
    setTimeout(() => { el.classList.add('toast--saliendo'); setTimeout(() => el.remove(), 300); }, tipo === 'error' ? 6000 : 3500);
}

/* ---------- Ventana modal ---------- */
const Modal = {
    abierto: false,
    abrir(titulo, html, { chico = false } = {}) {
        const modal = document.getElementById('modal');
        document.getElementById('modal-titulo').textContent = titulo;
        const cuerpo = document.getElementById('modal-cuerpo');
        cuerpo.innerHTML = html;
        modal.querySelector('.modal__caja').classList.toggle('modal__caja--chica', chico);
        modal.hidden = false;
        this.abierto = true;
        pintarIconos(modal);
        const primero = cuerpo.querySelector('input:not([type=hidden]), select, textarea');
        if (primero && window.matchMedia('(min-width: 761px)').matches) setTimeout(() => primero.focus(), 50);
        return cuerpo;
    },
    cerrar() {
        document.getElementById('modal').hidden = true;
        document.getElementById('modal-cuerpo').innerHTML = '';
        this.abierto = false;
    },
    /** Diálogo de confirmación; resuelve con el texto de la nota (si se pidió) o null si se canceló. */
    confirmar({ titulo, mensaje, textoBoton = 'Aceptar', peligro = false, pedirNota = null }) {
        return new Promise(resolve => {
            const cuerpo = this.abrir(titulo, `
                <p style="margin:0 0 14px">${mensaje}</p>
                ${pedirNota ? `<label class="campo"><span class="campo__etiqueta">${esc(pedirNota)}</span>
                    <textarea id="confirmar-nota" maxlength="300" placeholder="Opcional"></textarea></label>` : ''}
                <div class="acciones-form">
                    <button class="btn btn--borde" data-accion="no">Volver</button>
                    <button class="btn ${peligro ? 'btn--peligro' : 'btn--primario'}" data-accion="si">${esc(textoBoton)}</button>
                </div>`, { chico: true });
            cuerpo.querySelector('[data-accion=no]').onclick = () => { this.cerrar(); resolve(null); };
            cuerpo.querySelector('[data-accion=si]').onclick = () => {
                const nota = cuerpo.querySelector('#confirmar-nota')?.value.trim() ?? '';
                this.cerrar();
                resolve(nota);
            };
        });
    },
};

document.addEventListener('click', e => { if (e.target.closest('[data-cerrar]')) Modal.cerrar(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape' && Modal.abierto) Modal.cerrar(); });

function debounce(fn, ms = 250) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

function vacio(texto, nombreIcono = 'calendario') {
    return `<div class="vacio">${icono(nombreIcono)}<p>${esc(texto)}</p></div>`;
}
