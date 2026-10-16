package com.gdi.citas.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdi.citas.dto.AgendaDtos.Configuracion;
import com.gdi.citas.dto.AgendaDtos.Disponibilidad;
import com.gdi.citas.dto.AgendaDtos.ResumenDia;
import com.gdi.citas.service.AgendaService;

@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping("/configuracion")
    public Configuracion configuracion() {
        return agendaService.configuracion();
    }

    @GetMapping("/disponibilidad")
    public Disponibilidad disponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer odontologoId,
            @RequestParam(required = false) Integer excluirCitaId,
            @RequestParam(required = false) Integer duracion) {
        return agendaService.disponibilidad(fecha, odontologoId, excluirCitaId, duracion);
    }

    @GetMapping("/resumen")
    public List<ResumenDia> resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer odontologoId) {
        return agendaService.resumen(desde, hasta, odontologoId);
    }
}
