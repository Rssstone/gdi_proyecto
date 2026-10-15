/* Arranque de la aplicación: sesión, permisos por rol, navegación, indicadores y reloj. */

const App = {
    usuario: null,
    config: null,          // horario de la clínica y "ahora" según el servidor
    odontologos: [],
    vista: 'agenda',

    get hoy() { return this.config?.hoy; },
    get ahora() { return this.config?.ahora; },

    /* Permisos de la interfaz según el rol (RF-12) */
    puede(accion) {
        const rol = this.usuario?.rol;
        switch (accion) {
            case 'gestion': return rol === 'Recepcionista' || rol === 'Administrador';
            case 'eliminar': return rol === 'Administrador';
            default: return true;
        }
    },

    async iniciar() {
        pintarIconos();
        this.prepararLogin();
        document.getElementById('btn-salir').onclick = () => this.cerrarSesion();
        document.querySelectorAll('#tabs .tab').forEach(t => t.onclick = () => this.mostrarVista(t.dataset.vista));

        const guardado = leerSesion();
        if (guardado) await this.entrar(guardado);
        else document.getElementById('vista-login').hidden = false;
    },

    prepararLogin() {
        const form = document.getElementById('form-login');
        const error = document.getElementById('login-error');
        document.querySelectorAll('[data-demo]').forEach(b => b.onclick = () => {
            form.email.value = b.dataset.demo;
            form.password.value = '12345';
            form.requestSubmit();
        });
        form.onsubmit = async e => {
            e.preventDefault();
            error.hidden = true;
            if (!form.email.value.trim() || !form.password.value) {
                error.textContent = 'Escribe tu correo y contraseña.';
                error.hidden = false;
                return;
            }
            const boton = form.querySelector('button[type=submit]');
            boton.disabled = true;
            try {
                const usuario = await Api.login(form.email.value.trim(), form.password.value);
                guardarSesion(usuario);
                form.reset();
                await this.entrar(usuario);
            } catch (err) {
                error.textContent = err.message;
                error.hidden = false;
            } finally {
                boton.disabled = false;
            }
        };
    },

    async entrar(usuario) {
        this.usuario = usuario;
        try {
            [this.config, this.odontologos] = await Promise.all([Api.configuracion(), Api.odontologos()]);
        } catch (err) {
            document.getElementById('vista-login').hidden = false;
            const error = document.getElementById('login-error');
            error.textContent = err.message;
            error.hidden = false;
            return;
        }
        document.getElementById('vista-login').hidden = true;
        document.getElementById('vista-app').hidden = false;

        document.getElementById('usuario-nombre').textContent = usuario.nombreCompleto;
        document.getElementById('usuario-rol').textContent = usuario.rol === 'Odontologo' ? 'Odontólogo(a)' : usuario.rol;
        document.getElementById('usuario-avatar').textContent = usuario.nombreCompleto
            .replace(/^(Dra?\.)\s*/i, '').split(/\s+/).slice(0, 2).map(p => p[0]).join('').toUpperCase();
        document.querySelectorAll('[data-rol=gestion]').forEach(el => el.hidden = !this.puede('gestion'));

        // El odontólogo ve por defecto su propia agenda.
        const propio = this.odontologos.find(o => o.id === usuario.id);
        Agenda.iniciar(propio ? propio.id : this.odontologos[0]?.id);
        CitasVista.iniciar();
        PacientesVista.iniciar();
        this.pintarReloj();
        this.mostrarVista('agenda');

        clearInterval(this._timer);
        this._timer = setInterval(() => this.tic(), 60_000);
    },

    cerrarSesion() {
        borrarSesion();
        clearInterval(this._timer);
        location.reload();
    },

    mostrarVista(vista) {
        this.vista = vista;
        document.querySelectorAll('#tabs .tab').forEach(t => t.classList.toggle('activo', t.dataset.vista === vista));
        ['agenda', 'citas', 'pacientes'].forEach(v => document.getElementById(`vista-${v}`).hidden = v !== vista);
        this.refrescar();
    },

    /** Recarga indicadores y la vista activa (después de cualquier cambio). */
    async refrescar() {
        this.cargarIndicadores();
        if (this.vista === 'agenda') await Agenda.cargar();
        if (this.vista === 'citas') await CitasVista.cargar();
        if (this.vista === 'pacientes') await PacientesVista.cargar();
    },

    /** Cada minuto: actualiza la hora del servidor para que las horas que van pasando se bloqueen solas. */
    async tic() {
        try {
            const diaAnterior = this.hoy;
            this.config = await Api.configuracion();
            this.pintarReloj();
            if (!Modal.abierto) {
                if (diaAnterior !== this.hoy) Agenda.irAHoy(false);
                this.refrescar();
            }
        } catch { /* sin conexión: se reintenta en el siguiente minuto */ }
    },

    pintarReloj() {
        const [fecha, hora] = this.ahora.split('T');
        document.getElementById('reloj').innerHTML = `${icono('reloj')}<span>${esc(Fecha.larga(fecha))} · ${esc(Fecha.hora12(hora))}</span>`;
    },

    async cargarIndicadores() {
        const cont = document.getElementById('kpis');
        const manana = Fecha.sumarDias(this.hoy, 1);
        try {
            const [citas, dispHoy] = await Promise.all([
                Api.citas({ desde: this.hoy, hasta: manana }),
                Api.disponibilidad(this.hoy, Agenda.odontologoId),
            ]);
            const deHoy = citas.filter(c => c.fecha === this.hoy && c.estado !== 'Cancelada');
            const porConfirmar = citas.filter(c => c.estado === 'Programada' && !c.pasada);
            const proxima = citas.find(c => ['Programada', 'Confirmada'].includes(c.estado) && !c.pasada);

            cont.innerHTML = `
                <div class="kpi kpi--azul kpi--clic" data-ir="hoy">
                    <div class="kpi__icono">${icono('calendario')}</div>
                    <div><div class="kpi__valor">${deHoy.length}</div><div class="kpi__etiqueta">Citas de hoy</div></div>
                </div>
                <div class="kpi kpi--ambar kpi--clic" data-ir="confirmar">
                    <div class="kpi__icono">${icono('mensaje')}</div>
                    <div><div class="kpi__valor">${porConfirmar.length}</div><div class="kpi__etiqueta">Por confirmar (hoy y mañana)</div></div>
                </div>
                <div class="kpi kpi--verde kpi--clic" data-ir="libres">
                    <div class="kpi__icono">${icono('check')}</div>
                    <div><div class="kpi__valor">${dispHoy.abierto ? dispHoy.disponibles : '—'}</div>
                    <div class="kpi__etiqueta">${dispHoy.abierto ? 'Horarios libres hoy' : 'Hoy no hay consulta'}</div></div>
                </div>
                <div class="kpi kpi--morado ${proxima ? 'kpi--clic' : ''}" data-ir="proxima">
                    <div class="kpi__icono">${icono('reloj')}</div>
                    <div style="min-width:0"><div class="kpi__valor kpi__valor--texto">${proxima
                        ? `${esc(Fecha.relativa(proxima.fecha, this.hoy))} · ${esc(Fecha.hora12(proxima.hora))}` : 'Sin pendientes'}</div>
                    <div class="kpi__etiqueta" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${proxima
                        ? esc(proxima.pacienteNombre) : 'Próxima cita'}</div></div>
                </div>`;
            cont.querySelector('[data-ir=hoy]').onclick = () => CitasVista.filtrar('hoy', '');
            cont.querySelector('[data-ir=confirmar]').onclick = () => CitasVista.filtrar('proximas', 'Programada');
            cont.querySelector('[data-ir=libres]').onclick = () => { Agenda.irAHoy(); this.mostrarVista('agenda'); };
            if (proxima) cont.querySelector('[data-ir=proxima]').onclick = () => CitasVista.verDetalle(proxima.id);
        } catch {
            cont.innerHTML = '';
        }
    },
};

/* Sesión del navegador (solo recuerda quién inició sesión en esta pestaña). */
function leerSesion() {
    try { return JSON.parse(sessionStorage.getItem('gdi-usuario')); } catch { return null; }
}
function guardarSesion(usuario) {
    try { sessionStorage.setItem('gdi-usuario', JSON.stringify(usuario)); } catch { /* sin almacenamiento */ }
}
function borrarSesion() {
    try { sessionStorage.removeItem('gdi-usuario'); } catch { /* sin almacenamiento */ }
}

App.iniciar();
