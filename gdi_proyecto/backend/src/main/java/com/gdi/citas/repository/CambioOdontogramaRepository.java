package com.gdi.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gdi.citas.model.CambioOdontograma;

public interface CambioOdontogramaRepository extends JpaRepository<CambioOdontograma, Integer> {

    @EntityGraph(attributePaths = "usuario")
    List<CambioOdontograma> findByExpedienteIdOrderByFechaAscIdAsc(Integer expedienteId);
}
