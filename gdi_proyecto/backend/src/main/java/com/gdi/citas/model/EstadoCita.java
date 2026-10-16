package com.gdi.citas.model;

import java.util.EnumSet;
import java.util.Set;

/** Los nombres coinciden con el ENUM de la columna citas.estado. */
public enum EstadoCita {
    Programada,
    Confirmada,
    Cancelada,
    Completada,
    Inasistencia;

    /** Citas pendientes de atender: se pueden editar, confirmar o cancelar. */
    public static final Set<EstadoCita> ACTIVOS = EnumSet.of(Programada, Confirmada);

    /**
     * Estados que bloquean su horario en la agenda. Una cita cancelada o con inasistencia libera
     * su tiempo (por ejemplo, si el paciente no llegó, el resto de su hora se puede usar para otro).
     */
    public static final Set<EstadoCita> OCUPAN_HORARIO = EnumSet.of(Programada, Confirmada, Completada);

    public boolean esActivo() {
        return ACTIVOS.contains(this);
    }
}
