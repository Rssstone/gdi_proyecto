/* Vista de agenda: calendario mensual + horarios del día seleccionado (RF-06, RF-07). */

const Agenda = {
    mes: null,             // AAAA-MM-01
    dia: null,             // AAAA-MM-DD seleccionado
    odontologoId: null,

    iniciar(odontologoId) {
        this.odontologoId = odontologoId;
        const select = document.getElementById('filtro-odontologo');
        select.innerHTML = App.odontologos.map(o => `<option value="${o.id}">${esc(o.nombreCompleto)}</option>`).join('');
        if (odontologoId) select.value = odontologoId;
        select.onchange = () => { this.odontologoId = Number(select.value); App.refrescar(); };

        document.getElementById('mes-anterior').onclick = () => this.moverMes(-1);
        document.getElementById('mes-siguiente').onclick = () => this.moverMes(1);
        document.getElementById('btn-hoy').onclick = () => { this.irAHoy(); this.cargar(); };
        document.getElementById('btn-nueva-cita').onclick = () => CitasVista.abrirFormulario({
            fecha: this.dia >= App.hoy ? this.dia : App.hoy,
            odontologoId: this.odontologoId,
        });
        this.irAHoy(false);
    },

    irAHoy(recargar = true) {
        this.dia = App.hoy;
        this.mes = App.hoy.slice(0, 8) + '01';
        if (recargar) this.cargar();
    },

    moverMes(delta) {
        const d = Fecha.aDate(this.mes);
        d.setMonth(d.getMonth() + delta);
        this.mes = Fecha.aIso(d);
        this.cargar(false);
    },

    async cargar(conDia = true) {
        await Promise.all([this.cargarMes(), conDia ? this.cargarDia() : null]);
    },

    async cargarMes() {
        const inicio = Fecha.aDate(this.mes);
        const fin = Fecha.aIso(new Date(inicio.getFullYear(), inicio.getMonth() + 1, 0));
        document.getElementById('mes-titulo').textContent = `${MESES[inicio.getMonth()]} ${inicio.getFullYear()}`;
        const cont = document.getElementById('calendario-dias');
        try {
            const resumen = await Api.resumen(this.mes, fin, this.odontologoId);
            const desfase = (inicio.getDay() + 6) % 7; // semana inicia en lunes
            let html = '<span class="celda celda--fuera"></span>'.repeat(desfase);
            html += resumen.map(d => {
                const num = Number(d.fecha.slice(8));
                const info = {
                    DISPONIBLE: `${d.disponibles} libres`,
                    LLENO: 'Lleno',
                    PASADO: d.citas ? `${d.citas} cita${d.citas > 1 ? 's' : ''}` : '',
                    CERRADO: 'Cerrado',
                }[d.estado];
                const clases = ['celda', `celda--${d.estado}`,
                    d.fecha === App.hoy ? 'celda--hoy' : '', d.fecha === this.dia ? 'celda--seleccionado' : ''].join(' ');
                const titulo = {
                    DISPONIBLE: `${d.disponibles} horarios disponibles · ${d.citas} citas`,
                    LLENO: 'Sin horarios disponibles',
                    PASADO: 'Día pasado (solo consulta)',
                    CERRADO: 'La clínica no atiende este día',
                }[d.estado];
                return `<button class="${clases}" data-fecha="${d.fecha}" data-estado="${d.estado}" title="${titulo}">
                    <span class="celda__num">${num}</span><span class="celda__info">${info}</span></button>`;
            }).join('');
            cont.innerHTML = html;
            cont.querySelectorAll('button.celda').forEach(b => b.onclick = () => {
                if (b.dataset.estado === 'CERRADO') { toast('La clínica no atiende ese día.', 'info'); return; }
                this.dia = b.dataset.fecha;
                cont.querySelectorAll('.celda--seleccionado').forEach(c => c.classList.remove('celda--seleccionado'));
                b.classList.add('celda--seleccionado');
                this.cargarDia();
            });
        } catch (err) {
            cont.innerHTML = `<p class="error-form" style="grid-column:1/-1">${esc(err.message)}</p>`;
        }
    },

    async cargarDia() {
        const titulo = document.getElementById('dia-titulo');
        const mensaje = document.getElementById('dia-mensaje');
        const cont = document.getElementById('horarios');
        const relativa = Fecha.relativa(this.dia, App.hoy);
        titulo.textContent = (relativa ? `${relativa} · ` : '') + Fecha.larga(this.dia);
        cont.innerHTML = '<div class="cargando" style="grid-column:1/-1"></div>';

        const pasado = this.dia < App.hoy;
        const botonNueva = document.getElementById('btn-nueva-cita');
        botonNueva.disabled = pasado;
        botonNueva.title = pasado ? 'No se pueden agendar citas en fechas pasadas' : '';

        try {
            const disp = await Api.disponibilidad(this.dia, this.odontologoId);
            mensaje.textContent = disp.mensaje;
            if (!disp.abierto) {
                cont.innerHTML = vacio(disp.mensaje, 'candado');
                botonNueva.disabled = true;
                return;
            }
            const gestion = App.puede('gestion');
            cont.innerHTML = disp.espacios.map(e => this.pintarHueco(e, gestion, pasado)).join('');
            cont.querySelectorAll('.hueco').forEach(b => b.onclick = () => this.clicHueco(b));
        } catch (err) {
            mensaje.textContent = '';
            cont.innerHTML = `<p class="error-form" style="grid-column:1/-1">${esc(err.message)}</p>`;
        }
    },

    /**
     * Un horario de la cuadrícula. Las citas se pintan según su estado:
     * pendientes en rojo, completadas en morado y las de inasistencia en ámbar (ya no bloquean el horario).
     */
    pintarHueco(e, gestion, diaPasado) {
        const noAsistio = e.estadoCita === 'Inasistencia';
        let texto = e.estado === 'PASADO' ? 'Hora pasada' : gestion ? 'Disponible · agendar' : 'Disponible';
        let sub = '';
        let ic = e.estado === 'PASADO' ? 'candado' : 'reloj';
        if (e.citaId) {
            ic = noAsistio ? 'alerta' : 'usuario';
            if (e.continuacion) {
                texto = `↳ continúa · ${esc(e.pacienteNombre)}`;
            } else {
                texto = esc(e.pacienteNombre);
                sub = `${noAsistio ? 'No asistió' : e.estadoCita} · hasta ${Fecha.hora12(e.citaFin)}`;
                // Ya empezó y sigue pendiente: hay que registrar si llegó o no.
                if (['Programada', 'Confirmada'].includes(e.estadoCita) && `${this.dia}T${e.citaInicio}` <= App.ahora) {
                    sub = 'Registrar asistencia';
                }
            }
        }
        const clases = ['hueco', `hueco--${e.estado}`,
            e.estado === 'OCUPADO' && diaPasado ? 'hueco--historico' : '',
            e.continuacion ? 'hueco--continua' : '',
            e.estadoCita === 'Completada' ? 'hueco--completada' : '',
            sub === 'Registrar asistencia' ? 'hueco--pendiente' : '',
            noAsistio ? 'hueco--inasistencia' : '',
            !gestion ? 'hueco--solo-lectura' : ''].filter(Boolean).join(' ');
        const titulo = e.citaId
            ? `${e.pacienteNombre}: ${Fecha.hora12(e.citaInicio)} a ${Fecha.hora12(e.citaFin)} (${noAsistio ? 'no asistió' : e.estadoCita})`
            : e.estado === 'PASADO' ? 'Esta hora ya pasó, no se puede agendar' : '';
        return `<button class="${clases}" data-hora="${e.hora}" data-estado="${e.estado}"
                    ${e.citaId ? `data-cita="${e.citaId}"` : ''} title="${esc(titulo)}">
            <span class="hueco__hora">${icono(sub === 'Registrar asistencia' ? 'alerta' : ic)}${Fecha.hora12(e.hora)}</span>
            <span class="hueco__texto">${texto}</span>
            ${sub ? `<span class="hueco__sub">${esc(sub)}</span>` : ''}
        </button>`;
    },

    clicHueco(boton) {
        const { estado, hora, cita } = boton.dataset;
        if (cita) return CitasVista.verDetalle(Number(cita));
        if (estado === 'PASADO') return toast('Esa hora ya pasó; no se pueden agendar citas en el pasado.', 'error');
        if (!App.puede('gestion')) return toast('Solo recepción o administración pueden agendar citas.', 'info');
        CitasVista.abrirFormulario({ fecha: this.dia, hora, odontologoId: this.odontologoId });
    },
};
