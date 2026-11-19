package com.gdi.citas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gdi.citas.model.Expediente;

public interface ExpedienteRepository extends JpaRepository<Expediente, Integer> {

    Optional<Expediente> findFirstByPacienteIdOrderByIdAsc(Integer pacienteId);
}
