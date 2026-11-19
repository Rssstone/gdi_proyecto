package com.gdi.citas.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Escribe en el registro cada petición a la API: método, ruta, código de respuesta y tiempo. */
@Component
public class RegistroPeticiones extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("gdi.api");

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
            String consulta = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            log.info("{} {}{} -> {} ({} ms) desde {}", request.getMethod(), request.getRequestURI(), consulta,
                    response.getStatus(), System.currentTimeMillis() - inicio, request.getRemoteAddr());
        }
    }
}
