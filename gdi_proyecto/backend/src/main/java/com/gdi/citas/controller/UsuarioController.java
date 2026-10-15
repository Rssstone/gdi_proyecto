package com.gdi.citas.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdi.citas.dto.UsuarioDtos.LoginRequest;
import com.gdi.citas.dto.UsuarioDtos.UsuarioResponse;
import com.gdi.citas.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/auth/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest req) {
        return usuarioService.login(req);
    }

    @GetMapping("/usuarios/odontologos")
    public List<UsuarioResponse> odontologos() {
        return usuarioService.odontologos();
    }
}
