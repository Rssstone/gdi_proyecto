package com.gdi.citas.exception;

import org.springframework.http.HttpStatus;

/** Operación válida en forma pero no permitida por las reglas de la clínica. */
public class ReglaNegocioException extends RuntimeException {

    private final HttpStatus status;

    public ReglaNegocioException(String mensaje) {
        this(HttpStatus.UNPROCESSABLE_CONTENT, mensaje);
    }

    public ReglaNegocioException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public static ReglaNegocioException conflicto(String mensaje) {
        return new ReglaNegocioException(HttpStatus.CONFLICT, mensaje);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
