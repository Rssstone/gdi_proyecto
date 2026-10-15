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

    /** Estados que ocupan un espacio en la agenda. */
    public static final Set<EstadoCita> ACTIVOS = EnumSet.of(Programada, Confirmada);

    public boolean esActivo() {
        return ACTIVOS.contains(this);
    }
}
