package com.gdi.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gdi.citas.model.FichaTecnica;

public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Integer> {

    @EntityGraph(attributePaths = {"paciente", "odontologo"})
    List<FichaTecnica> findByPacienteIdOrderByFechaTomaDescIdDesc(Integer pacienteId);

    @EntityGraph(attributePaths = {"paciente", "odontologo"})
    List<FichaTecnica> findAllByOrderByFechaTomaDescIdDesc();
}
