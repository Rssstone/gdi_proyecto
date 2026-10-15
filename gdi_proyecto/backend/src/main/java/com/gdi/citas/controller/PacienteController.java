package com.gdi.citas.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdi.citas.dto.PacienteDtos.PacienteRequest;
import com.gdi.citas.dto.PacienteDtos.PacienteResponse;
import com.gdi.citas.service.PacienteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @GetMapping
    public List<PacienteResponse> buscar(@RequestParam(required = false) String q) {
        return pacienteService.buscar(q);
    }

    @GetMapping("/{id}")
    public PacienteResponse obtener(@PathVariable Integer id) {
        return pacienteService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest req) {
        PacienteResponse creado = pacienteService.crear(req);
        return ResponseEntity.created(URI.create("/api/pacientes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public PacienteResponse actualizar(@PathVariable Integer id, @Valid @RequestBody PacienteRequest req) {
        return pacienteService.actualizar(id, req);
    }
}
