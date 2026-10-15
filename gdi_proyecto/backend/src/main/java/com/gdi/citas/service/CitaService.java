package com.gdi.citas.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.dto.CambioEstadoRequest;
import com.gdi.citas.dto.CitaRequest;
import com.gdi.citas.dto.CitaResponse;
import com.gdi.citas.exception.RecursoNoEncontradoException;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.Cita;
import com.gdi.citas.model.EstadoCita;
import com.gdi.citas.model.Paciente;
import com.gdi.citas.model.Rol;
import com.gdi.citas.model.Usuario;
import com.gdi.citas.repository.CitaRepository;
import com.gdi.citas.repository.PacienteRepository;
import com.gdi.citas.repository.UsuarioRepository;

/** Reglas de negocio del módulo de citas (RF-06, RF-07, RF-08). */
@Service
public class CitaService {

    private static final DateTimeFormatter MARCA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final CitaRepository citaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendaService agenda;

    public CitaService(CitaRepository citaRepository, PacienteRepository pacienteRepository,
                       UsuarioRepository usuarioRepository, AgendaService agenda) {
        this.citaRepository = citaRepository;
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.agenda = agenda;
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> listar(LocalDate desde, LocalDate hasta, EstadoCita estado,
                                     Integer odontologoId, Integer pacienteId, String texto) {
        List<Cita> citas = (desde == null && hasta == null)
                ? citaRepository.findAllByOrderByFechaDescHoraDesc()
                : citaRepository.findByFechaBetweenOrderByFechaAscHoraAsc(
                        desde == null ? LocalDate.of(1900, 1, 1) : desde,
                        hasta == null ? LocalDate.of(9999, 12, 31) : hasta);
        String q = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        return citas.stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> odontologoId == null
                        || (c.getOdontologo() != null && odontologoId.equals(c.getOdontologo().getId())))
                .filter(c -> pacienteId == null || pacienteId.equals(c.getPaciente().getId()))
                .filter(c -> q.isEmpty() || contiene(c.getPaciente().getNombreCompleto(), q)
                        || contiene(c.getMotivo(), q) || contiene(c.getPaciente().getTelefono(), q))
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public CitaResponse obtener(Integer id) {
        return aRespuesta(buscar(id));
    }

    @Transactional
    public CitaResponse crear(CitaRequest req) {
        Cita cita = new Cita();
        aplicarDatos(cita, req);
        agenda.validarHorario(req.fecha(), req.hora());
        validarConflictos(cita);
        cita.setEstado(EstadoCita.Programada);
        return aRespuesta(citaRepository.save(cita));
    }

    @Transactional
    public CitaResponse actualizar(Integer id, CitaRequest req) {
        Cita cita = buscar(id);
        if (!cita.getEstado().esActivo()) {
            throw new ReglaNegocioException("Solo se pueden modificar citas programadas o confirmadas (esta cita está "
                    + cita.getEstado() + ").");
        }
        if (agenda.yaPaso(cita)) {
            throw new ReglaNegocioException("No se puede modificar una cita cuya hora ya pasó.");
        }
        boolean reprogramada = !req.fecha().equals(cita.getFecha()) || !req.hora().equals(cita.getHora())
                || !Objects.equals(req.odontologoId(), cita.getOdontologo() == null ? null : cita.getOdontologo().getId());

        aplicarDatos(cita, req);
        agenda.validarHorario(cita.getFecha(), cita.getHora());
        validarConflictos(cita);
        if (reprogramada) {
            // Un cambio de horario requiere volver a confirmar con el paciente.
            cita.setEstado(EstadoCita.Programada);
        }
        return aRespuesta(cita);
    }

    @Transactional
    public CitaResponse cambiarEstado(Integer id, CambioEstadoRequest req) {
        Cita cita = buscar(id);
        EstadoCita actual = cita.getEstado();
        EstadoCita nuevo = req.estado();
        if (actual == nuevo) {
            return aRespuesta(cita);
        }
        if (!actual.esActivo()) {
            throw new ReglaNegocioException("La cita ya está " + actual.name().toLowerCase(Locale.ROOT)
                    + " y su estado no puede cambiar.");
        }
        boolean paso = agenda.yaPaso(cita);
        switch (nuevo) {
            case Programada -> throw new ReglaNegocioException(
                    "Para regresar una cita a 'Programada' reprograma su fecha u hora.");
            case Confirmada, Cancelada -> {
                if (paso) {
                    throw new ReglaNegocioException(
                            "La hora de la cita ya pasó: márcala como Completada o Inasistencia.");
                }
            }
            case Completada, Inasistencia -> {
                if (!paso) {
                    throw new ReglaNegocioException(
                            "Aún no llega la hora de la cita; no se puede marcar como " + nuevo + ".");
                }
            }
        }
        cita.setEstado(nuevo);
        if (req.nota() != null && !req.nota().isBlank()) {
            String marca = "[" + nuevo + " " + agenda.ahora().format(MARCA) + "] " + req.nota().trim();
            String previas = cita.getNotasRecepcion();
            cita.setNotasRecepcion(previas == null || previas.isBlank() ? marca : previas + "\n" + marca);
        }
        return aRespuesta(cita);
    }

    @Transactional
    public void eliminar(Integer id) {
        Cita cita = buscar(id);
        if (cita.getEstado() == EstadoCita.Completada) {
            throw new ReglaNegocioException(
                    "Las citas completadas forman parte del historial del paciente y no se pueden eliminar.");
        }
        citaRepository.delete(cita);
    }

    private void aplicarDatos(Cita cita, CitaRequest req) {
        Paciente paciente = pacienteRepository.findById(req.pacienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + req.pacienteId()));
        Usuario odontologo = usuarioRepository.findById(req.odontologoId())
                .filter(u -> u.getRol() == Rol.Odontologo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Odontólogo no encontrado: " + req.odontologoId()));
        cita.setPaciente(paciente);
        cita.setOdontologo(odontologo);
        cita.setFecha(req.fecha());
        cita.setHora(req.hora());
        cita.setMotivo(req.motivo().trim());
        cita.setNotasRecepcion(req.notasRecepcion() == null || req.notasRecepcion().isBlank()
                ? null : req.notasRecepcion().trim());
    }

    private void validarConflictos(Cita cita) {
        long conflictos = citaRepository.contarConflictos(cita.getFecha(), cita.getHora(),
                cita.getOdontologo().getId(), cita.getPaciente().getId(), cita.getId(), EstadoCita.ACTIVOS);
        if (conflictos > 0) {
            throw ReglaNegocioException.conflicto("El horario " + cita.getHora() + " del " + cita.getFecha()
                    + " ya está ocupado para el odontólogo o el paciente. Elige otro horario.");
        }
    }

    private Cita buscar(Integer id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
    }

    private CitaResponse aRespuesta(Cita c) {
        return CitaResponse.de(c, agenda.yaPaso(c));
    }

    private static boolean contiene(String valor, String q) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(q);
    }
}
