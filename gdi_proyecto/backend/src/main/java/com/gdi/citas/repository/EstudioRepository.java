package com.gdi.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gdi.citas.dto.EstudioDtos.EstudioResponse;
import com.gdi.citas.model.Estudio;

public interface EstudioRepository extends JpaRepository<Estudio, Integer> {

    /** Solo los datos del estudio, sin cargar el archivo (puede pesar varios MB). */
    @Query("""
            select new com.gdi.citas.dto.EstudioDtos$EstudioResponse(e.id, e.tipo, e.descripcion, e.nombreArchivo,
                   e.tipoMime, e.tamano, e.fecha)
            from Estudio e where e.paciente.id = :pacienteId order by e.fecha desc
            """)
    List<EstudioResponse> listarPorPaciente(@Param("pacienteId") Integer pacienteId);
}
