package com.gdi.citas.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gdi.citas.model.CambioOdontograma;
import com.gdi.citas.model.EstadoDiente;
import com.gdi.citas.model.EstadoPieza;
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

    /** estado: hallazgo de la pieza completa; caras: hallazgo por cara, por ejemplo {"O": "Caries"}. */
    public record DienteRequest(
            @NotNull(message = "Indica el estado de la pieza") EstadoDiente estado,
            Map<String, EstadoDiente> caras,
            @Size(max = 500, message = "Máximo 500 caracteres") String descripcion,
            Integer usuarioId) {
    }

    public record DienteResponse(int numero, EstadoDiente estado, Map<String, EstadoDiente> caras, String descripcion) {
        public static DienteResponse de(int numero, EstadoPieza p, String descripcion) {
            return new DienteResponse(numero, p.estado(), p.caras(), descripcion);
        }
    }

    public record CambioResponse(Integer id, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fecha,
                                 int numero, String antes, String despues, String descripcion, String usuarioNombre) {
        public static CambioResponse de(CambioOdontograma c) {
            return new CambioResponse(c.getId(), c.getFecha(), c.getNumeroDiente(), c.getAnterior().texto(),
                    c.getNuevo().texto(), c.getDescripcion(), c.getUsuario() == null ? null : c.getUsuario().getNombreCompleto());
        }
    }

    /** dientes: el odontograma actual o, si se pidió una fecha, cómo estaba al final de ese día. */
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
