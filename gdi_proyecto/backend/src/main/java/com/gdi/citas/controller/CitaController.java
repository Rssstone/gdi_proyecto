package com.gdi.citas.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdi.citas.dto.CambioEstadoRequest;
import com.gdi.citas.dto.CitaRequest;
import com.gdi.citas.dto.CitaResponse;
import com.gdi.citas.model.EstadoCita;
import com.gdi.citas.service.CitaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping
    public List<CitaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) Integer odontologoId,
            @RequestParam(required = false) Integer pacienteId,
            @RequestParam(required = false) String q) {
        return citaService.listar(desde, hasta, estado, odontologoId, pacienteId, q);
    }

    @GetMapping("/{id}")
    public CitaResponse obtener(@PathVariable Integer id) {
        return citaService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<CitaResponse> crear(@Valid @RequestBody CitaRequest req) {
        CitaResponse creada = citaService.crear(req);
        return ResponseEntity.created(URI.create("/api/citas/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    public CitaResponse actualizar(@PathVariable Integer id, @Valid @RequestBody CitaRequest req) {
        return citaService.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    public CitaResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody CambioEstadoRequest req) {
        return citaService.cambiarEstado(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        citaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
