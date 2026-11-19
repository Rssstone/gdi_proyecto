package com.gdi.citas.model;

/**
 * Hallazgos del odontograma. Caries, restauración y fractura se marcan por cara del diente (con un código de una
 * letra para guardarlas); los demás aplican a la pieza completa. Una pieza puede tener ambos a la vez, por ejemplo
 * endodoncia en la pieza y una restauración en la cara oclusal.
 */
public enum EstadoDiente {
    Sano(null),
    Caries("C"),
    Restaurado("R"),
    Fractura("F"),
    Corona(null),
    Endodoncia(null),
    Implante(null),
    Ortodoncia(null),
    PorExtraer(null),
    Ausente(null);

    private final String codigo;

    EstadoDiente(String codigo) {
        this.codigo = codigo;
    }

    public boolean esPorSuperficie() {
        return codigo != null;
    }

    public String codigo() {
        return codigo;
    }

    public static EstadoDiente deCodigo(String codigo) {
        for (EstadoDiente e : values()) {
            if (codigo.equals(e.codigo)) return e;
        }
        throw new IllegalArgumentException("Código de superficie no válido: " + codigo);
    }
}
