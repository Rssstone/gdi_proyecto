package com.gdi.citas.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Horario de atención de la clínica (configurable en application.properties, prefijo "clinica").
 * Un día sin entrada en {@code horarios} se considera cerrado.
 */
@ConfigurationProperties(prefix = "clinica")
public class ClinicaProperties {

    private ZoneId zonaHoraria = ZoneId.of("America/Mexico_City");
    private int intervaloMinutos = 30;
    private Map<DayOfWeek, Horario> horarios = new EnumMap<>(DayOfWeek.class);

    public ZoneId getZonaHoraria() { return zonaHoraria; }
    public void setZonaHoraria(ZoneId zonaHoraria) { this.zonaHoraria = zonaHoraria; }

    public int getIntervaloMinutos() { return intervaloMinutos; }
    public void setIntervaloMinutos(int intervaloMinutos) { this.intervaloMinutos = intervaloMinutos; }

    public Map<DayOfWeek, Horario> getHorarios() { return horarios; }
    public void setHorarios(Map<DayOfWeek, Horario> horarios) { this.horarios = horarios; }

    public static class Horario {
        private LocalTime apertura;
        private LocalTime cierre;

        public LocalTime getApertura() { return apertura; }
        public void setApertura(LocalTime apertura) { this.apertura = apertura; }

        public LocalTime getCierre() { return cierre; }
        public void setCierre(LocalTime cierre) { this.cierre = cierre; }
    }
}
