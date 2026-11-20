/* Citas: listado con filtros, formulario de alta/reprogramación y detalle con acciones de estado. */

/* Procedimientos que se pueden agendar, con su duración sugerida (la recepcionista puede ajustarla). */
const PROCEDIMIENTOS = [
    ['Diagnóstico y prevención', [['Revisión general', 30], ['Valoración inicial', 30], ['Limpieza dental', 60]]],
    ['Restauración', [['Resina', 60], ['Incrustación', 60]]],
    ['Cirugía', [['Extracción simple', 60], ['Extracción de muela del juicio', 90]]],
    ['Endodoncia', [['Endodoncia (tratamiento de conductos)', 90]]],
    ['Ortodoncia', [['Colocación de brackets', 120], ['Ajuste de ortodoncia', 30]]],
    ['Prótesis', [['Toma de medidas para corona o prótesis', 60], ['Colocación de corona', 60], ['Ajuste de prótesis', 30]]],
    ['Estética', [['Blanqueamiento dental', 90]]],
];
/* Urgencias: se atienden como sobrecupo y son rápidas. */
const URGENCIAS = [
    ['Bracket despegado', 15], ['Dolor dental agudo', 30], ['Diente fracturado', 30],
    ['Corona o provisional despegado', 20], ['Inflamación o infección', 30], ['Sangrado después de una extracción', 20],
];
const DURACIONES = [15, 20, 30, 40, 45, 60, 75, 90, 120, 150, 180, 210, 240];
const DURACIONES_URGENCIA = [10, 15, 20, 30, 45, 60];
const OTRO = '__otro';

const Procedimientos = {
    opciones(urgencia) {
        return urgencia
            ? `<optgroup label="Urgencias">${URGENCIAS.map(([n, m]) => `<option value="${esc(n)}">${esc(n)}</option>`).join('')}</optgroup>`
            : PROCEDIMIENTOS.map(([grupo, lista]) => `<optgroup label="${esc(grupo)}">${lista.map(([n, m]) =>
                `<option value="${esc(n)}">${esc(n)}</option>`).join('')}</optgroup>`).join('');
    },
    lista(urgencia) { return urgencia ? URGENCIAS : PROCEDIMIENTOS.flatMap(([, l]) => l); },
    minutos(nombre, urgencia) { return this.lista(urgencia).find(([n]) => n === nombre)?.[1]; },
    /** Ubica un motivo guardado en la lista; si no está, queda como "Otro" con su texto. */
    identificar(motivo, urgencia) {
        const texto = (motivo || '').replace(/^Urgencia:\s*/, '');
        if (!texto) return { valor: '', otro: '' };
        return this.lista(urgencia).some(([n]) => n === texto) ? { valor: texto, otro: '' } : { valor: OTRO, otro: motivo };
    },
};

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
                    <td><strong>${Fecha.hora12(c.hora)}</strong><small>hasta ${Fecha.hora12(c.horaFin)}</small></td>
                    <td><strong>${esc(c.pacienteNombre)}</strong><small>${esc(c.pacienteTelefono || 'Sin teléfono')}</small></td>
                    <td>${c.urgente ? '<span class="badge badge--urgencia">Urgencia</span> ' : ''}${esc(c.motivo || '—')}</td>
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
                    <div>${Fecha.hora12(c.hora)} – ${Fecha.hora12(c.horaFin)} · ${esc(Fecha.larga(c.fecha))}</div>
                    <span class="badge badge--${c.estado}">${c.estado}</span>
                    ${c.urgente ? '<span class="badge badge--urgencia">Urgencia · sobrecupo</span>' : ''}
                </div>
            </div>
            ${aviso}
            <div class="detalle">
                <div class="detalle__dato"><small>Motivo</small><strong>${esc(c.motivo || '—')}</strong></div>
                <div class="detalle__dato"><small>Odontólogo</small><strong>${esc(c.odontologoNombre || '—')}</strong></div>
                <div class="detalle__dato"><small>Teléfono</small><strong>${c.pacienteTelefono
                    ? `<a href="tel:${esc(c.pacienteTelefono)}">${esc(c.pacienteTelefono)}</a>` : '—'}</strong></div>
                <div class="detalle__dato"><small>Duración</small><strong>${Fecha.duracion(c.duracionMinutos)}</strong></div>
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
     * datos: { cita } para editar, o { fecha, hora, odontologoId, paciente, motivo, notas, urgente } para una nueva.
     * Una cita programada se agenda en los horarios libres; una urgencia se atiende como sobrecupo a la hora que llegue.
     */
    abrirFormulario(datos = {}) {
        if (!App.puede('gestion')) return toast('Tu rol no permite agendar citas.', 'info');
        const c = datos.cita;
        const urgente = Boolean(c?.urgente ?? datos.urgente);
        const estado = {
            id: c?.id ?? null,
            paciente: c ? { id: c.pacienteId, nombreCompleto: c.pacienteNombre, telefono: c.pacienteTelefono } : datos.paciente ?? null,
            odontologoId: c?.odontologoId ?? datos.odontologoId ?? App.odontologos[0]?.id,
            fecha: c?.fecha ?? (datos.fecha && datos.fecha >= App.hoy ? datos.fecha : App.hoy),
            hora: c?.hora ?? datos.hora ?? null,
            duracion: c?.duracionMinutos ?? datos.duracion ?? (urgente ? 15 : 30),
            motivo: c?.motivo ?? datos.motivo ?? '',
            notas: c?.notasRecepcion ?? datos.notas ?? '',
        };

        const cuerpo = Modal.abrir(c ? 'Reprogramar / editar cita' : urgente ? 'Registrar urgencia' : 'Nueva cita', `
            <form id="form-cita" novalidate>
                <p class="error-form" id="cita-error" hidden></p>
                <div class="fila-campos">
                    <label class="campo">
                        <span class="campo__etiqueta">Tipo de cita <b class="req">*</b></span>
                        <select name="tipo">
                            <option value="normal">Cita programada</option>
                            <option value="urgencia">Urgencia (sobrecupo)</option>
                        </select>
                    </label>
                    <label class="campo">
                        <span class="campo__etiqueta">Odontólogo <b class="req">*</b></span>
                        <select name="odontologoId">${App.odontologos.map(o =>
                            `<option value="${o.id}">${esc(o.nombreCompleto)}</option>`).join('')}</select>
                    </label>
                </div>
                <div class="campo">
                    <span class="campo__etiqueta">Paciente <b class="req">*</b></span>
                    <div id="cita-paciente"></div>
                    <span class="campo__error" data-error="pacienteId"></span>
                </div>
                <label class="campo">
                    <span class="campo__etiqueta">Procedimiento <b class="req">*</b></span>
                    <select name="procedimiento"></select>
                    <input type="text" name="motivoOtro" maxlength="200" placeholder="Especifica el motivo" hidden>
                    <span class="campo__error" data-error="motivo"></span>
                </label>
                <div class="fila-campos">
                    <label class="campo">
                        <span class="campo__etiqueta">Fecha <b class="req">*</b></span>
                        <input type="date" name="fecha" min="${App.hoy}" required>
                        <span class="campo__error" data-error="fecha"></span>
                    </label>
                    <label class="campo">
                        <span class="campo__etiqueta">Duración <b class="req">*</b></span>
                        <select name="duracionMinutos"></select>
                        <span class="campo__error" data-error="duracionMinutos"></span>
                    </label>
                </div>
                <div class="campo" id="campo-hora">
                    <label class="campo__etiqueta" for="cita-hora">Hora <b class="req">*</b></label>
                    <input type="time" name="hora" id="cita-hora" step="300">
                    <span class="campo__ayuda" id="cita-hora-ayuda"></span>
                    <div class="libres" id="cita-libres"></div>
                    <span class="campo__error" data-error="hora"></span>
                </div>
                <label class="campo" id="campo-hora-urgencia" hidden>
                    <span class="campo__etiqueta">Hora de atención <b class="req">*</b></span>
                    <input type="time" name="horaUrgencia" step="300">
                    <span class="campo__error" data-error="horaUrgencia"></span>
                </label>
                <div id="cita-sobrecupo"></div>
                <label class="campo">
                    <span class="campo__etiqueta">Notas de recepción</span>
                    <textarea name="notasRecepcion" maxlength="1000" placeholder="Indicaciones, referencias, etc. (opcional)"></textarea>
                </label>
                ${c && c.estado === 'Confirmada' ? '<p class="aviso aviso--info">Si cambias la fecha, hora, duración u odontólogo, la cita regresará a <b>Programada</b> y deberá confirmarse de nuevo.</p>' : ''}
                <div class="acciones-form">
                    <button type="button" class="btn btn--borde" data-cerrar>Cancelar</button>
                    <button type="submit" class="btn btn--primario">${icono('check')}${c ? 'Guardar cambios' : 'Agendar'}</button>
                </div>
            </form>`);

        const form = cuerpo.querySelector('#form-cita');
        const esUrgencia = () => form.tipo.value === 'urgencia';
        form.tipo.value = urgente ? 'urgencia' : 'normal';
        form.odontologoId.value = estado.odontologoId;
        form.fecha.value = estado.fecha;
        form.notasRecepcion.value = estado.notas;

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

        /* Procedimientos y duraciones: listas que cambian según el tipo de cita. */
        const pintarProcedimientos = (motivo) => {
            const { valor, otro } = Procedimientos.identificar(motivo, esUrgencia());
            form.procedimiento.innerHTML = '<option value="">Elige el procedimiento…</option>'
                + Procedimientos.opciones(esUrgencia()) + `<option value="${OTRO}">Otro (especificar)</option>`;
            form.procedimiento.value = valor;
            form.motivoOtro.value = otro;
            form.motivoOtro.hidden = valor !== OTRO;
        };
        const pintarDuraciones = (minutos) => {
            const lista = esUrgencia() ? DURACIONES_URGENCIA : DURACIONES;
            const opciones = lista.includes(minutos) ? lista : [...lista, minutos].sort((a, b) => a - b);
            form.duracionMinutos.innerHTML = opciones.map(d => `<option value="${d}">${Fecha.duracion(d)}</option>`).join('');
            form.duracionMinutos.value = lista.includes(minutos) || minutos ? minutos : lista[0];
        };
        const motivoElegido = () => {
            if (form.procedimiento.value === OTRO) return form.motivoOtro.value.trim();
            if (!form.procedimiento.value) return '';
            return esUrgencia() ? `Urgencia: ${form.procedimiento.value}` : form.procedimiento.value;
        };

        const borrador = () => ({
            fecha: form.fecha.value, hora: esUrgencia() ? form.horaUrgencia.value : form.hora.value,
            odontologoId: Number(form.odontologoId.value), duracion: Number(form.duracionMinutos.value),
            paciente: estado.paciente, motivo: motivoElegido(), notas: form.notasRecepcion.value, urgente: esUrgencia(),
        });

        SelectorPaciente.montar(cuerpo.querySelector('#cita-paciente'), estado.paciente, {
            alElegir: p => { estado.paciente = p; limpiarError('pacienteId'); },
            alCrearNuevo: (nombre) => PacientesVista.abrirFormulario({ nombreCompleto: nombre }, nuevo =>
                this.abrirFormulario(c ? { cita: { ...c, ...aCita(borrador(), nuevo) } } : { ...borrador(), paciente: nuevo })),
        });

        /* Debajo de la hora: a qué hora terminaría la cita y si cabe en un espacio libre. */
        const horarioDe = fecha => App.config.horarios[['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][Fecha.aDate(fecha).getDay()]];
        let libres = [];
        const pintarFin = () => {
            const ayuda = cuerpo.querySelector('#cita-hora-ayuda');
            const hora = form.hora.value;
            if (!hora) {
                ayuda.textContent = 'Escribe la hora exacta (ej. 4:10 p. m.) o toca un espacio libre.';
                ayuda.classList.remove('campo__ayuda--alerta');
                return;
            }
            const fin = sumarMinutos(hora, Number(form.duracionMinutos.value));
            const cabe = libres.some(t => hora >= t.inicio && fin <= t.fin);
            ayuda.textContent = cabe
                ? `La cita sería de ${Fecha.hora12(hora)} a ${Fecha.hora12(fin)}`
                : `De ${Fecha.hora12(hora)} a ${Fecha.hora12(fin)} no está libre: se encima con otra cita o pasa del cierre. Elige una hora dentro de los espacios libres.`;
            ayuda.classList.toggle('campo__ayuda--alerta', !cabe);
        };

        /* ---- Cita programada: hora libre (pasos de 5 min) y espacios libres del día como guía ---- */
        const cargarHoras = async () => {
            const lista = cuerpo.querySelector('#cita-libres');
            limpiarError('fecha');
            limpiarError('hora');
            const fecha = form.fecha.value;
            const h = fecha && horarioDe(fecha);
            form.hora.min = h?.apertura || '';
            form.hora.max = h?.cierre || '';
            libres = [];
            const aviso = html => { lista.innerHTML = `<span class="libres__aviso">${html}</span>`; pintarFin(); };
            if (!fecha) return aviso('Primero elige una fecha.');
            if (fecha < App.hoy) {
                form.fecha.classList.add('invalido');
                return aviso('No se pueden agendar citas en fechas pasadas.');
            }
            lista.innerHTML = '<span class="libres__aviso">Buscando espacios libres…</span>';
            try {
                const duracion = Number(form.duracionMinutos.value);
                const disp = await Api.disponibilidad(fecha, form.odontologoId.value, estado.id, duracion);
                if (esUrgencia()) return;
                if (!disp.abierto) {
                    form.fecha.classList.add('invalido');
                    return aviso(esc(disp.mensaje));
                }
                libres = disp.libres || [];
                if (!libres.length) {
                    return aviso(`No hay espacio libre para ${Fecha.duracion(duracion)} este día. Elige otra fecha o, si es una urgencia, cambia el tipo a <b>Urgencia</b>.`);
                }
                lista.innerHTML = '<span class="libres__titulo">Espacios libres:</span>' + libres.map(t =>
                    `<button type="button" class="chip" data-inicio="${t.inicio}">${Fecha.hora12(t.inicio)} – ${Fecha.hora12(t.fin)}</button>`).join('');
                lista.querySelectorAll('[data-inicio]').forEach(b => b.onclick = () => {
                    form.hora.value = b.dataset.inicio;
                    limpiarError('hora');
                    pintarFin();
                });
                if (!form.hora.value && estado.hora) form.hora.value = estado.hora;
                pintarFin();
            } catch (err) {
                aviso(esc(err.message));
            }
        };

        /* ---- Urgencia: hora libre dentro del horario de atención; avisa con quién se encima ---- */
        const horaSugerida = fecha => {
            const h = horarioDe(fecha);
            if (!h) return '';
            let hora = h.apertura;
            if (fecha === App.hoy) {
                const [hh, mm] = App.ahora.split('T')[1].split(':').map(Number);
                const ahora = hh * 60 + Math.ceil(mm / 5) * 5;
                const ahoraTxt = `${String(Math.floor(ahora / 60)).padStart(2, '0')}:${String(ahora % 60).padStart(2, '0')}`;
                if (ahoraTxt > hora) hora = ahoraTxt;
            }
            return hora;
        };
        const revisarUrgencia = async () => {
            const aviso = cuerpo.querySelector('#cita-sobrecupo');
            limpiarError('fecha');
            limpiarError('horaUrgencia');
            const fecha = form.fecha.value;
            const h = fecha && horarioDe(fecha);
            form.horaUrgencia.min = h?.apertura || '';
            form.horaUrgencia.max = h?.cierre || '';
            if (!fecha || fecha < App.hoy) { aviso.innerHTML = '<p class="aviso aviso--alerta">Elige hoy o una fecha futura.</p>'; return; }
            if (!h) { aviso.innerHTML = '<p class="aviso aviso--alerta">La clínica no atiende ese día.</p>'; return; }
            const hora = form.horaUrgencia.value;
            if (!hora) { aviso.innerHTML = ''; return; }
            const fin = sumarMinutos(hora, Number(form.duracionMinutos.value));
            if (hora < h.apertura || fin > h.cierre) {
                aviso.innerHTML = `<p class="aviso aviso--alerta">La urgencia debe atenderse entre ${Fecha.hora12(h.apertura)} y ${Fecha.hora12(h.cierre)}.</p>`;
                return;
            }
            try {
                const disp = await Api.disponibilidad(fecha, form.odontologoId.value, estado.id);
                const encimadas = new Map();
                disp.espacios.forEach(e => {
                    if (e.citaId && e.estadoCita !== 'Inasistencia' && e.citaInicio < fin && e.citaFin > hora) encimadas.set(e.citaId, e);
                    (e.urgencias || []).forEach(u => { if (u.citaId !== estado.id && u.inicio < fin && u.fin > hora) encimadas.set(u.citaId, { ...u, citaInicio: u.inicio, citaFin: u.fin, urgencia: true }); });
                });
                aviso.innerHTML = encimadas.size
                    ? `<p class="aviso aviso--urgencia">${icono('alerta')}<span><b>Sobrecupo:</b> se atiende de ${Fecha.hora12(hora)} a ${Fecha.hora12(fin)} y se encima con
                        ${[...encimadas.values()].map(e => `${esc(e.pacienteNombre)} (${Fecha.hora12(e.citaInicio)} – ${Fecha.hora12(e.citaFin)})${e.urgencia ? ', otra urgencia' : ''}`).join('; ')}.
                        Avísale que espere unos minutos.</span></p>`
                    : `<p class="aviso aviso--info">El odontólogo está libre de ${Fecha.hora12(hora)} a ${Fecha.hora12(fin)}: la urgencia se atiende sin retrasar a nadie.</p>`;
            } catch (err) {
                aviso.innerHTML = `<p class="aviso aviso--alerta">${esc(err.message)}</p>`;
            }
        };

        const actualizar = () => (esUrgencia() ? revisarUrgencia() : cargarHoras());
        const aplicarTipo = (motivo, duracion) => {
            const urg = esUrgencia();
            cuerpo.querySelector('#campo-hora').hidden = urg;
            cuerpo.querySelector('#campo-hora-urgencia').hidden = !urg;
            if (!urg) cuerpo.querySelector('#cita-sobrecupo').innerHTML = '';
            document.getElementById('modal-titulo').textContent = c ? 'Reprogramar / editar cita' : urg ? 'Registrar urgencia' : 'Nueva cita';
            pintarProcedimientos(motivo);
            pintarDuraciones(duracion);
            if (urg && !form.horaUrgencia.value) form.horaUrgencia.value = estado.hora && urgente ? estado.hora : horaSugerida(form.fecha.value);
            actualizar();
        };

        form.tipo.onchange = () => aplicarTipo('', esUrgencia() ? 15 : 30);
        form.procedimiento.onchange = () => {
            limpiarError('motivo');
            form.motivoOtro.hidden = form.procedimiento.value !== OTRO;
            if (form.procedimiento.value === OTRO) form.motivoOtro.focus();
            const minutos = Procedimientos.minutos(form.procedimiento.value, esUrgencia());
            if (minutos && Number(form.duracionMinutos.value) !== minutos) {
                pintarDuraciones(minutos);
                actualizar();
            }
        };
        form.motivoOtro.oninput = () => limpiarError('motivo');
        form.fecha.onchange = () => {
            if (esUrgencia()) form.horaUrgencia.value = horaSugerida(form.fecha.value);
            actualizar();
        };
        form.odontologoId.onchange = actualizar;
        form.duracionMinutos.onchange = actualizar;
        form.hora.oninput = () => { limpiarError('hora'); pintarFin(); };
        form.horaUrgencia.onchange = revisarUrgencia;
        aplicarTipo(estado.motivo, estado.duracion);

        form.onsubmit = async e => {
            e.preventDefault();
            const error = cuerpo.querySelector('#cita-error');
            error.hidden = true;
            const urg = esUrgencia();
            const hora = urg ? form.horaUrgencia.value : form.hora.value;
            const motivo = motivoElegido();
            const errores = {};
            if (!estado.paciente) errores.pacienteId = 'Selecciona o registra un paciente.';
            if (!form.fecha.value) errores.fecha = 'Elige una fecha.';
            else if (form.fecha.value < App.hoy) errores.fecha = 'No se pueden agendar citas en fechas pasadas.';
            if (!hora) errores[urg ? 'horaUrgencia' : 'hora'] = urg ? 'Indica la hora de atención.' : 'Indica la hora de la cita.';
            if (motivo.length < 3) errores.motivo = form.procedimiento.value === OTRO ? 'Describe el motivo (mínimo 3 caracteres).' : 'Elige el procedimiento.';
            if (Object.keys(errores).length) return mostrarErrores(errores);

            const datosCita = {
                pacienteId: estado.paciente.id,
                odontologoId: Number(form.odontologoId.value),
                fecha: form.fecha.value,
                hora,
                duracionMinutos: Number(form.duracionMinutos.value),
                motivo,
                notasRecepcion: form.notasRecepcion.value.trim(),
                urgente: urg,
            };
            const boton = form.querySelector('button[type=submit]');
            boton.disabled = true;
            try {
                const guardada = estado.id ? await Api.actualizarCita(estado.id, datosCita) : await Api.crearCita(datosCita);
                Modal.cerrar();
                toast(`${estado.id ? 'Cita actualizada' : urg ? 'Urgencia registrada' : 'Cita agendada'}: ${guardada.pacienteNombre}, ${Fecha.larga(guardada.fecha)} a las ${Fecha.hora12(guardada.hora)}`);
                Agenda.dia = guardada.fecha;
                Agenda.mes = guardada.fecha.slice(0, 8) + '01';
                App.refrescar();
            } catch (err) {
                error.textContent = err.message;
                error.hidden = false;
                mostrarErrores(err.campos);
                if (err.status === 409 || err.status === 422) actualizar();
            } finally {
                boton.disabled = false;
            }
        };
    }
};

/** Combina un borrador del formulario con un paciente recién creado, en forma de cita para reabrir la edición. */
function aCita(b, paciente) {
    return {
        fecha: b.fecha, hora: b.hora, odontologoId: b.odontologoId, duracionMinutos: b.duracion, motivo: b.motivo, notasRecepcion: b.notas,
        urgente: b.urgente,
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
