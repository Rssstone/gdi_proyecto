/* Expediente clínico: odontograma interactivo, antecedentes, fichas técnicas y estudios del paciente. */

/* Estados del diente. color: relleno de la cara (los que se marcan por superficie). */
const ESTADOS_DIENTE = {
    Sano:       { nombre: 'Sano' },
    Caries:     { nombre: 'Caries', color: '#d63b3b', caras: true },
    Restaurado: { nombre: 'Restaurado (resina / amalgama)', color: '#2463b8', caras: true },
    Fractura:   { nombre: 'Fractura', color: '#e8590c', caras: true },
    Corona:     { nombre: 'Corona' },
    Endodoncia: { nombre: 'Endodoncia' },
    Implante:   { nombre: 'Implante' },
    Ortodoncia: { nombre: 'Bracket de ortodoncia' },
    PorExtraer: { nombre: 'Por extraer' },
    Ausente:    { nombre: 'Ausente' },
};
const CARAS = { O: 'Oclusal / incisal', M: 'Mesial', D: 'Distal', V: 'Vestibular', L: 'Lingual / palatino' };
const PIEZAS = ['', 'Incisivo central', 'Incisivo lateral', 'Canino', 'Primer premolar', 'Segundo premolar',
    'Primer molar', 'Segundo molar', 'Tercer molar'];
const CUADRANTES = ['', 'superior derecho', 'superior izquierdo', 'inferior izquierdo', 'inferior derecho'];

function nombreDiente(n) {
    return `${PIEZAS[n % 10]} ${CUADRANTES[Math.floor(n / 10)]}`;
}

const Expediente = {
    paciente: null,
    datos: null,
    pestana: 'odontograma',
    elegido: null,

    /** Abre el expediente del paciente en la pestaña indicada. */
    async abrir(paciente, pestana = 'odontograma') {
        this.paciente = paciente;
        this.pestana = pestana;
        this.elegido = null;
        const cuerpo = Modal.abrir(`Expediente clínico`, '<div class="cargando"></div>', { grande: true });
        try {
            this.datos = await Api.expediente(paciente.id);
        } catch (err) {
            cuerpo.innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
            return;
        }
        cuerpo.innerHTML = `
            <div class="ficha-paciente">
                <div><h4>${esc(paciente.nombreCompleto)}</h4>
                    <small>${esc(paciente.telefono || 'Sin teléfono')}${paciente.fechaNacimiento ? ` · Nac. ${esc(Fecha.corta(paciente.fechaNacimiento))}` : ''}</small></div>
                <span id="exp-alergias"></span>
            </div>
            <div class="segmentos pestanas" id="exp-pestanas">
                <button data-pestana="odontograma">Odontograma</button>
                <button data-pestana="antecedentes">Antecedentes</button>
                <button data-pestana="fichas">Fichas técnicas</button>
                <button data-pestana="estudios">Radiografías y estudios</button>
            </div>
            <div id="exp-contenido"></div>`;
        cuerpo.querySelectorAll('#exp-pestanas button').forEach(b => b.onclick = () => this.mostrar(b.dataset.pestana));
        this.pintarAlergias();
        this.mostrar(this.pestana);
    },

    pintarAlergias() {
        const el = document.getElementById('exp-alergias');
        if (el) el.innerHTML = this.datos.alergias ? `<span class="chip-alerta">${icono('alerta')}Alergias: ${esc(this.datos.alergias)}</span>` : '';
    },

    mostrar(pestana) {
        this.pestana = pestana;
        document.querySelectorAll('#exp-pestanas button').forEach(b => b.classList.toggle('activo', b.dataset.pestana === pestana));
        const cont = document.getElementById('exp-contenido');
        if (pestana === 'odontograma') this.pintarOdontograma(cont);
        if (pestana === 'antecedentes') this.pintarAntecedentes(cont);
        if (pestana === 'fichas') FichasTecnicas.pintarDePaciente(cont, this.paciente);
        if (pestana === 'estudios') this.pintarEstudios(cont);
    },

    /* ================= Odontograma ================= */
    pintarOdontograma(cont) {
        const clinico = App.puede('clinico');
        cont.innerHTML = `
            <div class="odontograma">${Odontograma.svg(this.datos.dientes)}</div>
            ${Odontograma.leyenda()}
            <div class="odontograma-editor">
                <div id="editor-diente" class="editor-diente"></div>
                <div>
                    <span class="campo__etiqueta">Hallazgos registrados</span>
                    <ul class="hallazgos" id="lista-hallazgos"></ul>
                </div>
            </div>`;
        cont.querySelectorAll('.diente').forEach(g => g.onclick = ev => {
            this.elegir(Number(g.dataset.diente), ev.target.dataset.cara);
        });
        const lista = cont.querySelector('#lista-hallazgos');
        lista.innerHTML = this.datos.dientes.length
            ? this.datos.dientes.map(d => `<li data-diente="${d.numero}"><b>${d.numero}</b><span>${esc(ESTADOS_DIENTE[d.estado]?.nombre || d.estado)}
                ${d.superficies.length ? ` · ${d.superficies.join(', ')}` : ''}<small>${esc(nombreDiente(d.numero))}${d.descripcion ? ` — ${esc(d.descripcion)}` : ''}</small></span></li>`).join('')
            : `<li style="cursor:default">Sin hallazgos: todas las piezas están sanas.</li>`;
        lista.querySelectorAll('li[data-diente]').forEach(li => li.onclick = () => this.elegir(Number(li.dataset.diente)));
        if (this.elegido) this.elegir(this.elegido);
        else cont.querySelector('#editor-diente').innerHTML = `<h5>Selecciona una pieza</h5>
            <p>${clinico ? 'Da clic en un diente (o en una de sus caras) para registrar su estado.' : 'Da clic en un diente para ver su detalle. Solo el odontólogo puede modificarlo.'}</p>`;
    },

    elegir(numero, cara) {
        this.elegido = numero;
        document.querySelectorAll('.odontograma .diente').forEach(g => g.classList.toggle('elegido', Number(g.dataset.diente) === numero));
        const editor = document.getElementById('editor-diente');
        const actual = this.datos.dientes.find(d => d.numero === numero) || { numero, estado: 'Sano', superficies: [], descripcion: '' };
        const clinico = App.puede('clinico');
        if (!clinico) {
            editor.innerHTML = `<h5>Pieza ${numero}</h5><p>${esc(nombreDiente(numero))}</p>
                <div class="detalle__dato"><small>Estado</small><strong>${esc(ESTADOS_DIENTE[actual.estado]?.nombre || actual.estado)}</strong></div>
                ${actual.superficies.length ? `<div class="detalle__dato" style="margin-top:8px"><small>Caras</small><strong>${actual.superficies.map(c => CARAS[c]).join(', ')}</strong></div>` : ''}
                ${actual.descripcion ? `<p class="detalle__notas">${esc(actual.descripcion)}</p>` : ''}`;
            return;
        }
        // Si se dio clic en una cara de una pieza sana, se propone marcarla con caries.
        const caras = new Set(actual.superficies);
        let estadoInicial = actual.estado;
        if (cara && (actual.estado === 'Sano' || ESTADOS_DIENTE[actual.estado].caras)) {
            if (actual.estado === 'Sano') estadoInicial = 'Caries';
            caras.add(cara);
        }
        editor.innerHTML = `<form id="form-diente" novalidate>
                <h5>Pieza ${numero}</h5><p>${esc(nombreDiente(numero))}</p>
                <label class="campo">
                    <span class="campo__etiqueta">Estado</span>
                    <select name="estado">${Object.entries(ESTADOS_DIENTE).map(([k, v]) => `<option value="${k}">${esc(v.nombre)}</option>`).join('')}</select>
                </label>
                <div class="campo" id="campo-caras">
                    <span class="campo__etiqueta">Caras afectadas</span>
                    <div class="caras">${Object.entries(CARAS).map(([k, v]) =>
                        `<label><input type="checkbox" name="cara" value="${k}"${caras.has(k) ? ' checked' : ''}> ${k} · ${v}</label>`).join('')}</div>
                </div>
                <label class="campo">
                    <span class="campo__etiqueta">Observaciones</span>
                    <textarea name="descripcion" maxlength="500" placeholder="Opcional">${esc(actual.descripcion || '')}</textarea>
                </label>
                <div class="acciones-form" style="margin-top:4px">
                    <button type="submit" class="btn btn--primario btn--chico">${icono('check')}Guardar pieza</button>
                </div>
            </form>`;
        const form = editor.querySelector('form');
        form.estado.value = estadoInicial;
        const verCaras = () => { editor.querySelector('#campo-caras').hidden = !ESTADOS_DIENTE[form.estado.value].caras; };
        form.estado.onchange = verCaras;
        verCaras();
        form.onsubmit = async e => {
            e.preventDefault();
            const datos = {
                estado: form.estado.value,
                superficies: [...form.querySelectorAll('[name=cara]:checked')].map(x => x.value),
                descripcion: form.descripcion.value.trim(),
            };
            if (ESTADOS_DIENTE[datos.estado].caras && !datos.superficies.length) {
                return toast('Marca al menos una cara afectada.', 'error');
            }
            try {
                const guardado = await Api.guardarDiente(this.paciente.id, numero, datos);
                this.datos.dientes = this.datos.dientes.filter(d => d.numero !== numero);
                if (guardado.estado !== 'Sano' || guardado.descripcion) this.datos.dientes.push(guardado);
                this.datos.dientes.sort((a, b) => a.numero - b.numero);
                toast(`Pieza ${numero} actualizada.`);
                this.pintarOdontograma(document.getElementById('exp-contenido'));
            } catch (err) {
                toast(err.message, 'error');
            }
        };
    },

    /* ================= Antecedentes ================= */
    pintarAntecedentes(cont) {
        const clinico = App.puede('clinico');
        const d = this.datos;
        cont.innerHTML = `<form id="form-antecedentes" novalidate>
                <label class="campo">
                    <span class="campo__etiqueta">Alergias</span>
                    <textarea name="alergias" maxlength="1000" placeholder="Medicamentos, anestesia, látex…">${esc(d.alergias || '')}</textarea>
                </label>
                <label class="campo">
                    <span class="campo__etiqueta">Antecedentes personales patológicos</span>
                    <textarea name="antecedentesPersonales" maxlength="2000" placeholder="Enfermedades, cirugías, medicamentos actuales…">${esc(d.antecedentesPersonales || '')}</textarea>
                </label>
                <label class="campo">
                    <span class="campo__etiqueta">Antecedentes heredofamiliares</span>
                    <textarea name="antecedentesFamiliares" maxlength="2000" placeholder="Diabetes, hipertensión, cardiopatías…">${esc(d.antecedentesFamiliares || '')}</textarea>
                </label>
                ${clinico ? `<div class="acciones-form"><button type="submit" class="btn btn--primario">${icono('check')}Guardar antecedentes</button></div>`
                    : '<p class="aviso aviso--info">Solo el odontólogo o el administrador pueden modificar el expediente.</p>'}
            </form>`;
        const form = cont.querySelector('form');
        if (!clinico) form.querySelectorAll('textarea').forEach(t => { t.readOnly = true; });
        form.onsubmit = async e => {
            e.preventDefault();
            try {
                this.datos = await Api.guardarExpediente(this.paciente.id, {
                    alergias: form.alergias.value, antecedentesPersonales: form.antecedentesPersonales.value,
                    antecedentesFamiliares: form.antecedentesFamiliares.value,
                });
                this.pintarAlergias();
                toast('Antecedentes guardados.');
            } catch (err) {
                toast(err.message, 'error');
            }
        };
    },

    /* ================= Radiografías y estudios ================= */
    async pintarEstudios(cont) {
        cont.innerHTML = `
            <form class="subir-estudio" id="form-estudio" novalidate>
                <label class="campo"><span class="campo__etiqueta">Archivo (JPG, PNG o PDF, máx. 10 MB)</span>
                    <input type="file" name="archivo" accept="image/jpeg,image/png,image/webp,application/pdf"></label>
                <label class="campo"><span class="campo__etiqueta">Tipo</span>
                    <select name="tipo"><option>Radiografía</option><option>Tomografía</option><option>Fotografía</option><option>Otro</option></select></label>
                <label class="campo"><span class="campo__etiqueta">Descripción</span>
                    <input type="text" name="descripcion" maxlength="255" placeholder="Ej. Panorámica inicial"></label>
                <button type="submit" class="btn btn--primario">${icono('mas')}Subir</button>
            </form>
            <div id="lista-estudios"><div class="cargando"></div></div>`;
        const form = cont.querySelector('form');
        form.onsubmit = async e => {
            e.preventDefault();
            const archivo = form.archivo.files[0];
            if (!archivo) return toast('Selecciona un archivo.', 'error');
            if (archivo.size > 10 * 1024 * 1024) return toast('El archivo pesa más de 10 MB.', 'error');
            const fd = new FormData();
            fd.append('archivo', archivo);
            fd.append('tipo', form.tipo.value);
            fd.append('descripcion', form.descripcion.value.trim());
            const boton = form.querySelector('button');
            boton.disabled = true;
            try {
                await Api.subirEstudio(this.paciente.id, fd);
                toast('Estudio guardado en el expediente.');
                form.reset();
                this.cargarEstudios();
            } catch (err) {
                toast(err.message, 'error');
            } finally {
                boton.disabled = false;
            }
        };
        this.cargarEstudios();
    },

    async cargarEstudios() {
        const cont = document.getElementById('lista-estudios');
        if (!cont) return;
        try {
            const estudios = await Api.estudios(this.paciente.id);
            if (!estudios.length) { cont.innerHTML = vacio('Aún no hay radiografías ni estudios en el expediente.', 'diente'); return; }
            const puedeBorrar = App.puede('clinico');
            cont.innerHTML = `<div class="estudios">${estudios.map(e => {
                const url = Api.urlEstudio(e.id);
                const vista = e.tipoMime.startsWith('image/')
                    ? `<img src="${esc(url)}" alt="${esc(e.descripcion || e.nombreArchivo)}" loading="lazy">`
                    : `${icono('lista')}<span style="font-size:12px;font-weight:800">PDF</span>`;
                return `<div class="estudio">
                    <a class="estudio__vista" href="${esc(url)}" target="_blank" rel="noopener" title="Abrir en tamaño completo">${vista}</a>
                    <div class="estudio__info"><div><strong>${esc(e.tipo)}</strong>
                        <small>${esc(e.descripcion || e.nombreArchivo)}</small><small>${esc(Fecha.corta(e.fecha.slice(0, 10)))} · ${e.tamano < 1048576 ? `${Math.max(1, Math.round(e.tamano / 1024))} KB` : `${(e.tamano / 1048576).toFixed(1)} MB`}</small></div>
                        ${puedeBorrar ? `<button class="btn-icono" data-borrar="${e.id}" title="Eliminar">${icono('basura')}</button>` : ''}</div>
                </div>`;
            }).join('')}</div>`;
            cont.querySelectorAll('[data-borrar]').forEach(b => b.onclick = async () => {
                if (!confirm('¿Eliminar este estudio del expediente?')) return;
                try {
                    await Api.eliminarEstudio(Number(b.dataset.borrar));
                    toast('Estudio eliminado.');
                    this.cargarEstudios();
                } catch (err) {
                    toast(err.message, 'error');
                }
            });
        } catch (err) {
            cont.innerHTML = `<p class="error-form">${esc(err.message)}</p>`;
        }
    },
};

/* ================= Dibujo del odontograma (SVG) ================= */
const Odontograma = {
    ANCHO: 50,          // ancho de cada pieza
    CAJA: 32,           // tamaño del cuadro de 5 caras
    MEDIA: 24,          // separación en la línea media

    /** Orden en pantalla: arcada superior 18→11 | 21→28; inferior 48→41 | 31→38 (vista de frente al paciente). */
    filas() {
        const rango = (c, desc) => Array.from({ length: 8 }, (_, i) => c * 10 + (desc ? 8 - i : i + 1));
        return [[...rango(1, true), ...rango(2, false)], [...rango(4, true), ...rango(3, false)]];
    },

    x(i) { return 14 + i * this.ANCHO + (i >= 8 ? this.MEDIA : 0); },

    svg(dientes) {
        const porNumero = Object.fromEntries(dientes.map(d => [d.numero, d]));
        const [sup, inf] = this.filas();
        const anchoTotal = this.x(15) + this.ANCHO + 14;
        const alto = 300;
        const piezas = [
            ...sup.map((n, i) => this.pieza(n, this.x(i), 14, true, porNumero[n])),
            ...inf.map((n, i) => this.pieza(n, this.x(i), 160, false, porNumero[n])),
        ].join('');
        const medio = this.x(8) - this.MEDIA / 2;
        return `<svg viewBox="0 0 ${anchoTotal} ${alto}" role="img" aria-label="Odontograma del paciente">
            <line class="linea-media" x1="${medio}" y1="6" x2="${medio}" y2="${alto - 6}"/>
            <line class="linea-media" x1="8" y1="150" x2="${anchoTotal - 8}" y2="150"/>
            <text class="etiqueta-arcada" x="10" y="146">DERECHO</text>
            <text class="etiqueta-arcada" x="${anchoTotal - 10}" y="146" text-anchor="end">IZQUIERDO</text>
            ${piezas}
        </svg>`;
    },

    /** Una pieza: silueta (corona y raíces) + cuadro de 5 caras + número. */
    pieza(n, x, y, superior, d) {
        const estado = d?.estado || 'Sano';
        const info = ESTADOS_DIENTE[estado] || {};
        const caras = new Set(d?.superficies || []);
        const S = this.CAJA, a = 10, cx = x + (this.ANCHO - S) / 2;
        // Arriba de la arcada superior van las raíces; en la inferior, abajo.
        const cajaY = superior ? y + 64 : y + 22;
        const numY = superior ? y + 118 : y + 12;
        const siluetaY = superior ? y : cajaY + S + 6;
        const cuadrante = Math.floor(n / 10);
        // Mesial siempre mira hacia la línea media; vestibular hacia afuera de la boca.
        const mesialDerecha = cuadrante === 1 || cuadrante === 4;
        const lados = superior ? { arriba: 'V', abajo: 'L' } : { arriba: 'L', abajo: 'V' };
        const izq = mesialDerecha ? 'D' : 'M';
        const der = mesialDerecha ? 'M' : 'D';
        const rel = ([px, py]) => `${cx + px},${cajaY + py}`;
        const poligonos = {
            [lados.arriba]: [[0, 0], [S, 0], [S - a, a], [a, a]],
            [lados.abajo]: [[a, S - a], [S - a, S - a], [S, S], [0, S]],
            [izq]: [[0, 0], [a, a], [a, S - a], [0, S]],
            [der]: [[S, 0], [S, S], [S - a, S - a], [S - a, a]],
        };
        const relleno = cara => (info.caras && caras.has(cara) ? info.color : '');
        const carasSvg = Object.entries(poligonos).map(([cara, pts]) =>
            `<polygon class="cara" data-cara="${cara}" points="${pts.map(rel).join(' ')}"${relleno(cara) ? ` style="fill:${relleno(cara)}"` : ''}><title>${n} · ${CARAS[cara]}</title></polygon>`).join('')
            + `<rect class="cara" data-cara="O" x="${cx + a}" y="${cajaY + a}" width="${S - 2 * a}" height="${S - 2 * a}"${relleno('O') ? ` style="fill:${relleno('O')}"` : ''}><title>${n} · ${CARAS.O}</title></rect>`;

        return `<g class="diente" data-diente="${n}">
            <title>${n} · ${nombreDiente(n)} · ${info.nombre || estado}</title>
            <rect class="caja-fondo" x="${x + 2}" y="${y - 4}" width="${this.ANCHO - 4}" height="134" rx="8" fill="transparent"/>
            ${this.silueta(n, x + this.ANCHO / 2, siluetaY, superior, estado)}
            ${carasSvg}
            ${this.marcaSobreCaja(estado, cx, cajaY, S)}
            <text class="num" x="${x + this.ANCHO / 2}" y="${numY}">${n}</text>
        </g>`;
    },

    /** Silueta simplificada de la pieza: el tamaño y número de raíces dependen del tipo de diente. */
    silueta(n, centro, y, superior, estado) {
        const tipo = n % 10;
        const anchoCorona = tipo <= 2 ? 16 : tipo === 3 ? 18 : tipo <= 5 ? 20 : 28;
        const raices = tipo >= 6 ? (superior ? 3 : 2) : 1;
        const altoRaiz = tipo === 3 ? 34 : 28;
        const altoCorona = 18;
        // Se dibuja con la corona abajo (superior) o arriba (inferior).
        const coronaY = superior ? y + altoRaiz + 2 : y;
        const raizY0 = superior ? coronaY : coronaY + altoCorona;
        const dir = superior ? -1 : 1;
        const x0 = centro - anchoCorona / 2;
        const ausente = estado === 'Ausente';
        let raizSvg = '';
        if (estado === 'Implante') {
            // Tornillo del implante en lugar de raíz.
            const h = altoRaiz;
            const yA = raizY0, yB = raizY0 + dir * h;
            raizSvg = `<rect x="${centro - 4}" y="${Math.min(yA, yB)}" width="8" height="${h}" rx="2" fill="#8a94a6" stroke="#4b5563"/>`
                + Array.from({ length: 5 }, (_, i) => {
                    const yy = raizY0 + dir * (5 + i * 5.5);
                    return `<line x1="${centro - 6}" y1="${yy}" x2="${centro + 6}" y2="${yy + dir * 2}" stroke="#4b5563" stroke-width="1.4"/>`;
                }).join('');
        } else {
            const paso = anchoCorona / (raices + 1);
            raizSvg = Array.from({ length: raices }, (_, i) => {
                const rx = x0 + paso * (i + 1);
                const base = 5;
                return `<path class="silueta" d="M ${rx - base} ${raizY0} L ${rx} ${raizY0 + dir * altoRaiz} L ${rx + base} ${raizY0} Z"/>`
                    + (estado === 'Endodoncia' ? `<line x1="${rx}" y1="${raizY0}" x2="${rx}" y2="${raizY0 + dir * (altoRaiz - 3)}" stroke="#d63b3b" stroke-width="2.4" stroke-linecap="round"/>` : '');
            }).join('');
        }
        const coronaColor = estado === 'Corona' ? ' style="fill:#dbe7fb;stroke:#2463b8;stroke-width:2"' : '';
        const corona = `<rect class="silueta" x="${x0}" y="${coronaY}" width="${anchoCorona}" height="${altoCorona}" rx="${tipo <= 3 ? 7 : 6}"${coronaColor}/>`;
        const bracket = estado === 'Ortodoncia'
            ? `<rect x="${centro - 4}" y="${coronaY + altoCorona / 2 - 4}" width="8" height="8" rx="1.5" fill="#5f9e1c"/><line x1="${x0 - 4}" y1="${coronaY + altoCorona / 2}" x2="${x0 + anchoCorona + 4}" y2="${coronaY + altoCorona / 2}" stroke="#5f9e1c" stroke-width="1.6"/>`
            : '';
        return `<g opacity="${ausente ? 0.25 : 1}">${raizSvg}${corona}${bracket}</g>`;
    },

    /** Marcas que aplican a toda la pieza, dibujadas sobre el cuadro de caras. */
    marcaSobreCaja(estado, x, y, S) {
        const cruz = color => `<g stroke="${color}" stroke-width="3" stroke-linecap="round" pointer-events="none">
            <line x1="${x - 3}" y1="${y - 3}" x2="${x + S + 3}" y2="${y + S + 3}"/><line x1="${x + S + 3}" y1="${y - 3}" x2="${x - 3}" y2="${y + S + 3}"/></g>`;
        if (estado === 'Ausente') return cruz('#2463b8');
        if (estado === 'PorExtraer') return cruz('#d63b3b');
        if (estado === 'Corona') return `<circle cx="${x + S / 2}" cy="${y + S / 2}" r="${S / 2 + 4}" fill="none" stroke="#2463b8" stroke-width="2.5" pointer-events="none"/>`;
        return '';
    },

    leyenda() {
        const cuadro = color => `<svg viewBox="0 0 22 22"><rect x="2" y="2" width="18" height="18" rx="3" fill="${color}" stroke="#7a869a"/></svg>`;
        const item = (svg, texto) => `<li>${svg}${texto}</li>`;
        return `<ul class="leyenda-odontograma">
            ${item(cuadro('#d63b3b'), 'Caries')}
            ${item(cuadro('#2463b8'), 'Restaurado')}
            ${item(cuadro('#e8590c'), 'Fractura')}
            ${item('<svg viewBox="0 0 22 22"><circle cx="11" cy="11" r="8" fill="none" stroke="#2463b8" stroke-width="2.5"/></svg>', 'Corona')}
            ${item('<svg viewBox="0 0 22 22"><path d="M8 3 11 19 14 3Z" fill="#fbfbfd" stroke="#9aa5b5"/><line x1="11" y1="4" x2="11" y2="16" stroke="#d63b3b" stroke-width="2.4"/></svg>', 'Endodoncia')}
            ${item('<svg viewBox="0 0 22 22"><rect x="7" y="3" width="8" height="16" rx="2" fill="#8a94a6"/></svg>', 'Implante')}
            ${item('<svg viewBox="0 0 22 22"><rect x="7" y="7" width="8" height="8" fill="#5f9e1c"/><line x1="1" y1="11" x2="21" y2="11" stroke="#5f9e1c" stroke-width="1.6"/></svg>', 'Bracket')}
            ${item('<svg viewBox="0 0 22 22"><path d="M4 4l14 14M18 4 4 18" stroke="#d63b3b" stroke-width="3"/></svg>', 'Por extraer')}
            ${item('<svg viewBox="0 0 22 22"><path d="M4 4l14 14M18 4 4 18" stroke="#2463b8" stroke-width="3"/></svg>', 'Ausente')}
        </ul>`;
    },
};
