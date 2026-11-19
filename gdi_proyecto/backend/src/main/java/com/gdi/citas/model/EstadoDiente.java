package com.gdi.citas.model;

/**
 * Estado de una pieza en el odontograma. Los que afectan superficies (caries, restauración, fractura) se marcan
 * por cara del diente; los demás aplican a la pieza completa.
 */
public enum EstadoDiente {
    Sano(false),
    Caries(true),
    Restaurado(true),
    Fractura(true),
    Corona(false),
    Endodoncia(false),
    Implante(false),
    Ortodoncia(false),
    PorExtraer(false),
    Ausente(false);

    private final boolean porSuperficie;

    EstadoDiente(boolean porSuperficie) {
        this.porSuperficie = porSuperficie;
    }

    public boolean esPorSuperficie() {
        return porSuperficie;
    }
}
