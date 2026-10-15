package com.gdi.citas.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gdi.citas.model.Cita;
import com.gdi.citas.model.EstadoCita;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    @EntityGraph(attributePaths = {"paciente", "odontologo"})
    List<Cita> findByFechaBetweenOrderByFechaAscHoraAsc(LocalDate desde, LocalDate hasta);

    @EntityGraph(attributePaths = {"paciente", "odontologo"})
    List<Cita> findAllByOrderByFechaDescHoraDesc();

    @EntityGraph(attributePaths = {"paciente", "odontologo"})
    List<Cita> findByFechaBetweenAndEstadoInOrderByFechaAscHoraAsc(
            LocalDate desde, LocalDate hasta, Collection<EstadoCita> estados);

    /** Cuenta las citas activas que chocan con un horario, ya sea por el odontólogo o por el paciente. */
    @Query("""
            select count(c) from Cita c left join c.odontologo o
            where c.fecha = :fecha and c.hora = :hora
              and c.estado in :estados
              and (:excluirId is null or c.id <> :excluirId)
              and (o.id = :odontologoId or c.paciente.id = :pacienteId)
            """)
    long contarConflictos(@Param("fecha") LocalDate fecha,
                          @Param("hora") LocalTime hora,
                          @Param("odontologoId") Integer odontologoId,
                          @Param("pacienteId") Integer pacienteId,
                          @Param("excluirId") Integer excluirId,
                          @Param("estados") Collection<EstadoCita> estados);
}
