package com.gdi.citas.dto;

import com.gdi.citas.model.Rol;
import com.gdi.citas.model.Usuario;

import jakarta.validation.constraints.NotBlank;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    public record UsuarioResponse(Integer id, String nombreCompleto, String email, Rol rol) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNombreCompleto(), u.getEmail(), u.getRol());
        }
    }
}
