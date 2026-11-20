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
import com.gdi.citas.dto.AgendaDtos.Tramo;
import com.gdi.citas.dto.AgendaDtos.UrgenciaEspacio;
import com.gdi.citas.exception.ReglaNegocioException;
import com.gdi.citas.model.Cita;
import com.gdi.citas.model.EstadoCita;
import com.gdi.citas.repository.CitaRepository;

/** Reglas de horario de la clínica y cálculo de disponibilidad de la agenda (RF-07). */
@Service
public class AgendaService {

    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final int MAX_DIAS_RESUMEN = 93;
    /**
     * Las citas (programadas y urgencias) empiezan a la hora exacta que acuerde el paciente (ej. 16:23), porque no
     * siempre puede llegar a una hora en punto. Las urgencias duran como máximo una hora.
     */
    private static final int MAX_URGENCIA = 60;
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

    /** Renglones en que se dibuja la agenda del día, cada {@code clinica.intervalo-minutos} (30 min). */
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
     * Lanza una excepción si la cita no se puede agendar: fecha u hora pasada, día cerrado, hora fuera de horario
     * o cita que termina después del cierre. La hora puede ser cualquier minuto (ej. 16:23).
     * Que no se encime con otra cita lo revisa CitaService.
     */
    public void validarHorario(LocalDate fecha, LocalTime hora, int duracionMinutos) {
        LocalDateTime ahora = ahora();
        if (fecha.isBefore(ahora.toLocalDate())) {
            throw new ReglaNegocioException("No se pueden agendar citas en fechas pasadas.");
        }
        Horario horario = horario(fecha).orElseThrow(() -> new ReglaNegocioException(
                "La clínica no atiende los " + nombreDia(fecha.getDayOfWeek()) + "."));
        if (hora.isBefore(horario.getApertura()) || !hora.isBefore(horario.getCierre())) {
            throw new ReglaNegocioException("La hora debe estar dentro del horario de atención ("
                    + horario.getApertura() + " a " + horario.getCierre() + ").");
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
     * Valida una urgencia (sobrecupo): puede empezar a cualquier hora en múltiplos de 5 minutos dentro del horario
     * de atención, no en el pasado, y debe terminar antes del cierre. No se exige un espacio libre.
     */
    public void validarUrgencia(LocalDate fecha, LocalTime hora, int duracionMinutos) {
        LocalDateTime ahora = ahora();
        if (fecha.isBefore(ahora.toLocalDate())) {
            throw new ReglaNegocioException("No se pueden registrar urgencias en fechas pasadas.");
        }
        Horario horario = horario(fecha).orElseThrow(() -> new ReglaNegocioException(
                "La clínica no atiende los " + nombreDia(fecha.getDayOfWeek()) + "."));
        if (duracionMinutos > MAX_URGENCIA) {
            throw new ReglaNegocioException("Una urgencia dura de 10 a " + MAX_URGENCIA + " minutos.");
        }
        if (hora.isBefore(horario.getApertura()) || !cabeAntesDelCierre(hora, duracionMinutos, horario)) {
            throw new ReglaNegocioException("La urgencia debe atenderse dentro del horario de atención ("
                    + horario.getApertura() + " a " + horario.getCierre() + ").");
        }
        if (LocalDateTime.of(fecha, hora).isBefore(ahora)) {
            throw new ReglaNegocioException("Esa hora ya pasó. Elige " + ahora.toLocalTime() + " o después.");
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
                    "La clínica no atiende los " + nombreDia(fecha.getDayOfWeek()) + ".", 0, List.of(), List.of());
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
                // Continuación: la cita empezó en un renglón anterior (una cita de 16:10 se muestra en el de 16:00).
                return new Espacio(hora, EstadoEspacio.OCUPADO, c.getId(), c.getPaciente().getNombreCompleto(),
                        c.getEstado(), c.getHora(), c.getHoraFin(), c.getHora().isBefore(hora), List.of());
            }
            if (!LocalDateTime.of(fecha, hora).isAfter(ahora)) {
                // Hora pasada; si ahí había una cita a la que el paciente no llegó, se informa para mostrarla.
                return inasistenciaQueCubre(citas, hora, hora.plusMinutes(bloque))
                        .map(c -> new Espacio(hora, EstadoEspacio.PASADO, c.getId(), c.getPaciente().getNombreCompleto(),
                                c.getEstado(), c.getHora(), c.getHoraFin(), c.getHora().isBefore(hora), List.of()))
                        .orElseGet(() -> libre(hora, EstadoEspacio.PASADO));
            }
            boolean alcanza = cabeAntesDelCierre(hora, duracion, horario.get())
                    && citaQueCubre(citas, hora, hora.plusMinutes(duracion)).isEmpty();
            return libre(hora, alcanza ? EstadoEspacio.DISPONIBLE : EstadoEspacio.INSUFICIENTE);
        }).map(e -> e.conUrgencias(urgenciasQueEmpiezan(citas, e.hora(), e.hora().plusMinutes(bloque)))).toList();

        int disponibles = (int) espacios.stream().filter(e -> e.estado() == EstadoEspacio.DISPONIBLE).count();
        List<Tramo> libres = tramosLibres(fecha, horario.get(), citas, ahora).stream()
                .filter(t -> ChronoUnit.MINUTES.between(t.inicio(), t.fin()) >= duracion)
                .toList();
        String mensaje = fecha.isBefore(ahora.toLocalDate()) ? "Fecha pasada: solo consulta."
                : libres.isEmpty() ? "No hay espacio libre para una cita de " + duracion + " minutos este día."
                : "Libre: " + libres.stream().map(t -> t.inicio() + " a " + t.fin()).collect(Collectors.joining(", ")) + ".";
        return new Disponibilidad(fecha, true, mensaje, disponibles, espacios, libres);
    }

    /**
     * Tramos del día sin citas que ocupen horario (las urgencias y las inasistencias no ocupan). Si es hoy, empieza en
     * el siguiente minuto.
     */
    private List<Tramo> tramosLibres(LocalDate fecha, Horario horario, List<Cita> citas, LocalDateTime ahora) {
        if (fecha.isBefore(ahora.toLocalDate())) {
            return List.of();
        }
        LocalTime desde = horario.getApertura();
        if (fecha.equals(ahora.toLocalDate())) {
            LocalTime actual = ahora.toLocalTime();
            LocalTime siguiente = actual.plusMinutes(1);
            if (siguiente.isBefore(actual)) {
                return List.of();          // ya pasó la medianoche: no queda tiempo hoy
            }
            if (siguiente.isAfter(desde)) {
                desde = siguiente;
            }
        }
        List<Cita> ocupan = citas.stream()
                .filter(c -> !c.isUrgente() && EstadoCita.OCUPAN_HORARIO.contains(c.getEstado()))
                .sorted((a, b) -> a.getHora().compareTo(b.getHora()))
                .toList();
        List<Tramo> tramos = new ArrayList<>();
        LocalTime cursor = desde;
        for (Cita c : ocupan) {
            if (c.getHora().isAfter(cursor)) {
                tramos.add(new Tramo(cursor, c.getHora().isAfter(horario.getCierre()) ? horario.getCierre() : c.getHora()));
            }
            if (c.getHoraFin().isAfter(cursor)) {
                cursor = c.getHoraFin();
            }
        }
        if (cursor.isBefore(horario.getCierre())) {
            tramos.add(new Tramo(cursor, horario.getCierre()));
        }
        return tramos.stream().filter(t -> t.inicio().isBefore(t.fin())).toList();
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

    /**
     * Cita que ocupa algún minuto del rango. Las de inasistencia no ocupan (liberan su horario) y las urgencias
     * tampoco: se atienden como sobrecupo sin quitarle el espacio a los demás pacientes.
     */
    private static Optional<Cita> citaQueCubre(List<Cita> citas, LocalTime inicio, LocalTime fin) {
        return citas.stream()
                .filter(c -> !c.isUrgente() && EstadoCita.OCUPAN_HORARIO.contains(c.getEstado()) && c.seEmpalmaCon(inicio, fin))
                .findFirst();
    }

    /** Urgencias que empiezan dentro del bloque [inicio, fin), para mostrarlas en la agenda. */
    private static List<UrgenciaEspacio> urgenciasQueEmpiezan(List<Cita> citas, LocalTime inicio, LocalTime fin) {
        return citas.stream()
                .filter(c -> c.isUrgente() && !c.getHora().isBefore(inicio) && c.getHora().isBefore(fin))
                .map(c -> new UrgenciaEspacio(c.getId(), c.getPaciente().getNombreCompleto(), c.getEstado(),
                        c.getHora(), c.getHoraFin()))
                .toList();
    }

    private static Optional<Cita> inasistenciaQueCubre(List<Cita> citas, LocalTime inicio, LocalTime fin) {
        return citas.stream()
                .filter(c -> c.getEstado() == EstadoCita.Inasistencia && c.seEmpalmaCon(inicio, fin))
                .findFirst();
    }

    private static boolean cabeAntesDelCierre(LocalTime hora, int duracionMinutos, Horario horario) {
        LocalTime fin = hora.plusMinutes(duracionMinutos);
        return !fin.isAfter(horario.getCierre()) && fin.isAfter(hora);
    }

    private static Espacio libre(LocalTime hora, EstadoEspacio estado) {
        return new Espacio(hora, estado, null, null, null, null, null, false, List.of());
    }

    private static String nombreDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, ES_MX);
    }
}
