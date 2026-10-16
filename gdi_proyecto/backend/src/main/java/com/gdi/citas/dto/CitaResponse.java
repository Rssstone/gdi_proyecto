package com.gdi.citas.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gdi.citas.model.Cita;
import com.gdi.citas.model.EstadoCita;

public record CitaResponse(
        Integer id,
        LocalDate fecha,
        @JsonFormat(pattern = "HH:mm") LocalTime hora,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
        int duracionMinutos,
        String motivo,
        EstadoCita estado,
        String notasRecepcion,
        Integer pacienteId,
        String pacienteNombre,
        String pacienteTelefono,
        Integer odontologoId,
        String odontologoNombre,
        boolean pasada) {

    public static CitaResponse de(Cita c, boolean pasada) {
        var p = c.getPaciente();
        var o = c.getOdontologo();
        return new CitaResponse(c.getId(), c.getFecha(), c.getHora(), c.getHoraFin(), c.getDuracionMinutos(), c.getMotivo(), c.getEstado(),
                c.getNotasRecepcion(),
                p.getId(), p.getNombreCompleto(), p.getTelefono(),
                o == null ? null : o.getId(), o == null ? null : o.getNombreCompleto(),
                pasada);
    }
}
