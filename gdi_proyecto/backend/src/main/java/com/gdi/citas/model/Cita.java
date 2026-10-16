package com.gdi.citas.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "citas")
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odontologo_id")
    private Usuario odontologo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(name = "duracion_minutos", columnDefinition = "INT NOT NULL DEFAULT 30")
    private Integer duracionMinutos = 30;

    private String motivo;

    @Enumerated(EnumType.STRING)
    private EstadoCita estado = EstadoCita.Programada;

    @Column(name = "notas_recepcion", columnDefinition = "TEXT")
    private String notasRecepcion;

    public LocalDateTime getInicio() {
        return LocalDateTime.of(fecha, hora);
    }

    public LocalTime getHoraFin() {
        return hora.plusMinutes(duracionMinutos);
    }

    /** true si esta cita ocupa algún minuto del rango [inicio, fin). */
    public boolean seEmpalmaCon(LocalTime inicio, LocalTime fin) {
        return hora.isBefore(fin) && getHoraFin().isAfter(inicio);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public Usuario getOdontologo() { return odontologo; }
    public void setOdontologo(Usuario odontologo) { this.odontologo = odontologo; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public Integer getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(Integer duracionMinutos) { this.duracionMinutos = duracionMinutos; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public EstadoCita getEstado() { return estado; }
    public void setEstado(EstadoCita estado) { this.estado = estado; }

    public String getNotasRecepcion() { return notasRecepcion; }
    public void setNotasRecepcion(String notasRecepcion) { this.notasRecepcion = notasRecepcion; }
}
