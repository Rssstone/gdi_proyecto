package com.gdi.citas.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.dto.UsuarioDtos.LoginRequest;
import com.gdi.citas.dto.UsuarioDtos.UsuarioResponse;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.Rol;
import com.gdi.citas.model.Usuario;
import com.gdi.citas.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> coincide(u, req.password()))
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        return UsuarioResponse.de(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> odontologos() {
        return usuarioRepository.findByRolOrderByNombreCompletoAsc(Rol.Odontologo).stream()
                .map(UsuarioResponse::de).toList();
    }

    /** Acepta contraseñas BCrypt; las heredadas en texto plano se cifran en el primer inicio de sesión correcto. */
    private boolean coincide(Usuario u, String password) {
        String guardada = u.getPassword();
        if (guardada.startsWith("$2")) {
            return passwordEncoder.matches(password, guardada);
        }
        if (guardada.equals(password)) {
            u.setPassword(passwordEncoder.encode(password));
            return true;
        }
        return false;
    }
}
