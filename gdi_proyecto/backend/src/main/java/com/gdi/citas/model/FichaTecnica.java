package com.gdi.citas.model;

import java.math.BigDecimal;
import java.time.LocalDate;

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

/** Módulo técnico: especificaciones de una corona, puente o prótesis que toma el odontólogo. */
@Entity
@Table(name = "fichas_tecnicas")
public class FichaTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odontologo_id")
    private Usuario odontologo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_trabajo", nullable = false, length = 30)
    private TipoTrabajo tipoTrabajo;

    /** Piezas dentales en numeración FDI, por ejemplo "16" o "14, 15, 16". */
    @Column(nullable = false, length = 60)
    private String piezas;

    @Column(nullable = false, length = 60)
    private String material;

    /** Tono según la guía VITA (A1 a D4). */
    @Column(name = "color_vita", length = 10)
    private String colorVita;

    @Column(name = "ancho_mm", precision = 5, scale = 2)
    private BigDecimal anchoMm;

    @Column(name = "alto_mm", precision = 5, scale = 2)
    private BigDecimal altoMm;

    @Column(name = "grosor_mm", precision = 5, scale = 2)
    private BigDecimal grosorMm;

    /** Tipo de terminación de la preparación (chamfer, hombro…). */
    @Column(length = 40)
    private String terminacion;

    @Column(length = 120)
    private String laboratorio;

    @Column(name = "fecha_toma", nullable = false)
    private LocalDate fechaToma;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    @Column(columnDefinition = "TEXT")
    private String notas;

    public Integer getId() { return id; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public Usuario getOdontologo() { return odontologo; }
    public void setOdontologo(Usuario odontologo) { this.odontologo = odontologo; }

    public TipoTrabajo getTipoTrabajo() { return tipoTrabajo; }
    public void setTipoTrabajo(TipoTrabajo tipoTrabajo) { this.tipoTrabajo = tipoTrabajo; }

    public String getPiezas() { return piezas; }
    public void setPiezas(String piezas) { this.piezas = piezas; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getColorVita() { return colorVita; }
    public void setColorVita(String colorVita) { this.colorVita = colorVita; }

    public BigDecimal getAnchoMm() { return anchoMm; }
    public void setAnchoMm(BigDecimal anchoMm) { this.anchoMm = anchoMm; }

    public BigDecimal getAltoMm() { return altoMm; }
    public void setAltoMm(BigDecimal altoMm) { this.altoMm = altoMm; }

    public BigDecimal getGrosorMm() { return grosorMm; }
    public void setGrosorMm(BigDecimal grosorMm) { this.grosorMm = grosorMm; }

    public String getTerminacion() { return terminacion; }
    public void setTerminacion(String terminacion) { this.terminacion = terminacion; }

    public String getLaboratorio() { return laboratorio; }
    public void setLaboratorio(String laboratorio) { this.laboratorio = laboratorio; }

    public LocalDate getFechaToma() { return fechaToma; }
    public void setFechaToma(LocalDate fechaToma) { this.fechaToma = fechaToma; }

    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
