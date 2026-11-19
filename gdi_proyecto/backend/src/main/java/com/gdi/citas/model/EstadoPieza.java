package com.gdi.citas.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado completo de una pieza: un hallazgo de pieza completa (corona, endodoncia…) y los hallazgos por cara
 * (O oclusal/incisal, M mesial, D distal, V vestibular, L lingual/palatino). Las caras se guardan como "O:C,M:R".
 */
public record EstadoPieza(EstadoDiente estado, Map<String, EstadoDiente> caras) {

    public static final List<String> CARAS = List.of("O", "M", "D", "V", "L");
    public static final EstadoPieza SANA = new EstadoPieza(EstadoDiente.Sano, Map.of());

    /** Lee lo guardado. Acepta también el formato anterior (estado de cara + "O,M"). */
    public static EstadoPieza de(EstadoDiente estado, String guardadas) {
        EstadoDiente pieza = estado == null ? EstadoDiente.Sano : estado;
        Map<String, EstadoDiente> caras = new LinkedHashMap<>();
        if (guardadas != null && !guardadas.isBlank()) {
            for (String parte : guardadas.split(",")) {
                String[] cv = parte.trim().split(":");
                if (cv.length == 2) caras.put(cv[0], EstadoDiente.deCodigo(cv[1]));
                else if (pieza.esPorSuperficie()) caras.put(cv[0], pieza);
            }
        }
        if (pieza.esPorSuperficie()) pieza = EstadoDiente.Sano;
        return new EstadoPieza(pieza, ordenar(caras));
    }

    public String carasGuardadas() {
        if (caras.isEmpty()) return null;
        List<String> partes = new ArrayList<>();
        caras.forEach((cara, e) -> partes.add(cara + ":" + e.codigo()));
        return String.join(",", partes);
    }

    public boolean esSana() {
        return estado == EstadoDiente.Sano && caras.isEmpty();
    }

    /** Resumen legible para el historial, por ejemplo "Endodoncia · Caries (O, M) · Restaurado (D)". */
    public String texto() {
        if (esSana()) return "Sano";
        List<String> partes = new ArrayList<>();
        if (estado != EstadoDiente.Sano) partes.add(nombre(estado));
        Map<EstadoDiente, List<String>> porEstado = new LinkedHashMap<>();
        caras.forEach((cara, e) -> porEstado.computeIfAbsent(e, k -> new ArrayList<>()).add(cara));
        porEstado.forEach((e, lista) -> partes.add(nombre(e) + " (" + String.join(", ", lista) + ")"));
        return String.join(" · ", partes);
    }

    private static String nombre(EstadoDiente e) {
        return switch (e) {
            case PorExtraer -> "Por extraer";
            case Ortodoncia -> "Bracket";
            default -> e.name();
        };
    }

    private static Map<String, EstadoDiente> ordenar(Map<String, EstadoDiente> caras) {
        Map<String, EstadoDiente> orden = new LinkedHashMap<>();
        CARAS.forEach(c -> { if (caras.containsKey(c)) orden.put(c, caras.get(c)); });
        return orden;
    }

    public static EstadoPieza nueva(EstadoDiente estado, Map<String, EstadoDiente> caras) {
        return new EstadoPieza(estado, ordenar(caras));
    }
}
