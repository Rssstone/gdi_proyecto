package com.gdi.citas.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CitaRequest(
        @NotNull(message = "Selecciona un paciente") Integer pacienteId,
        @NotNull(message = "Selecciona un odontólogo") Integer odontologoId,
        @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
        @NotNull(message = "La hora es obligatoria") @JsonFormat(pattern = "HH:mm") LocalTime hora,
        @NotNull(message = "El motivo es obligatorio")
        @Size(min = 3, max = 255, message = "El motivo debe tener entre 3 y 255 caracteres") String motivo,
        @Size(max = 1000, message = "Las notas no pueden exceder 1000 caracteres") String notasRecepcion) {
}
