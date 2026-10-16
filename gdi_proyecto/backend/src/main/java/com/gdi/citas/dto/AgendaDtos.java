package com.gdi.citas.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gdi.citas.model.EstadoCita;

/** Respuestas del módulo de agenda (disponibilidad). */
public final class AgendaDtos {

    private AgendaDtos() {
    }

    /**
     * DISPONIBLE: libre. OCUPADO: lo cubre una cita. PASADO: la hora ya pasó.
     * INSUFICIENTE: está libre, pero no alcanza para la duración pedida (choca con otra cita o con el cierre).
     */
    public enum EstadoEspacio { DISPONIBLE, OCUPADO, PASADO, INSUFICIENTE }

    public enum EstadoDia { DISPONIBLE, LLENO, PASADO, CERRADO }

    public record Espacio(
            @JsonFormat(pattern = "HH:mm") LocalTime hora,
            EstadoEspacio estado,
            Integer citaId,
            String pacienteNombre,
            EstadoCita estadoCita,
            @JsonFormat(pattern = "HH:mm") LocalTime citaInicio,
            @JsonFormat(pattern = "HH:mm") LocalTime citaFin,
            boolean continuacion) {
    }

    public record Disponibilidad(
            LocalDate fecha,
            boolean abierto,
            String mensaje,
            int disponibles,
            List<Espacio> espacios) {
    }

    public record ResumenDia(
            LocalDate fecha,
            EstadoDia estado,
            int citas,
            int disponibles,
            int totalEspacios) {
    }

    public record HorarioDia(
            @JsonFormat(pattern = "HH:mm") LocalTime apertura,
            @JsonFormat(pattern = "HH:mm") LocalTime cierre) {
    }

    public record Configuracion(
            LocalDate hoy,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime ahora,
            String zonaHoraria,
            int intervaloMinutos,
            Map<String, HorarioDia> horarios) {
    }
}
