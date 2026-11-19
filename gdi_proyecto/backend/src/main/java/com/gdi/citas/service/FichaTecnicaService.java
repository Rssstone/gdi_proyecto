package com.gdi.citas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.config.Bitacora;
import com.gdi.citas.dto.FichaTecnicaDtos.FichaTecnicaRequest;
import com.gdi.citas.dto.FichaTecnicaDtos.FichaTecnicaResponse;
import com.gdi.citas.exception.RecursoNoEncontradoException;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.FichaTecnica;
import com.gdi.citas.model.Rol;
import com.gdi.citas.repository.FichaTecnicaRepository;
import com.gdi.citas.repository.PacienteRepository;
import com.gdi.citas.repository.UsuarioRepository;

/** Módulo técnico: especificaciones de coronas, puentes y prótesis. */
@Service
public class FichaTecnicaService {

    private final FichaTecnicaRepository fichaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public FichaTecnicaService(FichaTecnicaRepository fichaRepository, PacienteRepository pacienteRepository,
                               UsuarioRepository usuarioRepository, Clock clock) {
        this.fichaRepository = fichaRepository;
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FichaTecnicaResponse> listar(Integer pacienteId) {
        List<FichaTecnica> fichas = pacienteId == null
                ? fichaRepository.findAllByOrderByFechaTomaDescIdDesc()
                : fichaRepository.findByPacienteIdOrderByFechaTomaDescIdDesc(pacienteId);
        return fichas.stream().map(FichaTecnicaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public FichaTecnicaResponse obtener(Integer id) {
        return FichaTecnicaResponse.de(buscar(id));
    }

    @Transactional
    public FichaTecnicaResponse crear(FichaTecnicaRequest req) {
        FichaTecnica f = new FichaTecnica();
        aplicar(f, req);
        fichaRepository.save(f);
        Bitacora.anotar("Ficha técnica #{} creada: {} · {} · piezas {} · entrega {}", f.getId(),
                f.getPaciente().getNombreCompleto(), f.getTipoTrabajo(), f.getPiezas(), f.getFechaEntrega());
        return FichaTecnicaResponse.de(f);
    }

    @Transactional
    public FichaTecnicaResponse actualizar(Integer id, FichaTecnicaRequest req) {
        FichaTecnica f = buscar(id);
        aplicar(f, req);
        Bitacora.anotar("Ficha técnica #{} actualizada: {} · {} · entrega {}", id, f.getPaciente().getNombreCompleto(),
                f.getTipoTrabajo(), f.getFechaEntrega());
        return FichaTecnicaResponse.de(f);
    }

    @Transactional
    public void eliminar(Integer id) {
        FichaTecnica f = buscar(id);
        fichaRepository.delete(f);
        Bitacora.anotar("Ficha técnica #{} de {} eliminada", id, f.getPaciente().getNombreCompleto());
    }

    private void aplicar(FichaTecnica f, FichaTecnicaRequest req) {
        if (req.fechaToma().isAfter(LocalDate.now(clock))) {
            throw new ReglaNegocioException("La fecha de toma de medidas no puede ser futura.");
        }
        if (req.fechaEntrega() != null && req.fechaEntrega().isBefore(req.fechaToma())) {
            throw new ReglaNegocioException("La fecha de entrega no puede ser anterior a la toma de medidas.");
        }
        f.setPaciente(pacienteRepository.findById(req.pacienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + req.pacienteId())));
        f.setOdontologo(usuarioRepository.findById(req.odontologoId())
                .filter(u -> u.getRol() == Rol.Odontologo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Odontólogo no encontrado: " + req.odontologoId())));
        f.setTipoTrabajo(req.tipoTrabajo());
        f.setPiezas(req.piezas().replaceAll("\\s+", "").replace(",", ", "));
        f.setMaterial(req.material().trim());
        f.setColorVita(vacioANull(req.colorVita()));
        f.setAnchoMm(req.anchoMm());
        f.setAltoMm(req.altoMm());
        f.setGrosorMm(req.grosorMm());
        f.setTerminacion(vacioANull(req.terminacion()));
        f.setLaboratorio(vacioANull(req.laboratorio()));
        f.setFechaToma(req.fechaToma());
        f.setFechaEntrega(req.fechaEntrega());
        f.setNotas(vacioANull(req.notas()));
    }

    private FichaTecnica buscar(Integer id) {
        return fichaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ficha técnica no encontrada: " + id));
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
