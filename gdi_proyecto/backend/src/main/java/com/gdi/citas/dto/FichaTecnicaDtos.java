package com.gdi.citas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.gdi.citas.model.FichaTecnica;
import com.gdi.citas.model.TipoTrabajo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class FichaTecnicaDtos {

    private FichaTecnicaDtos() {
    }

    public record FichaTecnicaRequest(
            @NotNull(message = "Selecciona un paciente") Integer pacienteId,
            @NotNull(message = "Selecciona un odontólogo") Integer odontologoId,
            @NotNull(message = "Elige el tipo de trabajo") TipoTrabajo tipoTrabajo,
            @NotBlank(message = "Indica las piezas dentales")
            @Pattern(regexp = "^\\s*[1-8][1-8](\\s*[,-]\\s*[1-8][1-8])*\\s*$",
                    message = "Usa la numeración FDI, por ejemplo 16 o 14, 15, 16") String piezas,
            @NotBlank(message = "Elige el material") @Size(max = 60) String material,
            @Pattern(regexp = "^$|^(A[1-4]|A3\\.5|B[1-4]|C[1-4]|D[2-4]|BL[1-4])$",
                    message = "Tono VITA no válido (A1 a D4)") String colorVita,
            @DecimalMin(value = "0.1", message = "Mínimo 0.1 mm") @DecimalMax(value = "40", message = "Máximo 40 mm") BigDecimal anchoMm,
            @DecimalMin(value = "0.1", message = "Mínimo 0.1 mm") @DecimalMax(value = "40", message = "Máximo 40 mm") BigDecimal altoMm,
            @DecimalMin(value = "0.1", message = "Mínimo 0.1 mm") @DecimalMax(value = "10", message = "Máximo 10 mm") BigDecimal grosorMm,
            @Size(max = 40) String terminacion,
            @Size(max = 120, message = "Máximo 120 caracteres") String laboratorio,
            @NotNull(message = "Indica la fecha de toma de medidas") LocalDate fechaToma,
            LocalDate fechaEntrega,
            @Size(max = 1000, message = "Máximo 1000 caracteres") String notas) {
    }

    public record FichaTecnicaResponse(Integer id, Integer pacienteId, String pacienteNombre, Integer odontologoId,
                                       String odontologoNombre, TipoTrabajo tipoTrabajo, String piezas, String material,
                                       String colorVita, BigDecimal anchoMm, BigDecimal altoMm, BigDecimal grosorMm,
                                       String terminacion, String laboratorio, LocalDate fechaToma,
                                       LocalDate fechaEntrega, String notas) {
        public static FichaTecnicaResponse de(FichaTecnica f) {
            var o = f.getOdontologo();
            return new FichaTecnicaResponse(f.getId(), f.getPaciente().getId(), f.getPaciente().getNombreCompleto(),
                    o == null ? null : o.getId(), o == null ? null : o.getNombreCompleto(), f.getTipoTrabajo(),
                    f.getPiezas(), f.getMaterial(), f.getColorVita(), f.getAnchoMm(), f.getAltoMm(), f.getGrosorMm(),
                    f.getTerminacion(), f.getLaboratorio(), f.getFechaToma(), f.getFechaEntrega(), f.getNotas());
        }
    }
}
