package com.gdi.citas.controller;

import java.net.URI;
import java.util.List;

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

import com.gdi.citas.dto.FichaTecnicaDtos.FichaTecnicaRequest;
import com.gdi.citas.dto.FichaTecnicaDtos.FichaTecnicaResponse;
import com.gdi.citas.service.FichaTecnicaService;

import jakarta.validation.Valid;

/** Módulo técnico: fichas de coronas, puentes y prótesis. */
@RestController
@RequestMapping("/api/fichas-tecnicas")
public class FichaTecnicaController {

    private final FichaTecnicaService fichaService;

    public FichaTecnicaController(FichaTecnicaService fichaService) {
        this.fichaService = fichaService;
    }

    @GetMapping
    public List<FichaTecnicaResponse> listar(@RequestParam(required = false) Integer pacienteId) {
        return fichaService.listar(pacienteId);
    }

    @GetMapping("/{id}")
    public FichaTecnicaResponse obtener(@PathVariable Integer id) {
        return fichaService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<FichaTecnicaResponse> crear(@Valid @RequestBody FichaTecnicaRequest req) {
        FichaTecnicaResponse creada = fichaService.crear(req);
        return ResponseEntity.created(URI.create("/api/fichas-tecnicas/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    public FichaTecnicaResponse actualizar(@PathVariable Integer id, @Valid @RequestBody FichaTecnicaRequest req) {
        return fichaService.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        fichaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
