package com.gdi.citas.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.gdi.citas.dto.EstudioDtos.EstudioResponse;
import com.gdi.citas.exception.RecursoNoEncontradoException;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.Estudio;
import com.gdi.citas.repository.EstudioRepository;
import com.gdi.citas.repository.PacienteRepository;

/** Radiografías, tomografías y fotografías del expediente. */
@Service
public class EstudioService {

    public static final long TAMANO_MAXIMO = 10L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("Radiografía", "Tomografía", "Fotografía", "Otro");
    private static final Set<String> FORMATOS = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final EstudioRepository estudioRepository;
    private final PacienteRepository pacienteRepository;
    private final Clock clock;

    public EstudioService(EstudioRepository estudioRepository, PacienteRepository pacienteRepository, Clock clock) {
        this.estudioRepository = estudioRepository;
        this.pacienteRepository = pacienteRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EstudioResponse> listar(Integer pacienteId) {
        return estudioRepository.listarPorPaciente(pacienteId);
    }

    @Transactional
    public EstudioResponse subir(Integer pacienteId, MultipartFile archivo, String tipo, String descripcion) {
        if (archivo == null || archivo.isEmpty()) throw new ReglaNegocioException("Selecciona un archivo.");
        if (!TIPOS.contains(tipo)) throw new ReglaNegocioException("Tipo de estudio no válido.");
        if (archivo.getSize() > TAMANO_MAXIMO) throw new ReglaNegocioException("El archivo pesa más de 10 MB.");
        if (!FORMATOS.contains(archivo.getContentType())) {
            throw new ReglaNegocioException("Formato no permitido: sube una imagen JPG, PNG o WEBP, o un PDF.");
        }
        Estudio e = new Estudio();
        e.setPaciente(pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + pacienteId)));
        e.setTipo(tipo);
        e.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
        e.setNombreArchivo(archivo.getOriginalFilename() == null ? "archivo" : archivo.getOriginalFilename());
        e.setTipoMime(archivo.getContentType());
        e.setTamano(archivo.getSize());
        try {
            e.setArchivo(archivo.getBytes());
        } catch (IOException ex) {
            throw new ReglaNegocioException("No se pudo leer el archivo.");
        }
        e.setFecha(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
        estudioRepository.save(e);
        return new EstudioResponse(e.getId(), e.getTipo(), e.getDescripcion(), e.getNombreArchivo(), e.getTipoMime(),
                e.getTamano(), e.getFecha());
    }

    @Transactional(readOnly = true)
    public Estudio obtener(Integer id) {
        Estudio e = estudioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudio no encontrado: " + id));
        e.getArchivo(); // se carga dentro de la transacción
        return e;
    }

    @Transactional
    public void eliminar(Integer id) {
        estudioRepository.delete(estudioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudio no encontrado: " + id)));
    }
}
