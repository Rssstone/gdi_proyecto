package com.gdi.citas.dto;

import java.util.Arrays;
import java.util.List;

import com.gdi.citas.model.DienteOdontograma;
import com.gdi.citas.model.EstadoDiente;
import com.gdi.citas.model.Expediente;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ExpedienteDtos {

    private ExpedienteDtos() {
    }

    public record ExpedienteRequest(
            @Size(max = 2000, message = "Máximo 2000 caracteres") String antecedentesPersonales,
            @Size(max = 2000, message = "Máximo 2000 caracteres") String antecedentesFamiliares,
            @Size(max = 1000, message = "Máximo 1000 caracteres") String alergias) {
    }

    public record DienteRequest(
            @NotNull(message = "Elige el estado del diente") EstadoDiente estado,
            List<String> superficies,
            @Size(max = 500, message = "Máximo 500 caracteres") String descripcion) {
    }

    public record DienteResponse(int numero, EstadoDiente estado, List<String> superficies, String descripcion) {
        public static DienteResponse de(DienteOdontograma d) {
            List<String> caras = d.getSuperficies() == null || d.getSuperficies().isBlank()
                    ? List.of() : Arrays.asList(d.getSuperficies().split(","));
            return new DienteResponse(d.getNumeroDiente(), d.getEstado(), caras, d.getDescripcion());
        }
    }

    public record ExpedienteResponse(Integer pacienteId, String pacienteNombre, String antecedentesPersonales,
                                     String antecedentesFamiliares, String alergias, List<DienteResponse> dientes) {
        public static ExpedienteResponse de(Integer pacienteId, String pacienteNombre, Expediente e,
                                            List<DienteResponse> dientes) {
            return e == null
                    ? new ExpedienteResponse(pacienteId, pacienteNombre, null, null, null, dientes)
                    : new ExpedienteResponse(pacienteId, pacienteNombre, e.getAntecedentesPersonales(),
                            e.getAntecedentesFamiliares(), e.getAlergias(), dientes);
        }
    }
}
