package com.gdi.citas.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gdi.citas.dto.EstudioDtos.EstudioResponse;
import com.gdi.citas.dto.ExpedienteDtos.CambioResponse;
import com.gdi.citas.dto.ExpedienteDtos.DienteRequest;
import com.gdi.citas.dto.ExpedienteDtos.DienteResponse;
import com.gdi.citas.dto.ExpedienteDtos.ExpedienteRequest;
import com.gdi.citas.dto.ExpedienteDtos.ExpedienteResponse;
import com.gdi.citas.model.Estudio;
import com.gdi.citas.service.EstudioService;
import com.gdi.citas.service.ExpedienteService;

import jakarta.validation.Valid;

/** Expediente clínico: antecedentes, odontograma y estudios (radiografías) del paciente. */
@RestController
@RequestMapping("/api")
public class ExpedienteController {

    private final ExpedienteService expedienteService;
    private final EstudioService estudioService;

    public ExpedienteController(ExpedienteService expedienteService, EstudioService estudioService) {
        this.expedienteService = expedienteService;
        this.estudioService = estudioService;
    }

    /** Con ?fecha=AAAA-MM-DD devuelve el odontograma como estaba al final de ese día. */
    @GetMapping("/pacientes/{pacienteId}/expediente")
    public ExpedienteResponse obtener(@PathVariable Integer pacienteId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return expedienteService.obtener(pacienteId, fecha);
    }

    @GetMapping("/pacientes/{pacienteId}/odontograma/historial")
    public List<CambioResponse> historial(@PathVariable Integer pacienteId) {
        return expedienteService.historial(pacienteId);
    }

    @PutMapping("/pacientes/{pacienteId}/expediente")
    public ExpedienteResponse guardar(@PathVariable Integer pacienteId, @Valid @RequestBody ExpedienteRequest req) {
        return expedienteService.guardar(pacienteId, req);
    }

    @PutMapping("/pacientes/{pacienteId}/odontograma/{numero}")
    public DienteResponse guardarDiente(@PathVariable Integer pacienteId, @PathVariable int numero,
                                        @Valid @RequestBody DienteRequest req) {
        return expedienteService.guardarDiente(pacienteId, numero, req);
    }

    @GetMapping("/pacientes/{pacienteId}/estudios")
    public List<EstudioResponse> estudios(@PathVariable Integer pacienteId) {
        return estudioService.listar(pacienteId);
    }

    @PostMapping(value = "/pacientes/{pacienteId}/estudios", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EstudioResponse> subir(@PathVariable Integer pacienteId,
                                                 @RequestParam MultipartFile archivo,
                                                 @RequestParam String tipo,
                                                 @RequestParam(required = false) String descripcion) {
        return ResponseEntity.status(201).body(estudioService.subir(pacienteId, archivo, tipo, descripcion));
    }

    /** Devuelve el archivo para verlo en el navegador (inline). */
    @GetMapping("/estudios/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable Integer id) {
        Estudio e = estudioService.obtener(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(e.getNombreArchivo(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .body(e.getArchivo());
    }

    @DeleteMapping("/estudios/{id}")
    public ResponseEntity<Void> eliminarEstudio(@PathVariable Integer id) {
        estudioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
