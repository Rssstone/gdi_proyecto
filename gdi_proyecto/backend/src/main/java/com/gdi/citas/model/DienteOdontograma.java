package com.gdi.citas.model;

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
import jakarta.persistence.UniqueConstraint;

/** Una pieza del odontograma con hallazgo. Las piezas sanas no se guardan. Numeración FDI (11 a 48). */
@Entity
@Table(name = "odontogramas", uniqueConstraints = @UniqueConstraint(columnNames = {"expediente_id", "numero_diente"}))
public class DienteOdontograma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Column(name = "numero_diente", nullable = false)
    private Integer numeroDiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_diente", length = 100)
    private EstadoDiente estado;

    /** Caras afectadas, por ejemplo "O,M": O oclusal/incisal, M mesial, D distal, V vestibular, L lingual/palatino. */
    @Column(length = 20)
    private String superficies;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    public Integer getId() { return id; }

    public Expediente getExpediente() { return expediente; }
    public void setExpediente(Expediente expediente) { this.expediente = expediente; }

    public Integer getNumeroDiente() { return numeroDiente; }
    public void setNumeroDiente(Integer numeroDiente) { this.numeroDiente = numeroDiente; }

    public EstadoDiente getEstado() { return estado; }
    public void setEstado(EstadoDiente estado) { this.estado = estado; }

    public String getSuperficies() { return superficies; }
    public void setSuperficies(String superficies) { this.superficies = superficies; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
