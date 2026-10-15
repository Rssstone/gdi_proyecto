package com.gdi.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gdi.citas.model.Paciente;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    List<Paciente> findTop20ByNombreCompletoContainingIgnoreCaseOrTelefonoContainingOrderByNombreCompletoAsc(
            String nombre, String telefono);

    List<Paciente> findAllByOrderByNombreCompletoAsc();
}
