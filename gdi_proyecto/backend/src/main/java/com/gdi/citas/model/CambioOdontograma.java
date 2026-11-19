package com.gdi.citas.model;

import java.time.LocalDateTime;

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

/** Historial del odontograma: cada cambio de una pieza con su estado anterior y el nuevo. */
@Entity
@Table(name = "odontograma_historial")
public class CambioOdontograma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Column(name = "numero_diente", nullable = false)
    private Integer numeroDiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 30)
    private EstadoDiente estadoAnterior;

    @Column(name = "caras_anteriores", length = 20)
    private String carasAnteriores;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", length = 30)
    private EstadoDiente estadoNuevo;

    @Column(name = "caras_nuevas", length = 20)
    private String carasNuevas;

    @Column(length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Integer getId() { return id; }

    public Expediente getExpediente() { return expediente; }
    public void setExpediente(Expediente expediente) { this.expediente = expediente; }

    public Integer getNumeroDiente() { return numeroDiente; }
    public void setNumeroDiente(Integer numeroDiente) { this.numeroDiente = numeroDiente; }

    public EstadoPieza getAnterior() { return EstadoPieza.de(estadoAnterior, carasAnteriores); }
    public void setAnterior(EstadoPieza p) { this.estadoAnterior = p.estado(); this.carasAnteriores = p.carasGuardadas(); }

    public EstadoPieza getNuevo() { return EstadoPieza.de(estadoNuevo, carasNuevas); }
    public void setNuevo(EstadoPieza p) { this.estadoNuevo = p.estado(); this.carasNuevas = p.carasGuardadas(); }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
