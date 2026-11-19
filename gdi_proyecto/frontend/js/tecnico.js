/* Módulo técnico: especificaciones de coronas, puentes y prótesis (las llena el odontólogo). */

const TIPOS_TRABAJO = {
    Corona: 'Corona', CoronaSobreImplante: 'Corona sobre implante', Puente: 'Puente', Carilla: 'Carilla',
    Incrustacion: 'Incrustación', ProtesisRemovible: 'Prótesis removible', ProtesisTotal: 'Prótesis total', Otro: 'Otro',
};
const MATERIALES = ['Zirconia', 'Disilicato de litio (e.max)', 'Metal-porcelana', 'Porcelana pura', 'Resina',
    'Metal colado', 'Acrílico', 'Otro'];
const COLORES_VITA = ['A1', 'A2', 'A3', 'A3.5', 'A4', 'B1', 'B2', 'B3', 'B4', 'C1', 'C2', 'C3', 'C4', 'D2', 'D3', 'D4',
    'BL1', 'BL2', 'BL3', 'BL4'];
const TERMINACIONES = ['Chamfer', 'Hombro', 'Hombro biselado', 'Filo de cuchillo'];

const FichasTecnicas = {
    /** Etiqueta de entrega: cuántos días faltan o si ya pasó la fecha acordada con el laboratorio. */
    entrega(f) {
        if (!f.fechaEntrega) return '<span class="badge badge--Cancelada">Sin fecha de entrega</span>';
        const dias = Math.round((Fecha.aDate(f.fechaEntrega) - Fecha.aDate(App.hoy)) / 86400000);
        if (dias === 0) return '<span class="badge badge--entrega-hoy">Se entrega hoy</span>';
        if (dias < 0) return `<span class="badge badge--entrega-vencida">Entrega: ${esc(Fecha.corta(f.fechaEntrega))}</span>`;
        return `<span class="badge badge--entrega">Entrega en ${dias} día${dias > 1 ? 's' : ''}</span>`;
    },

    medidas(f) {
        const m = [['Ancho', f.anchoMm], ['Alto', f.altoMm], ['Grosor', f.grosorMm]].filter(([, v]) => v != null);
        return m.length ? `<span class="medidas">${m.map(([k, v]) => `<span class="medida">${k} ${Number(v).toFixed(1)} mm</span>`).join('')}</span>` : '—';
    },

    /** Lista de fichas dentro del expediente del paciente. */
    async pintarDePaciente(cont, paciente) {
        const clinico = App.puede('clinico');
        cont.innerHTML = `${clinico ? `<div class="acciones-form" style="margin:0 0 12px;justify-content:flex-start">
                <button class="btn btn--primario" id="btn-ficha-paciente">${icono('mas')}Nueva ficha técnica</button></div>` : ''}
            <div id="lista-fichas"><div class="cargando"></div></div>`;
        const volver = () => Expediente.abrir(paciente, 'fichas');
        cont.querySelector('#btn-ficha-paciente')?.addEventListener('click', () => this.abrirFormulario({ paciente }, volver));
        try {
            const fichas = await Api.fichas(paciente.id);
            const lista = cont.querySelector('#lista-fichas');
            if (!fichas.length) { lista.innerHTML = vacio('Este paciente no tiene fichas técnicas (coronas, puentes o prótesis).', 'diente'); return; }
            lista.innerHTML = `<div class="fichas">${fichas.map(f => `
                <div class="ficha-card" data-id="${f.id}">
                    <div><h5>${esc(TIPOS_TRABAJO[f.tipoTrabajo])} · pieza${f.piezas.includes(',') ? 's' : ''} ${esc(f.piezas)}</h5>
                        <small>${esc(f.material)}${f.colorVita ? ` · color ${esc(f.colorVita)}` : ''} · tomada el ${esc(Fecha.corta(f.fechaToma))}</small></div>
                    <div>${this.entrega(f)}</div>
                    <div style="grid-column:1/-1">${this.medidas(f)}</div>
                </div>`).join('')}</div>`;
            lista.querySelectorAll('.ficha-card').forEach(c => c.onclick = () =>
                this.verDetalle(fichas.find(f => f.id === Number(c.dataset.id)), volver));
        } catch (err) {
            cont.querySelector('#lista-fichas').innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
        }
    },

    verDetalle(f, alCerrar) {
        const clinico = App.puede('clinico');
        const dato = (titulo, valor, ancho) => `<div class="detalle__dato${ancho ? ' detalle__ancho' : ''}"><small>${titulo}</small><strong>${valor}</strong></div>`;
        const cuerpo = Modal.abrir('Ficha técnica', `
            <div class="detalle__cabecera">
                <div class="detalle__fecha"><span>Pieza</span><b>${esc(f.piezas.split(',')[0])}</b><span>${f.piezas.includes(',') ? '+' + (f.piezas.split(',').length - 1) : 'FDI'}</span></div>
                <div><h4>${esc(TIPOS_TRABAJO[f.tipoTrabajo])} · ${esc(f.pacienteNombre)}</h4>
                    <div>Piezas ${esc(f.piezas)} · ${esc(f.odontologoNombre || 'Sin odontólogo')}</div>${this.entrega(f)}</div>
            </div>
            <div class="ficha-tecnica">
                ${dato('Material', esc(f.material))}
                ${dato('Color (guía VITA)', f.colorVita ? `<span class="color-vita"><i></i>${esc(f.colorVita)}</span>` : '—')}
                ${dato('Terminación', esc(f.terminacion || '—'))}
                ${dato('Ancho mesiodistal', f.anchoMm != null ? `${Number(f.anchoMm).toFixed(2)} mm` : '—')}
                ${dato('Alto', f.altoMm != null ? `${Number(f.altoMm).toFixed(2)} mm` : '—')}
                ${dato('Grosor', f.grosorMm != null ? `${Number(f.grosorMm).toFixed(2)} mm` : '—')}
                ${dato('Toma de medidas', esc(Fecha.corta(f.fechaToma)))}
                ${dato('Entrega del laboratorio', f.fechaEntrega ? esc(Fecha.corta(f.fechaEntrega)) : '—')}
                ${dato('Laboratorio', esc(f.laboratorio || '—'))}
                ${f.notas ? dato('Notas', `<p class="detalle__notas">${esc(f.notas)}</p>`, true) : ''}
            </div>
            <div class="botonera" style="margin-top:18px">
                ${alCerrar ? `<button class="btn btn--borde" data-accion="volver">${icono('izq')}Volver</button>` : ''}
                <span class="separador"></span>
                ${clinico ? `<button class="btn-icono" data-accion="eliminar" title="Eliminar ficha">${icono('basura')}</button>
                    <button class="btn btn--primario" data-accion="editar">${icono('editar')}Editar</button>` : ''}
            </div>`);
        cuerpo.querySelector('[data-accion=volver]')?.addEventListener('click', alCerrar);
        cuerpo.querySelector('[data-accion=editar]')?.addEventListener('click', () => this.abrirFormulario({ ficha: f }, alCerrar));
        cuerpo.querySelector('[data-accion=eliminar]')?.addEventListener('click', async () => {
            const ok = await Modal.confirmar({ titulo: 'Eliminar ficha técnica', mensaje: `Se eliminará la ficha de ${esc(TIPOS_TRABAJO[f.tipoTrabajo])} (piezas ${esc(f.piezas)}).`, textoBoton: 'Eliminar', peligro: true });
            if (ok === null) return;
            try {
                await Api.eliminarFicha(f.id);
                toast('Ficha técnica eliminada.');
                alCerrar ? alCerrar() : (Modal.cerrar(), TecnicoVista.cargar());
            } catch (err) {
                toast(err.message, 'error');
            }
        });
    },

    /** Alta o edición. datos: { ficha } para editar o { paciente } para una nueva. */
    abrirFormulario(datos = {}, alGuardar) {
        if (!App.puede('clinico')) return toast('Solo el odontólogo o el administrador registran fichas técnicas.', 'info');
        const f = datos.ficha;
        let paciente = f ? { id: f.pacienteId, nombreCompleto: f.pacienteNombre } : datos.paciente ?? null;
        const propio = App.odontologos.find(o => o.id === App.usuario.id);
        const opciones = (lista, actual, vacioTxt) => (vacioTxt ? `<option value="">${vacioTxt}</option>` : '')
            + [...new Set([...lista, ...(actual && !lista.includes(actual) ? [actual] : [])])].map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join('');
        const cuerpo = Modal.abrir(f ? 'Editar ficha técnica' : 'Nueva ficha técnica', `
            <form id="form-ficha" novalidate>
                <p class="error-form" id="ficha-error" hidden></p>
                <div class="campo">
                    <span class="campo__etiqueta">Paciente <b class="req">*</b></span>
                    <div id="ficha-paciente"></div>
                    <span class="campo__error" data-error="pacienteId"></span>
                </div>
                <div class="fila-campos fila-campos--3">
                    <label class="campo"><span class="campo__etiqueta">Tipo de trabajo <b class="req">*</b></span>
                        <select name="tipoTrabajo">${Object.entries(TIPOS_TRABAJO).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}</select></label>
                    <label class="campo"><span class="campo__etiqueta">Piezas (FDI) <b class="req">*</b></span>
                        <input type="text" name="piezas" maxlength="60" placeholder="Ej. 16 o 14, 15, 16">
                        <span class="campo__error" data-error="piezas"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Odontólogo <b class="req">*</b></span>
                        <select name="odontologoId">${App.odontologos.map(o => `<option value="${o.id}">${esc(o.nombreCompleto)}</option>`).join('')}</select></label>
                </div>
                <div class="fila-campos fila-campos--3">
                    <label class="campo"><span class="campo__etiqueta">Material <b class="req">*</b></span>
                        <select name="material">${opciones(MATERIALES, f?.material, 'Elige el material…')}</select>
                        <span class="campo__error" data-error="material"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Color (guía VITA)</span>
                        <select name="colorVita">${opciones(COLORES_VITA, f?.colorVita, 'Sin especificar')}</select>
                        <span class="campo__error" data-error="colorVita"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Terminación de la preparación</span>
                        <select name="terminacion">${opciones(TERMINACIONES, f?.terminacion, 'Sin especificar')}</select></label>
                </div>
                <div class="fila-campos fila-campos--3">
                    <label class="campo"><span class="campo__etiqueta">Ancho mesiodistal (mm)</span>
                        <input type="number" name="anchoMm" min="0.1" max="40" step="0.1" placeholder="Ej. 10.5">
                        <span class="campo__error" data-error="anchoMm"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Alto (mm)</span>
                        <input type="number" name="altoMm" min="0.1" max="40" step="0.1" placeholder="Ej. 7.2">
                        <span class="campo__error" data-error="altoMm"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Grosor (mm)</span>
                        <input type="number" name="grosorMm" min="0.1" max="10" step="0.1" placeholder="Ej. 1.5">
                        <span class="campo__error" data-error="grosorMm"></span></label>
                </div>
                <div class="fila-campos fila-campos--3">
                    <label class="campo"><span class="campo__etiqueta">Toma de medidas <b class="req">*</b></span>
                        <input type="date" name="fechaToma" max="${App.hoy}">
                        <span class="campo__error" data-error="fechaToma"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Fecha de entrega del laboratorio</span>
                        <input type="date" name="fechaEntrega">
                        <span class="campo__error" data-error="fechaEntrega"></span></label>
                    <label class="campo"><span class="campo__etiqueta">Laboratorio</span>
                        <input type="text" name="laboratorio" maxlength="120" placeholder="Opcional"></label>
                </div>
                <label class="campo"><span class="campo__etiqueta">Notas para el laboratorio</span>
                    <textarea name="notas" maxlength="1000" placeholder="Contactos, oclusión, forma, textura…"></textarea></label>
                <div class="acciones-form">
                    <button type="button" class="btn btn--borde" data-accion="cancelar">Cancelar</button>
                    <button type="submit" class="btn btn--primario">${icono('check')}Guardar ficha</button>
                </div>
            </form>`);
        const form = cuerpo.querySelector('form');
        form.tipoTrabajo.value = f?.tipoTrabajo || 'Corona';
        form.piezas.value = f?.piezas || '';
        form.odontologoId.value = f?.odontologoId || propio?.id || App.odontologos[0]?.id;
        form.material.value = f?.material || '';
        form.colorVita.value = f?.colorVita || '';
        form.terminacion.value = f?.terminacion || '';
        form.anchoMm.value = f?.anchoMm ?? '';
        form.altoMm.value = f?.altoMm ?? '';
        form.grosorMm.value = f?.grosorMm ?? '';
        form.fechaToma.value = f?.fechaToma || App.hoy;
        form.fechaEntrega.value = f?.fechaEntrega || '';
        form.fechaEntrega.min = form.fechaToma.value;
        form.fechaToma.onchange = () => { form.fechaEntrega.min = form.fechaToma.value; };
        form.laboratorio.value = f?.laboratorio || '';
        form.notas.value = f?.notas || '';
        cuerpo.querySelector('[data-accion=cancelar]').onclick = () => (alGuardar ? alGuardar() : Modal.cerrar());

        SelectorPaciente.montar(cuerpo.querySelector('#ficha-paciente'), paciente, {
            alElegir: p => { paciente = p; },
            alCrearNuevo: () => toast('Registra primero al paciente en la pestaña Pacientes.', 'info'),
        });

        form.onsubmit = async e => {
            e.preventDefault();
            cuerpo.querySelectorAll('[data-error]').forEach(el => { el.textContent = ''; });
            const num = v => (v === '' ? null : Number(v));
            const datosFicha = {
                pacienteId: paciente?.id, odontologoId: Number(form.odontologoId.value), tipoTrabajo: form.tipoTrabajo.value,
                piezas: form.piezas.value.trim(), material: form.material.value, colorVita: form.colorVita.value || null,
                terminacion: form.terminacion.value || null, anchoMm: num(form.anchoMm.value), altoMm: num(form.altoMm.value),
                grosorMm: num(form.grosorMm.value), fechaToma: form.fechaToma.value, fechaEntrega: form.fechaEntrega.value || null,
                laboratorio: form.laboratorio.value.trim(), notas: form.notas.value.trim(),
            };
            const errores = {};
            if (!datosFicha.pacienteId) errores.pacienteId = 'Selecciona un paciente.';
            if (!/^\s*[1-8][1-8](\s*[,-]\s*[1-8][1-8])*\s*$/.test(datosFicha.piezas)) errores.piezas = 'Usa la numeración FDI, ej. 16 o 14, 15, 16.';
            if (!datosFicha.material) errores.material = 'Elige el material.';
            if (!datosFicha.fechaToma) errores.fechaToma = 'Indica la fecha.';
            if (datosFicha.fechaEntrega && datosFicha.fechaEntrega < datosFicha.fechaToma) errores.fechaEntrega = 'No puede ser antes de la toma de medidas.';
            if (Object.keys(errores).length) return PacientesVista.marcarErrores(form, cuerpo, errores);
            try {
                const guardada = f ? await Api.actualizarFicha(f.id, datosFicha) : await Api.crearFicha(datosFicha);
                toast(f ? 'Ficha técnica actualizada.' : `Ficha técnica registrada: ${TIPOS_TRABAJO[guardada.tipoTrabajo]} pieza(s) ${guardada.piezas}.`);
                if (alGuardar) alGuardar(); else { Modal.cerrar(); TecnicoVista.cargar(); }
            } catch (err) {
                const el = cuerpo.querySelector('#ficha-error');
                el.textContent = err.message;
                el.hidden = false;
                PacientesVista.marcarErrores(form, cuerpo, err.campos || {});
            }
        };
    },
};

/* Pestaña "Módulo técnico": todas las fichas, con las próximas entregas del laboratorio. */
const TecnicoVista = {
    filtro: 'pendientes',

    iniciar() {
        document.getElementById('tecnico-buscar').oninput = debounce(() => this.cargar());
        document.querySelectorAll('#tecnico-filtro button').forEach(b => b.onclick = () => {
            this.filtro = b.dataset.filtro;
            document.querySelectorAll('#tecnico-filtro button').forEach(x => x.classList.toggle('activo', x === b));
            this.cargar();
        });
        document.getElementById('btn-nueva-ficha').onclick = () => FichasTecnicas.abrirFormulario({});
    },

    async cargar() {
        const cont = document.getElementById('tabla-tecnico');
        cont.innerHTML = '<div class="cargando"></div>';
        try {
            const q = document.getElementById('tecnico-buscar').value.trim().toLowerCase();
            let fichas = await Api.fichas();
            if (q) fichas = fichas.filter(f => `${f.pacienteNombre} ${f.piezas} ${f.material} ${TIPOS_TRABAJO[f.tipoTrabajo]}`.toLowerCase().includes(q));
            if (this.filtro === 'pendientes') {
                fichas = fichas.filter(f => !f.fechaEntrega || f.fechaEntrega >= App.hoy)
                    .sort((a, b) => (a.fechaEntrega || '9999').localeCompare(b.fechaEntrega || '9999'));
            }
            if (!fichas.length) { cont.innerHTML = vacio(this.filtro === 'pendientes' ? 'No hay trabajos pendientes de entrega.' : 'No hay fichas técnicas.', 'diente'); return; }
            cont.innerHTML = `<table class="tabla"><thead><tr><th>Paciente</th><th>Trabajo</th><th>Material y color</th><th>Medidas</th><th>Entrega</th></tr></thead>
                <tbody>${fichas.map(f => `<tr data-id="${f.id}">
                    <td><strong>${esc(f.pacienteNombre)}</strong><small>${esc(f.odontologoNombre || '')}</small></td>
                    <td><strong>${esc(TIPOS_TRABAJO[f.tipoTrabajo])}</strong><small>Pieza(s) ${esc(f.piezas)}</small></td>
                    <td>${esc(f.material)}<small>${f.colorVita ? `Color ${esc(f.colorVita)}` : 'Sin color'}${f.terminacion ? ` · ${esc(f.terminacion)}` : ''}</small></td>
                    <td>${FichasTecnicas.medidas(f)}</td>
                    <td>${FichasTecnicas.entrega(f)}<small>${f.laboratorio ? esc(f.laboratorio) : ''}</small></td>
                </tr>`).join('')}</tbody></table>`;
            cont.querySelectorAll('tbody tr').forEach(tr => tr.onclick = () =>
                FichasTecnicas.verDetalle(fichas.find(f => f.id === Number(tr.dataset.id))));
        } catch (err) {
            cont.innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
        }
    },
};
