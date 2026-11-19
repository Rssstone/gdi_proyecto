package com.gdi.citas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.config.Bitacora;
import com.gdi.citas.dto.ExpedienteDtos.CambioResponse;
import com.gdi.citas.dto.ExpedienteDtos.DienteRequest;
import com.gdi.citas.dto.ExpedienteDtos.DienteResponse;
import com.gdi.citas.dto.ExpedienteDtos.ExpedienteRequest;
import com.gdi.citas.dto.ExpedienteDtos.ExpedienteResponse;
import com.gdi.citas.exception.RecursoNoEncontradoException;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.CambioOdontograma;
import com.gdi.citas.model.DienteOdontograma;
import com.gdi.citas.model.EstadoDiente;
import com.gdi.citas.model.EstadoPieza;
import com.gdi.citas.model.Expediente;
import com.gdi.citas.model.Paciente;
import com.gdi.citas.repository.CambioOdontogramaRepository;
import com.gdi.citas.repository.DienteOdontogramaRepository;
import com.gdi.citas.repository.ExpedienteRepository;
import com.gdi.citas.repository.PacienteRepository;
import com.gdi.citas.repository.UsuarioRepository;

/** Expediente clínico y odontograma del paciente, con historial de cambios por pieza. */
@Service
public class ExpedienteService {


    private final PacienteRepository pacienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final DienteOdontogramaRepository dienteRepository;
    private final CambioOdontogramaRepository cambioRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public ExpedienteService(PacienteRepository pacienteRepository, ExpedienteRepository expedienteRepository,
                             DienteOdontogramaRepository dienteRepository, CambioOdontogramaRepository cambioRepository,
                             UsuarioRepository usuarioRepository, Clock clock) {
        this.pacienteRepository = pacienteRepository;
        this.expedienteRepository = expedienteRepository;
        this.dienteRepository = dienteRepository;
        this.cambioRepository = cambioRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    /** Expediente con el odontograma actual, o con el que tenía al final del día indicado. */
    @Transactional(readOnly = true)
    public ExpedienteResponse obtener(Integer pacienteId, LocalDate fecha) {
        Paciente paciente = buscarPaciente(pacienteId);
        Expediente expediente = expedienteRepository.findFirstByPacienteIdOrderByIdAsc(pacienteId).orElse(null);
        List<DienteResponse> dientes = expediente == null ? List.of() : odontograma(expediente, fecha);
        return ExpedienteResponse.de(pacienteId, paciente.getNombreCompleto(), expediente, dientes);
    }

    @Transactional(readOnly = true)
    public List<CambioResponse> historial(Integer pacienteId) {
        buscarPaciente(pacienteId);
        return expedienteRepository.findFirstByPacienteIdOrderByIdAsc(pacienteId)
                .map(e -> cambioRepository.findByExpedienteIdOrderByFechaAscIdAsc(e.getId()).stream()
                        .sorted(Comparator.comparing(CambioOdontograma::getFecha).thenComparing(CambioOdontograma::getId).reversed())
                        .map(CambioResponse::de).toList())
                .orElse(List.of());
    }

    @Transactional
    public ExpedienteResponse guardar(Integer pacienteId, ExpedienteRequest req) {
        Expediente e = expedienteDe(buscarPaciente(pacienteId));
        e.setAntecedentesPersonales(limpiar(req.antecedentesPersonales()));
        e.setAntecedentesFamiliares(limpiar(req.antecedentesFamiliares()));
        e.setAlergias(limpiar(req.alergias()));
        Bitacora.anotar("Expediente de {}: antecedentes y alergias actualizados", e.getPaciente().getNombreCompleto());
        return obtener(pacienteId, null);
    }

    /**
     * Registra el estado de una pieza y deja el cambio en el historial. Si queda sana y sin notas se borra el
     * registro, porque el odontograma solo guarda hallazgos.
     */
    @Transactional
    public DienteResponse guardarDiente(Integer pacienteId, int numero, DienteRequest req) {
        validarNumero(numero);
        EstadoPieza nuevo = validar(req);
        Expediente expediente = expedienteDe(buscarPaciente(pacienteId));
        DienteOdontograma diente = dienteRepository.findByExpedienteIdAndNumeroDiente(expediente.getId(), numero)
                .orElseGet(() -> {
                    DienteOdontograma d = new DienteOdontograma();
                    d.setExpediente(expediente);
                    d.setNumeroDiente(numero);
                    return d;
                });
        EstadoPieza anterior = diente.getId() == null ? EstadoPieza.SANA : diente.getEstadoPieza();
        String descripcion = limpiar(req.descripcion());

        if (!anterior.equals(nuevo) || !Objects.equals(descripcion, diente.getDescripcion())) {
            CambioOdontograma cambio = new CambioOdontograma();
            cambio.setExpediente(expediente);
            cambio.setNumeroDiente(numero);
            cambio.setAnterior(anterior);
            cambio.setNuevo(nuevo);
            cambio.setDescripcion(descripcion);
            cambio.setUsuario(req.usuarioId() == null ? null : usuarioRepository.findById(req.usuarioId()).orElse(null));
            cambio.setFecha(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
            cambioRepository.save(cambio);
            Bitacora.anotar("Odontograma de {} · diente {}: {} -> {}", expediente.getPaciente().getNombreCompleto(), numero,
                    anterior.texto(), nuevo.texto());
        }

        if (nuevo.esSana() && descripcion == null) {
            if (diente.getId() != null) dienteRepository.delete(diente);
            return DienteResponse.de(numero, EstadoPieza.SANA, null);
        }
        diente.setEstadoPieza(nuevo);
        diente.setDescripcion(descripcion);
        dienteRepository.save(diente);
        return DienteResponse.de(numero, nuevo, descripcion);
    }

    /**
     * Odontograma al final del día indicado: una pieza que cambió después de esa fecha estaba como indica el
     * "antes" de su primer cambio posterior; las demás siguen como hoy.
     */
    private List<DienteResponse> odontograma(Expediente expediente, LocalDate fecha) {
        Map<Integer, DienteResponse> actuales = new TreeMap<>();
        dienteRepository.findByExpedienteIdOrderByNumeroDienteAsc(expediente.getId())
                .forEach(d -> actuales.put(d.getNumeroDiente(), DienteResponse.de(d.getNumeroDiente(), d.getEstadoPieza(), d.getDescripcion())));
        if (fecha == null) return List.copyOf(actuales.values());

        LocalDateTime limite = LocalDateTime.of(fecha, LocalTime.MAX);
        Map<Integer, CambioOdontograma> primerCambioPosterior = cambioRepository
                .findByExpedienteIdOrderByFechaAscIdAsc(expediente.getId()).stream()
                .filter(c -> c.getFecha().isAfter(limite))
                .collect(Collectors.toMap(CambioOdontograma::getNumeroDiente, c -> c, (primero, otro) -> primero, LinkedHashMap::new));
        Map<Integer, DienteResponse> enFecha = new TreeMap<>(actuales);
        primerCambioPosterior.forEach((numero, cambio) -> {
            EstadoPieza antes = cambio.getAnterior();
            if (antes.esSana()) enFecha.remove(numero);
            else enFecha.put(numero, DienteResponse.de(numero, antes, null));
        });
        return List.copyOf(enFecha.values());
    }

    /** La pieza completa solo admite hallazgos de pieza; las caras, solo caries, restauración o fractura. */
    private static EstadoPieza validar(DienteRequest req) {
        if (req.estado().esPorSuperficie()) {
            throw new ReglaNegocioException(req.estado() + " se marca por cara del diente, no en la pieza completa.");
        }
        Map<String, EstadoDiente> caras = req.caras() == null || req.estado() == EstadoDiente.Ausente
                ? Map.of() : req.caras();
        caras.forEach((cara, e) -> {
            if (!EstadoPieza.CARAS.contains(cara)) throw new ReglaNegocioException("Cara no válida: " + cara);
            if (e == null || !e.esPorSuperficie()) {
                throw new ReglaNegocioException("En la cara " + cara + " solo se marca caries, restauración o fractura.");
            }
        });
        return EstadoPieza.nueva(req.estado(), caras);
    }

    /** Numeración FDI de dientes permanentes: cuadrante 1 a 4 y pieza 1 a 8 (11 a 48). */
    private static void validarNumero(int numero) {
        int cuadrante = numero / 10;
        int pieza = numero % 10;
        if (cuadrante < 1 || cuadrante > 4 || pieza < 1 || pieza > 8) {
            throw new ReglaNegocioException("Pieza dental no válida: " + numero + " (numeración FDI de 11 a 48).");
        }
    }

    private Expediente expedienteDe(Paciente paciente) {
        return expedienteRepository.findFirstByPacienteIdOrderByIdAsc(paciente.getId()).orElseGet(() -> {
            Expediente nuevo = new Expediente();
            nuevo.setPaciente(paciente);
            return expedienteRepository.save(nuevo);
        });
    }

    private Paciente buscarPaciente(Integer id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + id));
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
