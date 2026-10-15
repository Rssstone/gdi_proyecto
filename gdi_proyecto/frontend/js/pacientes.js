/* Pacientes: búsqueda, alta rápida desde recepción y acceso directo para agendar. */

const PacientesVista = {
    iniciar() {
        document.getElementById('pacientes-buscar').oninput = debounce(() => this.cargar());
        document.getElementById('btn-nuevo-paciente').onclick = () => this.abrirFormulario({}, () => App.refrescar());
    },

    async cargar() {
        const cont = document.getElementById('tabla-pacientes');
        cont.innerHTML = '<div class="cargando"></div>';
        try {
            const pacientes = await Api.pacientes(document.getElementById('pacientes-buscar').value.trim());
            if (!pacientes.length) { cont.innerHTML = vacio('No se encontraron pacientes.', 'usuarios'); return; }
            const gestion = App.puede('gestion');
            cont.innerHTML = `<table class="tabla"><thead><tr><th>Paciente</th><th>Teléfono</th><th>Correo</th><th></th></tr></thead>
                <tbody>${pacientes.map(p => `
                    <tr data-id="${p.id}">
                        <td><strong>${esc(p.nombreCompleto)}</strong>${p.fechaNacimiento ? `<small>Nac. ${esc(Fecha.corta(p.fechaNacimiento))}</small>` : ''}</td>
                        <td>${esc(p.telefono || '—')}</td>
                        <td>${esc(p.email || '—')}</td>
                        <td class="acciones">
                            <button class="btn-icono" data-accion="historial" title="Ver citas">${icono('lista')}</button>
                            ${gestion ? `<button class="btn-icono" data-accion="editar" title="Editar datos">${icono('editar')}</button>
                            <button class="btn btn--suave btn--chico" data-accion="agendar">${icono('calendario')}Agendar</button>` : ''}
                        </td>
                    </tr>`).join('')}</tbody></table>`;
            cont.querySelectorAll('tbody tr').forEach(tr => tr.onclick = e => {
                const p = pacientes.find(x => x.id === Number(tr.dataset.id));
                const accion = e.target.closest('[data-accion]')?.dataset.accion || 'historial';
                if (accion === 'agendar') CitasVista.abrirFormulario({ paciente: p, fecha: App.hoy, odontologoId: Agenda.odontologoId });
                if (accion === 'editar') this.abrirFormulario(p, () => App.refrescar());
                if (accion === 'historial') this.verHistorial(p);
            });
        } catch (err) {
            cont.innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
        }
    },

    async verHistorial(p) {
        const cuerpo = Modal.abrir(`Citas de ${p.nombreCompleto}`, '<div class="cargando"></div>');
        try {
            const citas = (await Api.citas({ pacienteId: p.id }));
            cuerpo.innerHTML = citas.length
                ? `<table class="tabla"><tbody>${citas.map(c => `
                    <tr data-id="${c.id}">
                        <td><strong>${esc(Fecha.corta(c.fecha))}</strong><small>${Fecha.hora12(c.hora)}</small></td>
                        <td>${esc(c.motivo || '—')}</td>
                        <td><span class="badge badge--${c.estado}">${c.estado}</span></td>
                    </tr>`).join('')}</tbody></table>`
                : vacio('Este paciente aún no tiene citas.');
            cuerpo.querySelectorAll('tr[data-id]').forEach(tr => tr.onclick = () => CitasVista.verDetalle(Number(tr.dataset.id)));
        } catch (err) {
            cuerpo.innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
        }
    },

    /** Alta (sin id) o edición de paciente. alGuardar recibe el paciente guardado. */
    abrirFormulario(p = {}, alGuardar) {
        const editando = Boolean(p.id);
        const cuerpo = Modal.abrir(editando ? 'Editar paciente' : 'Nuevo paciente', `
            <form id="form-paciente" novalidate>
                <p class="error-form" id="paciente-error" hidden></p>
                <label class="campo">
                    <span class="campo__etiqueta">Nombre completo <b class="req">*</b></span>
                    <input type="text" name="nombreCompleto" maxlength="150" required>
                    <span class="campo__error" data-error="nombreCompleto"></span>
                </label>
                <div class="fila-campos">
                    <label class="campo">
                        <span class="campo__etiqueta">Teléfono (WhatsApp) <b class="req">*</b></span>
                        <input type="tel" name="telefono" maxlength="20" placeholder="10 dígitos" required>
                        <span class="campo__error" data-error="telefono"></span>
                    </label>
                    <label class="campo">
                        <span class="campo__etiqueta">Fecha de nacimiento</span>
                        <input type="date" name="fechaNacimiento" max="${Fecha.sumarDias(App.hoy, -1)}">
                        <span class="campo__error" data-error="fechaNacimiento"></span>
                    </label>
                </div>
                <label class="campo">
                    <span class="campo__etiqueta">Correo electrónico</span>
                    <input type="email" name="email" maxlength="100" placeholder="opcional">
                    <span class="campo__error" data-error="email"></span>
                </label>
                <div class="acciones-form">
                    <button type="button" class="btn btn--borde" data-cerrar>Cancelar</button>
                    <button type="submit" class="btn btn--primario">${icono('check')}Guardar paciente</button>
                </div>
            </form>`, { chico: true });
        const form = cuerpo.querySelector('form');
        form.nombreCompleto.value = p.nombreCompleto || '';
        form.telefono.value = p.telefono || '';
        form.fechaNacimiento.value = p.fechaNacimiento || '';
        form.email.value = p.email || '';

        form.onsubmit = async e => {
            e.preventDefault();
            cuerpo.querySelectorAll('[data-error]').forEach(el => el.textContent = '');
            form.querySelectorAll('.invalido').forEach(el => el.classList.remove('invalido'));
            const datos = {
                nombreCompleto: form.nombreCompleto.value.trim(),
                telefono: form.telefono.value.trim(),
                fechaNacimiento: form.fechaNacimiento.value || null,
                email: form.email.value.trim() || null,
            };
            const errores = {};
            if (!datos.nombreCompleto) errores.nombreCompleto = 'El nombre es obligatorio.';
            if (datos.telefono.replace(/\D/g, '').length < 8) errores.telefono = 'Escribe un teléfono válido (10 dígitos).';
            if (Object.keys(errores).length) return this.marcarErrores(form, cuerpo, errores);

            try {
                const guardado = editando ? await Api.actualizarPaciente(p.id, datos) : await Api.crearPaciente(datos);
                toast(editando ? 'Datos del paciente actualizados.' : `Paciente registrado: ${guardado.nombreCompleto}.`);
                Modal.cerrar();
                alGuardar?.(guardado);
            } catch (err) {
                const error = cuerpo.querySelector('#paciente-error');
                error.textContent = err.message;
                error.hidden = false;
                this.marcarErrores(form, cuerpo, err.campos);
            }
        };
    },

    marcarErrores(form, cuerpo, errores) {
        Object.entries(errores).forEach(([campo, msg]) => {
            const el = cuerpo.querySelector(`[data-error=${campo}]`);
            if (el) el.textContent = msg;
            form.elements[campo]?.classList.add('invalido');
        });
    },
};
