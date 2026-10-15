package com.gdi.citas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gdi.citas.model.Rol;
import com.gdi.citas.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findByRolOrderByNombreCompletoAsc(Rol rol);
}
