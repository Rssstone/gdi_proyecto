package com.gdi.citas.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public final class EstudioDtos {

    private EstudioDtos() {
    }

    public record EstudioResponse(Integer id, String tipo, String descripcion, String nombreArchivo, String tipoMime,
                                  Long tamano, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fecha) {
    }
}
