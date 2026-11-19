package com.gdi.citas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gdi.citas.model.DienteOdontograma;

public interface DienteOdontogramaRepository extends JpaRepository<DienteOdontograma, Integer> {

    List<DienteOdontograma> findByExpedienteIdOrderByNumeroDienteAsc(Integer expedienteId);

    Optional<DienteOdontograma> findByExpedienteIdAndNumeroDiente(Integer expedienteId, Integer numeroDiente);
}
