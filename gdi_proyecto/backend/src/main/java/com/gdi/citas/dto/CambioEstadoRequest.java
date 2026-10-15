package com.gdi.citas.dto;

import com.gdi.citas.model.EstadoCita;

import jakarta.validation.constraints.NotNull;

public record CambioEstadoRequest(
        @NotNull(message = "Indica el nuevo estado") EstadoCita estado,
        String nota) {
}
