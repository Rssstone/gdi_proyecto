package com.gdi.citas.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdi.citas.config.ClinicaProperties;
import com.gdi.citas.config.ClinicaProperties.Horario;
import com.gdi.citas.dto.AgendaDtos.Configuracion;
import com.gdi.citas.dto.AgendaDtos.Disponibilidad;
import com.gdi.citas.dto.AgendaDtos.Espacio;
import com.gdi.citas.dto.AgendaDtos.EstadoDia;
import com.gdi.citas.dto.AgendaDtos.EstadoEspacio;
import com.gdi.citas.dto.AgendaDtos.HorarioDia;
import com.gdi.citas.dto.AgendaDtos.ResumenDia;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.Cita;
import com.gdi.citas.model.EstadoCita;
import com.gdi.citas.repository.CitaRepository;

/** Reglas de horario de la clínica y cálculo de disponibilidad de la agenda (RF-07). */
@Service
public class AgendaService {

    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final int MAX_DIAS_RESUMEN = 93;
    /** Estados que se muestran en la agenda (las canceladas liberan el espacio). */
    private static final EnumSet<EstadoCita> VISIBLES = EnumSet.complementOf(EnumSet.of(EstadoCita.Cancelada));

    private final ClinicaProperties clinica;
    private final CitaRepository citaRepository;
    private final Clock clock;

    public AgendaService(ClinicaProperties clinica, CitaRepository citaRepository, Clock clock) {
        this.clinica = clinica;
        this.citaRepository = citaRepository;
        this.clock = clock;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }

    public boolean yaPaso(Cita cita) {
        return !cita.getInicio().isAfter(ahora());
    }

    public Optional<Horario> horario(LocalDate fecha) {
        return Optional.ofNullable(clinica.getHorarios().get(fecha.getDayOfWeek()));
    }

    /** Horas de inicio de cita para un día, en intervalos de {@code clinica.intervalo-minutos}. */
    public List<LocalTime> espaciosDelDia(LocalDate fecha) {
        List<LocalTime> espacios = new ArrayList<>();
        horario(fecha).ifPresent(h -> {
            int paso = clinica.getIntervaloMinutos();
            for (LocalTime t = h.getApertura(); !t.plusMinutes(paso).isAfter(h.getCierre())
                    && !t.plusMinutes(paso).isBefore(t); t = t.plusMinutes(paso)) {
                espacios.add(t);
            }
        });
        return espacios;
    }

    /**
     * Lanza una excepción si la cita no se puede agendar: fecha u hora pasada, día cerrado,
     * hora fuera de horario o duración que termina después del cierre.
     */
    public void validarHorario(LocalDate fecha, LocalTime hora, int duracionMinutos) {
        LocalDateTime ahora = ahora();
        if (fecha.isBefore(ahora.toLocalDate())) {
            throw new ReglaNegocioException("No se pueden agendar citas en fechas pasadas.");
        }
        Horario horario = horario(fecha).orElseThrow(() -> new ReglaNegocioException(
                "La clínica no atiende los " + nombreDia(fecha.getDayOfWeek()) + "."));
        if (!espaciosDelDia(fecha).contains(hora)) {
            throw new ReglaNegocioException("La hora debe estar dentro del horario de atención ("
                    + horario.getApertura() + " a " + horario.getCierre() + ") y en intervalos de "
                    + clinica.getIntervaloMinutos() + " minutos.");
        }
        if (duracionMinutos % clinica.getIntervaloMinutos() != 0) {
            throw new ReglaNegocioException("La duración debe ser múltiplo de "
                    + clinica.getIntervaloMinutos() + " minutos.");
        }
        if (!cabeAntesDelCierre(hora, duracionMinutos, horario)) {
            throw new ReglaNegocioException("Una cita de " + duracionMinutos + " minutos a las " + hora
                    + " terminaría a las " + hora.plusMinutes(duracionMinutos) + ", después del cierre ("
                    + horario.getCierre() + ").");
        }
        if (!LocalDateTime.of(fecha, hora).isAfter(ahora)) {
            throw new ReglaNegocioException("Esa hora ya pasó. Elige un horario posterior a las "
                    + ahora.toLocalTime() + ".");
        }
    }

    /**
     * Estado de cada horario del día. Con {@code duracionMinutos} mayor a un bloque, un horario libre
     * solo se marca DISPONIBLE si la cita completa cabe sin encimarse con otra ni pasar del cierre.
     */
    @Transactional(readOnly = true)
    public Disponibilidad disponibilidad(LocalDate fecha, Integer odontologoId, Integer excluirCitaId,
                                         Integer duracionMinutos) {
        Optional<Horario> horario = horario(fecha);
        if (horario.isEmpty()) {
            return new Disponibilidad(fecha, false,
                    "La clínica no atiende los " + nombreDia(fecha.getDayOfWeek()) + ".", 0, List.of());
        }
        List<Cita> citas = citasDelRango(fecha, fecha, odontologoId).stream()
                .filter(c -> !Objects.equals(c.getId(), excluirCitaId))
                .toList();
        int bloque = clinica.getIntervaloMinutos();
        int duracion = duracionMinutos == null ? bloque : duracionMinutos;

        LocalDateTime ahora = ahora();
        List<Espacio> espacios = espaciosDelDia(fecha).stream().map(hora -> {
            Optional<Cita> cubre = citaQueCubre(citas, hora, hora.plusMinutes(bloque));
            if (cubre.isPresent()) {
                Cita c = cubre.get();
                return new Espacio(hora, EstadoEspacio.OCUPADO, c.getId(), c.getPaciente().getNombreCompleto(),
                        c.getEstado(), c.getHora(), c.getHoraFin(), !c.getHora().equals(hora));
            }
            if (!LocalDateTime.of(fecha, hora).isAfter(ahora)) {
                return libre(hora, EstadoEspacio.PASADO);
            }
            boolean alcanza = cabeAntesDelCierre(hora, duracion, horario.get())
                    && citaQueCubre(citas, hora, hora.plusMinutes(duracion)).isEmpty();
            return libre(hora, alcanza ? EstadoEspacio.DISPONIBLE : EstadoEspacio.INSUFICIENTE);
        }).toList();

        int disponibles = (int) espacios.stream().filter(e -> e.estado() == EstadoEspacio.DISPONIBLE).count();
        String mensaje = fecha.isBefore(ahora.toLocalDate()) ? "Fecha pasada: solo consulta."
                : disponibles == 0 ? "No hay horarios disponibles en este día."
                : disponibles + " horario(s) disponible(s).";
        return new Disponibilidad(fecha, true, mensaje, disponibles, espacios);
    }

    @Transactional(readOnly = true)
    public List<ResumenDia> resumen(LocalDate desde, LocalDate hasta, Integer odontologoId) {
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS_RESUMEN) {
            throw new ReglaNegocioException("Rango de fechas no válido (máximo " + MAX_DIAS_RESUMEN + " días).");
        }
        Map<LocalDate, List<Cita>> porDia = citasDelRango(desde, hasta, odontologoId).stream()
                .collect(Collectors.groupingBy(Cita::getFecha));
        LocalDateTime ahora = ahora();
        int bloque = clinica.getIntervaloMinutos();

        List<ResumenDia> dias = new ArrayList<>();
        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            List<Cita> citas = porDia.getOrDefault(f, List.of());
            List<LocalTime> espacios = espaciosDelDia(f);
            final LocalDate fecha = f;
            int disponibles = (int) espacios.stream()
                    .filter(h -> LocalDateTime.of(fecha, h).isAfter(ahora)
                            && citaQueCubre(citas, h, h.plusMinutes(bloque)).isEmpty())
                    .count();

            EstadoDia estado;
            if (espacios.isEmpty()) {
                estado = EstadoDia.CERRADO;
            } else if (f.isBefore(ahora.toLocalDate())) {
                estado = EstadoDia.PASADO;
            } else if (disponibles > 0) {
                estado = EstadoDia.DISPONIBLE;
            } else {
                boolean quedanHoras = espacios.stream().anyMatch(h -> LocalDateTime.of(fecha, h).isAfter(ahora));
                estado = quedanHoras ? EstadoDia.LLENO : EstadoDia.PASADO;
            }
            dias.add(new ResumenDia(f, estado, citas.size(), disponibles, espacios.size()));
        }
        return dias;
    }

    public Configuracion configuracion() {
        Map<String, HorarioDia> horarios = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            Horario h = clinica.getHorarios().get(d);
            horarios.put(d.name(), h == null ? null : new HorarioDia(h.getApertura(), h.getCierre()));
        }
        LocalDateTime ahora = ahora();
        return new Configuracion(ahora.toLocalDate(), ahora, clinica.getZonaHoraria().getId(),
                clinica.getIntervaloMinutos(), horarios);
    }

    private List<Cita> citasDelRango(LocalDate desde, LocalDate hasta, Integer odontologoId) {
        return citaRepository.findByFechaBetweenAndEstadoInOrderByFechaAscHoraAsc(desde, hasta, VISIBLES).stream()
                .filter(c -> odontologoId == null
                        || (c.getOdontologo() != null && odontologoId.equals(c.getOdontologo().getId())))
                .toList();
    }

    private static Optional<Cita> citaQueCubre(List<Cita> citas, LocalTime inicio, LocalTime fin) {
        return citas.stream().filter(c -> c.seEmpalmaCon(inicio, fin)).findFirst();
    }

    private static boolean cabeAntesDelCierre(LocalTime hora, int duracionMinutos, Horario horario) {
        LocalTime fin = hora.plusMinutes(duracionMinutos);
        return !fin.isAfter(horario.getCierre()) && fin.isAfter(hora);
    }

    private static Espacio libre(LocalTime hora, EstadoEspacio estado) {
        return new Espacio(hora, estado, null, null, null, null, null, false);
    }

    private static String nombreDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, ES_MX);
    }
}
