package com.gdi.citas.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.dto.ExpedienteDtos.DienteRequest;
import com.gdi.citas.dto.ExpedienteDtos.DienteResponse;
import com.gdi.citas.dto.ExpedienteDtos.ExpedienteRequest;
import com.gdi.citas.dto.ExpedienteDtos.ExpedienteResponse;
import com.gdi.citas.exception.RecursoNoEncontradoException;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.DienteOdontograma;
import com.gdi.citas.model.EstadoDiente;
import com.gdi.citas.model.Expediente;
import com.gdi.citas.model.Paciente;
import com.gdi.citas.repository.DienteOdontogramaRepository;
import com.gdi.citas.repository.ExpedienteRepository;
import com.gdi.citas.repository.PacienteRepository;

/** Expediente clínico y odontograma del paciente. */
@Service
public class ExpedienteService {

    /** Caras del diente: oclusal/incisal, mesial, distal, vestibular y lingual/palatino. */
    private static final List<String> SUPERFICIES = List.of("O", "M", "D", "V", "L");

    private final PacienteRepository pacienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final DienteOdontogramaRepository dienteRepository;

    public ExpedienteService(PacienteRepository pacienteRepository, ExpedienteRepository expedienteRepository,
                             DienteOdontogramaRepository dienteRepository) {
        this.pacienteRepository = pacienteRepository;
        this.expedienteRepository = expedienteRepository;
        this.dienteRepository = dienteRepository;
    }

    @Transactional(readOnly = true)
    public ExpedienteResponse obtener(Integer pacienteId) {
        Paciente paciente = buscarPaciente(pacienteId);
        Expediente expediente = expedienteRepository.findFirstByPacienteIdOrderByIdAsc(pacienteId).orElse(null);
        List<DienteResponse> dientes = expediente == null ? List.of()
                : dienteRepository.findByExpedienteIdOrderByNumeroDienteAsc(expediente.getId()).stream()
                        .map(DienteResponse::de).toList();
        return ExpedienteResponse.de(pacienteId, paciente.getNombreCompleto(), expediente, dientes);
    }

    @Transactional
    public ExpedienteResponse guardar(Integer pacienteId, ExpedienteRequest req) {
        Expediente e = expedienteDe(buscarPaciente(pacienteId));
        e.setAntecedentesPersonales(limpiar(req.antecedentesPersonales()));
        e.setAntecedentesFamiliares(limpiar(req.antecedentesFamiliares()));
        e.setAlergias(limpiar(req.alergias()));
        return obtener(pacienteId);
    }

    /**
     * Registra el estado de una pieza. Si queda sana y sin notas se borra el registro, porque el odontograma
     * solo guarda hallazgos.
     */
    @Transactional
    public DienteResponse guardarDiente(Integer pacienteId, int numero, DienteRequest req) {
        validarNumero(numero);
        Expediente expediente = expedienteDe(buscarPaciente(pacienteId));
        DienteOdontograma diente = dienteRepository.findByExpedienteIdAndNumeroDiente(expediente.getId(), numero)
                .orElseGet(() -> {
                    DienteOdontograma nuevo = new DienteOdontograma();
                    nuevo.setExpediente(expediente);
                    nuevo.setNumeroDiente(numero);
                    return nuevo;
                });
        String descripcion = limpiar(req.descripcion());
        if (req.estado() == EstadoDiente.Sano && descripcion == null) {
            if (diente.getId() != null) dienteRepository.delete(diente);
            return new DienteResponse(numero, EstadoDiente.Sano, List.of(), null);
        }
        diente.setEstado(req.estado());
        diente.setSuperficies(superficies(req));
        diente.setDescripcion(descripcion);
        return DienteResponse.de(dienteRepository.save(diente));
    }

    /** Solo caries, restauraciones y fracturas se marcan por cara; se guardan en orden y sin repetir. */
    private static String superficies(DienteRequest req) {
        if (!req.estado().esPorSuperficie() || req.superficies() == null) return null;
        Set<String> elegidas = Set.copyOf(req.superficies());
        elegidas.forEach(s -> {
            if (!SUPERFICIES.contains(s)) throw new ReglaNegocioException("Superficie no válida: " + s);
        });
        String caras = String.join(",", SUPERFICIES.stream().filter(elegidas::contains).toList());
        return caras.isEmpty() ? null : caras;
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
