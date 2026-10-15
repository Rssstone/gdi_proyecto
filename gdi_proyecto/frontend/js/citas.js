/* Citas: listado con filtros, formulario de alta/reprogramación y detalle con acciones de estado. */

const MOTIVOS_FRECUENTES = ['Revisión general', 'Limpieza dental', 'Resina', 'Extracción', 'Ortodoncia', 'Endodoncia', 'Urgencia'];

const CitasVista = {
    rango: 'proximas',

    iniciar() {
        document.getElementById('citas-buscar').oninput = debounce(() => this.cargar());
        document.getElementById('citas-estado').onchange = () => this.cargar();
        document.querySelectorAll('#citas-rango button').forEach(b => b.onclick = () => {
            this.rango = b.dataset.rango;
            this.marcarRango();
            this.cargar();
        });
        document.getElementById('btn-nueva-cita-2').onclick = () =>
            this.abrirFormulario({ fecha: App.hoy, odontologoId: Agenda.odontologoId });
    },

    marcarRango() {
        document.querySelectorAll('#citas-rango button').forEach(x => x.classList.toggle('activo', x.dataset.rango === this.rango));
    },

    /** Abre el listado con un filtro predefinido (desde los indicadores). */
    filtrar(rango, estado) {
        this.rango = rango;
        this.marcarRango();
        document.getElementById('citas-estado').value = estado;
        document.getElementById('citas-buscar').value = '';
        App.mostrarVista('citas');
    },

    async cargar() {
        const cont = document.getElementById('tabla-citas');
        const hoy = App.hoy;
        const filtros = {
            q: document.getElementById('citas-buscar').value.trim(),
            estado: document.getElementById('citas-estado').value,
            odontologoId: App.usuario.rol === 'Odontologo' ? Agenda.odontologoId : undefined,
        };
        if (this.rango === 'hoy') Object.assign(filtros, { desde: hoy, hasta: hoy });
        if (this.rango === 'proximas') Object.assign(filtros, { desde: hoy });
        if (this.rango === 'pasadas') Object.assign(filtros, { hasta: Fecha.sumarDias(hoy, -1) });

        cont.innerHTML = '<div class="cargando"></div>';
        try {
            let citas = await Api.citas(filtros);
            if (this.rango === 'pasadas') citas = citas.slice().reverse();
            this.pintarTabla(cont, citas);
        } catch (err) {
            cont.innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
        }
    },

    pintarTabla(cont, citas) {
        if (!citas.length) {
            cont.innerHTML = vacio('No hay citas con estos filtros.', 'lista');
            return;
        }
        let fechaActual = null;
        const filas = citas.map(c => {
            let grupo = '';
            if (c.fecha !== fechaActual) {
                fechaActual = c.fecha;
                const rel = Fecha.relativa(c.fecha, App.hoy);
                grupo = `<tr class="grupo-fecha"><td colspan="6">${rel ? `${rel} · ` : ''}${esc(Fecha.larguisima(c.fecha))}</td></tr>`;
            }
            return grupo + `
                <tr data-id="${c.id}" class="${c.pasada ? 'fila-pasada' : ''}">
                    <td><strong>${Fecha.hora12(c.hora)}</strong></td>
                    <td><strong>${esc(c.pacienteNombre)}</strong><small>${esc(c.pacienteTelefono || 'Sin teléfono')}</small></td>
                    <td>${esc(c.motivo || '—')}</td>
                    <td>${esc(c.odontologoNombre || '—')}</td>
                    <td><span class="badge badge--${c.estado}">${c.estado}</span></td>
                    <td class="acciones">${this.botonesRapidos(c)}</td>
                </tr>`;
        }).join('');
        cont.innerHTML = `<table class="tabla"><thead><tr>
            <th>Hora</th><th>Paciente</th><th>Motivo</th><th>Odontólogo</th><th>Estado</th><th></th></tr></thead>
            <tbody>${filas}</tbody></table>`;

        cont.querySelectorAll('tbody tr[data-id]').forEach(tr => tr.onclick = e => {
            const boton = e.target.closest('[data-accion]');
            const cita = citas.find(c => c.id === Number(tr.dataset.id));
            if (boton) { e.stopPropagation(); this.ejecutar(boton.dataset.accion, cita); }
            else this.verDetalle(cita.id);
        });
    },

    botonesRapidos(c) {
        const activa = ['Programada', 'Confirmada'].includes(c.estado);
        const b = [];
        if (activa && !c.pasada && App.puede('gestion')) {
            if (c.estado === 'Programada') b.push(['confirmar', 'check', 'Confirmar asistencia']);
            if (c.pacienteTelefono) b.push(['whatsapp', 'mensaje', 'Enviar recordatorio por WhatsApp']);
            b.push(['editar', 'editar', 'Reprogramar / editar']);
            b.push(['cancelar', 'x', 'Cancelar cita']);
        }
        if (activa && c.pasada) {
            b.push(['completar', 'check', 'Marcar como completada']);
            b.push(['inasistencia', 'alerta', 'Marcar inasistencia']);
        }
        return b.map(([accion, ic, titulo]) =>
            `<button class="btn-icono" data-accion="${accion}" title="${titulo}" aria-label="${titulo}">${icono(ic)}</button>`).join('');
    },

    /* ---------- Detalle ---------- */
    async verDetalle(id) {
        let c;
        try { c = await Api.cita(id); } catch (err) { return toast(err.message, 'error'); }
        const d = Fecha.aDate(c.fecha);
        const activa = ['Programada', 'Confirmada'].includes(c.estado);
        const gestion = App.puede('gestion');

        const botones = [];
        if (activa && !c.pasada && gestion) {
            if (c.estado === 'Programada') botones.push(`<button class="btn btn--verde" data-accion="confirmar">${icono('check')}Confirmar</button>`);
            if (c.pacienteTelefono) botones.push(`<button class="btn btn--whats" data-accion="whatsapp">${icono('mensaje')}Recordatorio</button>`);
            botones.push(`<button class="btn btn--suave" data-accion="editar">${icono('editar')}Reprogramar</button>`);
        }
        if (activa && c.pasada) {
            botones.push(`<button class="btn btn--primario" data-accion="completar">${icono('check')}Completada</button>`);
            botones.push(`<button class="btn btn--borde" data-accion="inasistencia">${icono('alerta')}No asistió</button>`);
        }
        botones.push('<span class="separador"></span>');
        if (activa && !c.pasada && gestion) botones.push(`<button class="btn btn--borde" data-accion="cancelar">${icono('x')}Cancelar cita</button>`);
        if (App.puede('eliminar') && c.estado !== 'Completada') {
            botones.push(`<button class="btn-icono" data-accion="eliminar" title="Eliminar definitivamente">${icono('basura')}</button>`);
        }

        let aviso = '';
        if (activa && c.pasada) aviso = '<p class="aviso aviso--info">La hora de esta cita ya pasó. Registra si el paciente asistió.</p>';
        if (!activa) aviso = `<p class="aviso aviso--info">Esta cita está <b>${c.estado.toLowerCase()}</b>; ya no puede modificarse.</p>`;

        const cuerpo = Modal.abrir('Detalle de la cita', `
            <div class="detalle__cabecera">
                <div class="detalle__fecha"><span>${MESES[d.getMonth()].slice(0, 3)}</span><b>${d.getDate()}</b><span>${DIAS[d.getDay()].slice(0, 3)}</span></div>
                <div>
                    <h4>${esc(c.pacienteNombre)}</h4>
                    <div>${Fecha.hora12(c.hora)} · ${esc(Fecha.larga(c.fecha))}</div>
                    <span class="badge badge--${c.estado}">${c.estado}</span>
                </div>
            </div>
            ${aviso}
            <div class="detalle">
                <div class="detalle__dato"><small>Motivo</small><strong>${esc(c.motivo || '—')}</strong></div>
                <div class="detalle__dato"><small>Odontólogo</small><strong>${esc(c.odontologoNombre || '—')}</strong></div>
                <div class="detalle__dato"><small>Teléfono</small><strong>${c.pacienteTelefono
                    ? `<a href="tel:${esc(c.pacienteTelefono)}">${esc(c.pacienteTelefono)}</a>` : '—'}</strong></div>
                <div class="detalle__dato"><small>Folio</small><strong>#${c.id}</strong></div>
                ${c.notasRecepcion ? `<div class="detalle__dato detalle__ancho"><small>Notas</small>
                    <p class="detalle__notas">${esc(c.notasRecepcion)}</p></div>` : ''}
            </div>
            <div class="botonera">${botones.join('')}</div>`);
        cuerpo.querySelectorAll('[data-accion]').forEach(b => b.onclick = () => this.ejecutar(b.dataset.accion, c));
    },

    /* ---------- Acciones ---------- */
    async ejecutar(accion, c) {
        const quien = `<b>${esc(c.pacienteNombre)}</b> — ${esc(Fecha.larga(c.fecha))}, ${Fecha.hora12(c.hora)}`;
        try {
            switch (accion) {
                case 'editar':
                    return this.abrirFormulario({ cita: c });
                case 'whatsapp':
                    return this.enviarRecordatorio(c);
                case 'confirmar':
                    await Api.cambiarEstado(c.id, 'Confirmada');
                    toast('Cita confirmada.');
                    break;
                case 'cancelar': {
                    const nota = await Modal.confirmar({
                        titulo: 'Cancelar cita', mensaje: `¿Cancelar la cita de ${quien}? El horario quedará libre.`,
                        textoBoton: 'Sí, cancelar', peligro: true, pedirNota: 'Motivo de la cancelación',
                    });
                    if (nota === null) return;
                    await Api.cambiarEstado(c.id, 'Cancelada', nota);
                    toast('Cita cancelada. El horario quedó disponible.');
                    break;
                }
                case 'completar':
                    await Api.cambiarEstado(c.id, 'Completada');
                    toast('Cita marcada como completada.');
                    break;
                case 'inasistencia': {
                    const nota = await Modal.confirmar({
                        titulo: 'Registrar inasistencia', mensaje: `¿El paciente no asistió a su cita? ${quien}`,
                        textoBoton: 'Registrar inasistencia', pedirNota: 'Observaciones',
                    });
                    if (nota === null) return;
                    await Api.cambiarEstado(c.id, 'Inasistencia', nota);
                    toast('Inasistencia registrada.');
                    break;
                }
                case 'eliminar': {
                    const ok = await Modal.confirmar({
                        titulo: 'Eliminar cita', mensaje: `Se eliminará definitivamente la cita de ${quien}. Esta acción no se puede deshacer.`,
                        textoBoton: 'Eliminar', peligro: true,
                    });
                    if (ok === null) return;
                    await Api.eliminarCita(c.id);
                    toast('Cita eliminada.');
                    break;
                }
            }
            Modal.cerrar();
            App.refrescar();
        } catch (err) {
            toast(err.message, 'error');
        }
    },

    /** RF-08: recordatorio por WhatsApp con el mensaje ya redactado. */
    enviarRecordatorio(c) {
        const digitos = (c.pacienteTelefono || '').replace(/\D/g, '');
        const numero = digitos.length === 10 ? `52${digitos}` : digitos;
        const texto = `Hola ${c.pacienteNombre}, le recordamos su cita en Clínica Imagen el ${Fecha.larga(c.fecha)} a las ${Fecha.hora12(c.hora)}`
            + `${c.motivo ? ` (${c.motivo})` : ''}. Por favor confírmenos su asistencia respondiendo este mensaje. ¡Gracias!`;
        window.open(`https://wa.me/${numero}?text=${encodeURIComponent(texto)}`, '_blank', 'noopener');
    },

    /* ---------- Formulario de alta / reprogramación ---------- */
    /**
     * datos: { cita } para editar, o { fecha, hora, odontologoId, paciente, motivo, notas } para una nueva.
     */
    abrirFormulario(datos = {}) {
        if (!App.puede('gestion')) return toast('Tu rol no permite agendar citas.', 'info');
        const c = datos.cita;
        const estado = {
            id: c?.id ?? null,
            paciente: c ? { id: c.pacienteId, nombreCompleto: c.pacienteNombre, telefono: c.pacienteTelefono } : datos.paciente ?? null,
            odontologoId: c?.odontologoId ?? datos.odontologoId ?? App.odontologos[0]?.id,
            fecha: c?.fecha ?? (datos.fecha && datos.fecha >= App.hoy ? datos.fecha : App.hoy),
            hora: c?.hora ?? datos.hora ?? null,
            motivo: c?.motivo ?? datos.motivo ?? '',
            notas: c?.notasRecepcion ?? datos.notas ?? '',
        };

        const cuerpo = Modal.abrir(c ? 'Reprogramar / editar cita' : 'Nueva cita', `
            <form id="form-cita" novalidate>
                <p class="error-form" id="cita-error" hidden></p>
                <div class="campo">
                    <span class="campo__etiqueta">Paciente <b class="req">*</b></span>
                    <div id="cita-paciente"></div>
                    <span class="campo__error" data-error="pacienteId"></span>
                </div>
                <div class="fila-campos">
                    <label class="campo">
                        <span class="campo__etiqueta">Odontólogo <b class="req">*</b></span>
                        <select name="odontologoId">${App.odontologos.map(o =>
                            `<option value="${o.id}">${esc(o.nombreCompleto)}</option>`).join('')}</select>
                    </label>
                    <label class="campo">
                        <span class="campo__etiqueta">Fecha <b class="req">*</b></span>
                        <input type="date" name="fecha" min="${App.hoy}" required>
                        <span class="campo__error" data-error="fecha"></span>
                    </label>
                </div>
                <div class="campo">
                    <span class="campo__etiqueta">Hora <b class="req">*</b> <span class="campo__ayuda" id="cita-hora-ayuda"></span></span>
                    <div id="cita-horas" class="selector-horas"></div>
                    <span class="campo__error" data-error="hora"></span>
                </div>
                <label class="campo">
                    <span class="campo__etiqueta">Motivo de la consulta <b class="req">*</b></span>
                    <input type="text" name="motivo" maxlength="255" placeholder="Ej. Limpieza dental" required>
                    <div class="sugerencias">${MOTIVOS_FRECUENTES.map(m => `<button type="button" class="chip" data-motivo="${esc(m)}">${esc(m)}</button>`).join('')}</div>
                    <span class="campo__error" data-error="motivo"></span>
                </label>
                <label class="campo">
                    <span class="campo__etiqueta">Notas de recepción</span>
                    <textarea name="notasRecepcion" maxlength="1000" placeholder="Indicaciones, referencias, etc. (opcional)"></textarea>
                </label>
                ${c && c.estado === 'Confirmada' ? '<p class="aviso aviso--info">Si cambias la fecha, hora u odontólogo, la cita regresará a <b>Programada</b> y deberá confirmarse de nuevo.</p>' : ''}
                <div class="acciones-form">
                    <button type="button" class="btn btn--borde" data-cerrar>Cancelar</button>
                    <button type="submit" class="btn btn--primario">${icono('check')}${c ? 'Guardar cambios' : 'Agendar cita'}</button>
                </div>
            </form>`);

        const form = cuerpo.querySelector('#form-cita');
        form.odontologoId.value = estado.odontologoId;
        form.fecha.value = estado.fecha;
        form.motivo.value = estado.motivo;
        form.notasRecepcion.value = estado.notas;

        const borrador = () => ({
            fecha: form.fecha.value, hora: estado.hora, odontologoId: Number(form.odontologoId.value),
            paciente: estado.paciente, motivo: form.motivo.value, notas: form.notasRecepcion.value,
        });

        SelectorPaciente.montar(cuerpo.querySelector('#cita-paciente'), estado.paciente, {
            alElegir: p => { estado.paciente = p; limpiarError('pacienteId'); },
            alCrearNuevo: (nombre) => PacientesVista.abrirFormulario({ nombreCompleto: nombre }, nuevo =>
                this.abrirFormulario(c ? { cita: { ...c, ...aCita(borrador(), nuevo) } } : { ...borrador(), paciente: nuevo })),
        });

        cuerpo.querySelectorAll('[data-motivo]').forEach(b => b.onclick = () => { form.motivo.value = b.dataset.motivo; limpiarError('motivo'); });

        const limpiarError = campo => {
            const el = cuerpo.querySelector(`[data-error=${campo}]`);
            if (el) el.textContent = '';
            form.elements[campo]?.classList.remove('invalido');
        };
        const mostrarErrores = (campos) => Object.entries(campos).forEach(([campo, msg]) => {
            const el = cuerpo.querySelector(`[data-error=${campo}]`);
            if (el) el.textContent = msg;
            form.elements[campo]?.classList.add('invalido');
        });

        const cargarHoras = async () => {
            const cont = cuerpo.querySelector('#cita-horas');
            const ayuda = cuerpo.querySelector('#cita-hora-ayuda');
            limpiarError('fecha');
            limpiarError('hora');
            const fecha = form.fecha.value;
            if (!fecha) { cont.innerHTML = '<p class="aviso aviso--info" style="grid-column:1/-1">Elige una fecha.</p>'; return; }
            if (fecha < App.hoy) {
                form.fecha.classList.add('invalido');
                cont.innerHTML = '<p class="aviso aviso--alerta" style="grid-column:1/-1">No se pueden agendar citas en fechas pasadas.</p>';
                return;
            }
            cont.innerHTML = '<div class="cargando" style="grid-column:1/-1"></div>';
            try {
                const disp = await Api.disponibilidad(fecha, form.odontologoId.value, estado.id);
                if (!disp.abierto) {
                    form.fecha.classList.add('invalido');
                    cont.innerHTML = `<p class="aviso aviso--alerta" style="grid-column:1/-1">${esc(disp.mensaje)}</p>`;
                    ayuda.textContent = '';
                    return;
                }
                ayuda.textContent = `· ${disp.disponibles} disponible(s)`;
                if (!disp.espacios.some(e => e.estado === 'DISPONIBLE' && e.hora === estado.hora)) estado.hora = null;
                cont.innerHTML = disp.disponibles === 0
                    ? '<p class="aviso aviso--alerta" style="grid-column:1/-1">No quedan horarios libres este día. Elige otra fecha.</p>'
                    : disp.espacios.map(e => {
                        const clase = e.estado === 'OCUPADO' ? 'ocupada' : e.estado === 'PASADO' ? 'pasada' : (e.hora === estado.hora ? 'elegida' : '');
                        const titulo = e.estado === 'OCUPADO' ? `Ocupado: ${e.pacienteNombre}` : e.estado === 'PASADO' ? 'Hora pasada' : 'Disponible';
                        return `<button type="button" class="${clase}" data-hora="${e.hora}" ${e.estado !== 'DISPONIBLE' ? 'disabled' : ''}
                                    title="${esc(titulo)}">${e.hora}</button>`;
                    }).join('');
                cont.querySelectorAll('button:not(:disabled)').forEach(b => b.onclick = () => {
                    estado.hora = b.dataset.hora;
                    cont.querySelectorAll('.elegida').forEach(x => x.classList.remove('elegida'));
                    b.classList.add('elegida');
                    limpiarError('hora');
                });
            } catch (err) {
                cont.innerHTML = `<p class="aviso aviso--alerta" style="grid-column:1/-1">${esc(err.message)}</p>`;
            }
        };
        form.fecha.onchange = cargarHoras;
        form.odontologoId.onchange = cargarHoras;
        form.motivo.oninput = () => limpiarError('motivo');
        cargarHoras();

        form.onsubmit = async e => {
            e.preventDefault();
            const error = cuerpo.querySelector('#cita-error');
            error.hidden = true;
            const errores = {};
            if (!estado.paciente) errores.pacienteId = 'Selecciona o registra un paciente.';
            if (!form.fecha.value) errores.fecha = 'Elige una fecha.';
            else if (form.fecha.value < App.hoy) errores.fecha = 'No se pueden agendar citas en fechas pasadas.';
            if (!estado.hora) errores.hora = 'Elige un horario disponible (en verde).';
            if (form.motivo.value.trim().length < 3) errores.motivo = 'Describe el motivo (mínimo 3 caracteres).';
            if (Object.keys(errores).length) return mostrarErrores(errores);

            const datosCita = {
                pacienteId: estado.paciente.id,
                odontologoId: Number(form.odontologoId.value),
                fecha: form.fecha.value,
                hora: estado.hora,
                motivo: form.motivo.value.trim(),
                notasRecepcion: form.notasRecepcion.value.trim(),
            };
            const boton = form.querySelector('button[type=submit]');
            boton.disabled = true;
            try {
                const guardada = estado.id ? await Api.actualizarCita(estado.id, datosCita) : await Api.crearCita(datosCita);
                Modal.cerrar();
                toast(`${estado.id ? 'Cita actualizada' : 'Cita agendada'}: ${guardada.pacienteNombre}, ${Fecha.larga(guardada.fecha)} a las ${Fecha.hora12(guardada.hora)}`);
                Agenda.dia = guardada.fecha;
                Agenda.mes = guardada.fecha.slice(0, 8) + '01';
                App.refrescar();
            } catch (err) {
                error.textContent = err.message;
                error.hidden = false;
                mostrarErrores(err.campos);
                if (err.status === 409 || err.status === 422) cargarHoras();
            } finally {
                boton.disabled = false;
            }
        };
    },
};

/** Combina un borrador del formulario con un paciente recién creado, en forma de cita para reabrir la edición. */
function aCita(b, paciente) {
    return {
        fecha: b.fecha, hora: b.hora, odontologoId: b.odontologoId, motivo: b.motivo, notasRecepcion: b.notas,
        pacienteId: paciente.id, pacienteNombre: paciente.nombreCompleto, pacienteTelefono: paciente.telefono,
    };
}

/* Buscador de pacientes con autocompletado. */
const SelectorPaciente = {
    montar(cont, inicial, { alElegir, alCrearNuevo }) {
        const pintarElegido = p => {
            cont.innerHTML = `<div class="paciente-elegido">
                <div><strong>${esc(p.nombreCompleto)}</strong><small>${esc(p.telefono || 'Sin teléfono')}</small></div>
                <button type="button" class="enlace">Cambiar</button></div>`;
            cont.querySelector('.enlace').onclick = () => { alElegir(null); pintarBuscador(); };
        };
        const pintarBuscador = () => {
            cont.innerHTML = `<div class="autocompletar">
                <div class="buscador"><span>${icono('buscar')}</span>
                    <input type="text" name="pacienteId" placeholder="Escribe el nombre o teléfono del paciente…" autocomplete="off"></div>
                <ul class="autocompletar__lista" hidden></ul></div>`;
            const input = cont.querySelector('input');
            const lista = cont.querySelector('ul');
            const buscar = debounce(async () => {
                const q = input.value.trim();
                try {
                    const pacientes = await Api.pacientes(q);
                    lista.innerHTML = pacientes.slice(0, 8).map(p =>
                        `<li data-id="${p.id}"><span>${esc(p.nombreCompleto)}</span><small>${esc(p.telefono || '')}</small></li>`).join('')
                        + `<li data-nuevo><span class="enlace">${icono('mas')} Registrar nuevo paciente${q ? `: “${esc(q)}”` : ''}</span></li>`;
                    lista.hidden = false;
                    lista.querySelectorAll('li[data-id]').forEach(li => li.onmousedown = ev => {
                        ev.preventDefault();
                        const p = pacientes.find(x => x.id === Number(li.dataset.id));
                        alElegir(p);
                        pintarElegido(p);
                    });
                    lista.querySelector('li[data-nuevo]').onmousedown = ev => { ev.preventDefault(); alCrearNuevo(q); };
                } catch (err) {
                    lista.innerHTML = `<li>${esc(err.message)}</li>`;
                    lista.hidden = false;
                }
            }, 200);
            input.oninput = buscar;
            input.onfocus = buscar;
            input.onblur = () => setTimeout(() => { lista.hidden = true; }, 150);
        };
        if (inicial) pintarElegido(inicial); else pintarBuscador();
    },
};
