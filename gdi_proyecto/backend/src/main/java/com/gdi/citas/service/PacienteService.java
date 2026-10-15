package com.gdi.citas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.dto.PacienteDtos.PacienteRequest;
import com.gdi.citas.dto.PacienteDtos.PacienteResponse;
import com.gdi.citas.exception.RecursoNoEncontradoException;
import com.gdi.citas.model.Paciente;
import com.gdi.citas.repository.PacienteRepository;

@Service
public class PacienteService {

    private final PacienteRepository pacienteRepository;

    public PacienteService(PacienteRepository pacienteRepository) {
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional(readOnly = true)
    public List<PacienteResponse> buscar(String q) {
        List<Paciente> pacientes = (q == null || q.isBlank())
                ? pacienteRepository.findAllByOrderByNombreCompletoAsc()
                : pacienteRepository.findTop20ByNombreCompletoContainingIgnoreCaseOrTelefonoContainingOrderByNombreCompletoAsc(
                        q.trim(), q.trim());
        return pacientes.stream().map(PacienteResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtener(Integer id) {
        return PacienteResponse.de(buscarEntidad(id));
    }

    @Transactional
    public PacienteResponse crear(PacienteRequest req) {
        Paciente p = new Paciente();
        aplicar(p, req);
        return PacienteResponse.de(pacienteRepository.save(p));
    }

    @Transactional
    public PacienteResponse actualizar(Integer id, PacienteRequest req) {
        Paciente p = buscarEntidad(id);
        aplicar(p, req);
        return PacienteResponse.de(p);
    }

    private Paciente buscarEntidad(Integer id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + id));
    }

    private static void aplicar(Paciente p, PacienteRequest req) {
        p.setNombreCompleto(req.nombreCompleto().trim());
        p.setTelefono(req.telefono().trim());
        p.setFechaNacimiento(req.fechaNacimiento());
        p.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
    }
}
