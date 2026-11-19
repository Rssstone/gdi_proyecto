package com.gdi.citas.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Detalle en español de lo que se modificó durante una petición (cita agendada, diente cambiado, motivo de un
 * rechazo…). Los services anotan aquí y {@link RegistroPeticiones} lo escribe debajo de la línea de la petición,
 * para que en el registro se lea en un solo bloque: qué ruta se llamó, qué cambió y cómo terminó.
 */
public final class Bitacora {

    static final String ATRIBUTO = "gdi.bitacora";
    private static final Logger log = LoggerFactory.getLogger("gdi.api");

    private Bitacora() {
    }

    /** Anota un detalle con el formato de SLF4J: {@code anotar("Cita #{} agendada", id)}. */
    public static void anotar(String formato, Object... argumentos) {
        String texto = MessageFormatter.arrayFormat(formato, argumentos).getMessage();
        RequestAttributes peticion = RequestContextHolder.getRequestAttributes();
        if (peticion == null) {
            log.info("  -> {}", texto);   // fuera de una petición HTTP (pruebas, tareas internas)
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> lista = (List<String>) peticion.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
        if (lista == null) {
            lista = new ArrayList<>();
            peticion.setAttribute(ATRIBUTO, lista, RequestAttributes.SCOPE_REQUEST);
        }
        lista.add(texto);
    }
}
