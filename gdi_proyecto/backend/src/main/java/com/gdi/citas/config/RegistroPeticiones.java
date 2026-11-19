package com.gdi.citas.config;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe en el registro cada petición a la API de forma legible, por ejemplo:
 * <pre>
 * CAMBIO    | Agendar cita | POST /api/citas | 201 OK | 14 ms | desde 187.190.186.64
 *             -> Cita #12 agendada: Ana López · 2026-11-19 10:00 (30 min) · Limpieza · Dra. Ruiz
 * </pre>
 * CONSULTA = solo lee datos (GET) · CAMBIO = guarda, modifica o borra · SESION = inicio de sesión ·
 * RECHAZO = la API respondió con error (debajo va el motivo).
 */
@Component
public class RegistroPeticiones extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("gdi.api");

    /** Método + ruta (con {id} como comodín) → descripción en español. */
    private static final List<String[]> ACCIONES = List.of(
            new String[] {"POST", "/api/auth/login", "Inicio de sesión"},
            new String[] {"GET", "/api/usuarios/odontologos", "Lista de odontólogos"},
            new String[] {"GET", "/api/agenda/configuracion", "Horario de la clínica"},
            new String[] {"GET", "/api/agenda/disponibilidad", "Horarios disponibles del día"},
            new String[] {"GET", "/api/agenda/resumen", "Resumen del mes (calendario)"},
            new String[] {"GET", "/api/citas", "Lista de citas"},
            new String[] {"GET", "/api/citas/{}", "Detalle de una cita"},
            new String[] {"POST", "/api/citas", "Agendar cita"},
            new String[] {"PUT", "/api/citas/{}", "Editar o reprogramar cita"},
            new String[] {"PATCH", "/api/citas/{}/estado", "Cambiar estado de cita"},
            new String[] {"DELETE", "/api/citas/{}", "Eliminar cita"},
            new String[] {"GET", "/api/pacientes", "Buscar pacientes"},
            new String[] {"GET", "/api/pacientes/{}", "Datos de un paciente"},
            new String[] {"POST", "/api/pacientes", "Registrar paciente"},
            new String[] {"PUT", "/api/pacientes/{}", "Editar paciente"},
            new String[] {"GET", "/api/pacientes/{}/expediente", "Abrir expediente"},
            new String[] {"PUT", "/api/pacientes/{}/expediente", "Guardar antecedentes del expediente"},
            new String[] {"GET", "/api/pacientes/{}/odontograma/historial", "Historial del odontograma"},
            new String[] {"PUT", "/api/pacientes/{}/odontograma/{}", "Modificar diente del odontograma"},
            new String[] {"GET", "/api/pacientes/{}/estudios", "Lista de radiografías"},
            new String[] {"POST", "/api/pacientes/{}/estudios", "Subir radiografía o estudio"},
            new String[] {"GET", "/api/estudios/{}/archivo", "Ver radiografía"},
            new String[] {"DELETE", "/api/estudios/{}", "Eliminar radiografía"},
            new String[] {"GET", "/api/fichas-tecnicas", "Lista de fichas técnicas"},
            new String[] {"GET", "/api/fichas-tecnicas/{}", "Detalle de ficha técnica"},
            new String[] {"POST", "/api/fichas-tecnicas", "Crear ficha técnica"},
            new String[] {"PUT", "/api/fichas-tecnicas/{}", "Editar ficha técnica"},
            new String[] {"DELETE", "/api/fichas-tecnicas/{}", "Eliminar ficha técnica"});

    private static final List<Object[]> PATRONES = ACCIONES.stream()
            .map(a -> new Object[] {a[0], Pattern.compile(Pattern.quote(a[1]).replace("{}", "\\E[^/]+\\Q")), a[2]})
            .toList();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        long inicio = System.currentTimeMillis();
        try {
            cadena.doFilter(request, response);
        } finally {
            int estado = response.getStatus();
            String metodo = request.getMethod();
            String tipo = estado >= 400 ? "RECHAZO"
                    : request.getRequestURI().startsWith("/api/auth/") ? "SESION"
                    : "GET".equals(metodo) ? "CONSULTA" : "CAMBIO";
            String consulta = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            StringBuilder linea = new StringBuilder(String.format("%-8s | %s | %s %s%s | %d %s | %d ms | desde %s",
                    tipo, describir(metodo, request.getRequestURI()), metodo, request.getRequestURI(), consulta,
                    estado, significado(estado), System.currentTimeMillis() - inicio, request.getRemoteAddr()));
            @SuppressWarnings("unchecked")
            List<String> detalles = (List<String>) request.getAttribute(Bitacora.ATRIBUTO);
            if (detalles != null) {
                detalles.forEach(d -> linea.append(System.lineSeparator()).append("           -> ").append(d));
            }
            if (estado >= 500) log.error(linea.toString());
            else if (estado >= 400) log.warn(linea.toString());
            else log.info(linea.toString());
        }
    }

    static String describir(String metodo, String ruta) {
        for (Object[] p : PATRONES) {
            if (p[0].equals(metodo) && ((Pattern) p[1]).matcher(ruta).matches()) return (String) p[2];
        }
        return "Otra petición";
    }

    static String significado(int estado) {
        return switch (estado) {
            case 200, 201, 204 -> "OK";
            case 400 -> "datos no válidos";
            case 401 -> "correo o contraseña incorrectos";
            case 404 -> "no encontrado";
            case 409 -> "choca con otra cita";
            case 413 -> "archivo demasiado grande";
            case 422 -> "no cumple una regla de negocio";
            default -> estado >= 500 ? "error del servidor" : "";
        };
    }
}
