package com.gdi.citas;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.gdi.citas.model.Cita;
import com.gdi.citas.model.EstadoCita;
import com.gdi.citas.model.Paciente;
import com.gdi.citas.model.Rol;
import com.gdi.citas.model.Usuario;
import com.gdi.citas.repository.CambioOdontogramaRepository;
import com.gdi.citas.repository.CitaRepository;
import com.gdi.citas.repository.DienteOdontogramaRepository;
import com.gdi.citas.repository.EstudioRepository;
import com.gdi.citas.repository.ExpedienteRepository;
import com.gdi.citas.repository.FichaTecnicaRepository;
import com.gdi.citas.repository.PacienteRepository;
import com.gdi.citas.repository.UsuarioRepository;

/** "Ahora" está fijo en el miércoles 14/10/2026 a las 10:05 (hora de la Ciudad de México). */
@SpringBootTest
class CitasApplicationTests {

    static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    static final LocalDate HOY = LocalDate.of(2026, 10, 14);
    static final LocalDate MANANA = HOY.plusDays(1);
    static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDePrueba() {
            return Clock.fixed(LocalDateTime.of(HOY, LocalTime.of(10, 5)).atZone(CDMX).toInstant(), CDMX);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired CitaRepository citas;
    @Autowired PacienteRepository pacientes;
    @Autowired UsuarioRepository usuarios;
    @Autowired ExpedienteRepository expedientes;
    @Autowired DienteOdontogramaRepository dientes;
    @Autowired CambioOdontogramaRepository cambios;
    @Autowired FichaTecnicaRepository fichas;
    @Autowired EstudioRepository estudios;

    MockMvc mvc;
    Paciente juan;
    Paciente maria;
    Usuario dra;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        estudios.deleteAll();
        fichas.deleteAll();
        cambios.deleteAll();
        dientes.deleteAll();
        expedientes.deleteAll();
        citas.deleteAll();
        pacientes.deleteAll();
        usuarios.deleteAll();
        juan = paciente("Juan Pérez", "5551234567");
        maria = paciente("María García", "5557654321");
        dra = new Usuario();
        dra.setNombreCompleto("Dra. Ana López");
        dra.setRol(Rol.Odontologo);
        dra.setEmail("ana@clinica.com");
        dra.setPassword("12345");
        dra = usuarios.save(dra);
    }

    @Test
    void creaYConsultaUnaCita() throws Exception {
        crear(juan, MANANA, "10:00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Programada"))
                .andExpect(jsonPath("$.hora").value("10:00"))
                .andExpect(jsonPath("$.pacienteNombre").value("Juan Pérez"));

        mvc.perform(get("/api/citas").param("desde", MANANA.toString()).param("hasta", MANANA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void rechazaFechasYHorasPasadas() throws Exception {
        crear(juan, HOY.minusDays(1), "10:00").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("fechas pasadas")));
        crear(juan, HOY, "09:30").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("ya pasó")));
        crear(juan, HOY, "10:30").andExpect(status().isCreated());
    }

    @Test
    void rechazaDiasCerradosYHorasFueraDeHorario() throws Exception {
        crear(juan, DOMINGO, "10:00").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("domingo")));
        crear(juan, MANANA, "19:00").andExpect(status().isUnprocessableContent());
        crear(juan, MANANA, "10:15").andExpect(status().isUnprocessableContent());
    }

    @Test
    void noPermiteEmpalmarCitas() throws Exception {
        crear(juan, MANANA, "11:00").andExpect(status().isCreated());
        crear(maria, MANANA, "11:00").andExpect(status().isConflict());
    }

    @Test
    void cancelarLiberaElHorario() throws Exception {
        int id = citaGuardada(juan, MANANA, LocalTime.of(11, 0), EstadoCita.Programada).getId();
        cambiarEstado(id, "Cancelada").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cancelada"));
        crear(maria, MANANA, "11:00").andExpect(status().isCreated());
        cambiarEstado(id, "Confirmada").andExpect(status().isUnprocessableContent());
    }

    @Test
    void respetaLasTransicionesDeEstadoSegunLaHora() throws Exception {
        int futura = citaGuardada(juan, MANANA, LocalTime.of(9, 0), EstadoCita.Programada).getId();
        cambiarEstado(futura, "Completada").andExpect(status().isUnprocessableContent());
        cambiarEstado(futura, "Confirmada").andExpect(status().isOk());

        int pasada = citaGuardada(maria, HOY, LocalTime.of(9, 0), EstadoCita.Confirmada).getId();
        cambiarEstado(pasada, "Cancelada").andExpect(status().isUnprocessableContent());
        cambiarEstado(pasada, "Completada").andExpect(status().isOk());
        mvc.perform(delete("/api/citas/" + pasada)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void reprogramarVuelveAProgramada() throws Exception {
        int id = citaGuardada(juan, MANANA, LocalTime.of(9, 0), EstadoCita.Confirmada).getId();
        mvc.perform(put("/api/citas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(juan, MANANA, "12:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hora").value("12:00"))
                .andExpect(jsonPath("$.estado").value("Programada"));
    }

    @Test
    void eliminaUnaCita() throws Exception {
        int id = citaGuardada(juan, MANANA, LocalTime.of(9, 0), EstadoCita.Programada).getId();
        mvc.perform(delete("/api/citas/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/citas/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void disponibilidadMarcaPasadosYOcupados() throws Exception {
        citaGuardada(juan, HOY, LocalTime.of(11, 0), EstadoCita.Programada);
        mvc.perform(get("/api/agenda/disponibilidad").param("fecha", HOY.toString())
                        .param("odontologoId", dra.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.espacios", hasSize(20)))
                .andExpect(jsonPath("$.espacios[0].estado").value("PASADO"))
                .andExpect(jsonPath("$.espacios[2].hora").value("10:00"))
                .andExpect(jsonPath("$.espacios[2].estado").value("PASADO"))
                .andExpect(jsonPath("$.espacios[3].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.espacios[4].estado").value("OCUPADO"))
                .andExpect(jsonPath("$.espacios[4].pacienteNombre").value("Juan Pérez"))
                .andExpect(jsonPath("$.disponibles").value(16));
    }

    @Test
    void resumenDelMesIndicaEstadoDeCadaDia() throws Exception {
        mvc.perform(get("/api/agenda/resumen").param("desde", HOY.minusDays(1).toString())
                        .param("hasta", DOMINGO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PASADO"))
                .andExpect(jsonPath("$[1].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[5].estado").value("CERRADO"));
    }

    @Test
    void unaCitaLargaOcupaVariosBloques() throws Exception {
        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(json(juan, MANANA, "10:00", 60)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horaFin").value("11:00"))
                .andExpect(jsonPath("$.duracionMinutos").value(60));
        // 10:30 cae dentro de la extracción de 10:00 a 11:00
        crear(maria, MANANA, "10:30").andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("se empalma")));
        crear(maria, MANANA, "11:00").andExpect(status().isCreated());
    }

    @Test
    void unaCitaLargaNoPuedeEncimarseConLaSiguiente() throws Exception {
        citaGuardada(maria, MANANA, LocalTime.of(11, 0), EstadoCita.Programada);
        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(json(juan, MANANA, "10:30", 60)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(json(juan, MANANA, "10:00", 60)))
                .andExpect(status().isCreated());
    }

    @Test
    void unaCitaNoPuedeTerminarDespuesDelCierre() throws Exception {
        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(json(juan, MANANA, "18:30", 60)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("después del cierre")));
        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(json(juan, MANANA, "18:00", 45)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("múltiplo")));
    }

    @Test
    void disponibilidadConsideraLaDuracion() throws Exception {
        Cita larga = citaGuardada(juan, MANANA, LocalTime.of(10, 0), EstadoCita.Programada);
        larga.setDuracionMinutos(90);
        citas.save(larga);
        mvc.perform(get("/api/agenda/disponibilidad").param("fecha", MANANA.toString())
                        .param("odontologoId", dra.getId().toString()).param("duracion", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.espacios[0].estado").value("DISPONIBLE"))     // 09:00-10:00 cabe
                .andExpect(jsonPath("$.espacios[1].estado").value("INSUFICIENTE"))   // 09:30-10:30 choca
                .andExpect(jsonPath("$.espacios[2].estado").value("OCUPADO"))
                .andExpect(jsonPath("$.espacios[2].continuacion").value(false))
                .andExpect(jsonPath("$.espacios[4].estado").value("OCUPADO"))       // 11:00 sigue ocupado
                .andExpect(jsonPath("$.espacios[4].continuacion").value(true))
                .andExpect(jsonPath("$.espacios[5].estado").value("DISPONIBLE"))     // 11:30 libre
                .andExpect(jsonPath("$.espacios[19].estado").value("INSUFICIENTE")); // 18:30 + 60 pasa el cierre
    }

    @Test
    void inasistenciaSeRegistraYLiberaElRestoDelHorario() throws Exception {
        // Juan tenía de 9:30 a 11:00 y no llegó; son las 10:05.
        Cita cita = citaGuardada(juan, HOY, LocalTime.of(9, 30), EstadoCita.Confirmada);
        cita.setDuracionMinutos(90);
        citas.save(cita);
        crear(maria, HOY, "10:30").andExpect(status().isConflict());

        cambiarEstado(cita.getId(), "Inasistencia").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Inasistencia"));

        mvc.perform(get("/api/agenda/disponibilidad").param("fecha", HOY.toString())
                        .param("odontologoId", dra.getId().toString()))
                .andExpect(jsonPath("$.espacios[1].estado").value("PASADO"))
                .andExpect(jsonPath("$.espacios[1].estadoCita").value("Inasistencia"))
                .andExpect(jsonPath("$.espacios[1].pacienteNombre").value("Juan Pérez"))
                .andExpect(jsonPath("$.espacios[3].estado").value("DISPONIBLE"));
        crear(maria, HOY, "10:30").andExpect(status().isCreated());
        cambiarEstado(cita.getId(), "Completada").andExpect(status().isUnprocessableContent());
    }

    @Test
    void validaCamposObligatorios() throws Exception {
        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.pacienteId").exists());
    }

    @Test
    void loginCifraLaContrasenaHeredada() throws Exception {
        String body = "{\"email\":\"ana@clinica.com\",\"password\":\"12345\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("Odontologo"));
        org.junit.jupiter.api.Assertions.assertTrue(usuarios.findById(dra.getId()).orElseThrow().getPassword().startsWith("$2"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@clinica.com\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    /* ---------- Urgencias (sobrecupo) ---------- */

    @Test
    void unaUrgenciaSeAtiendeComoSobrecupo() throws Exception {
        crear(juan, HOY, "10:30").andExpect(status().isCreated());
        // María llega con un bracket despegado: se le atiende a las 10:35 aunque Juan ya tiene cita.
        urgencia(maria, HOY, "10:35", 15).andExpect(status().isCreated())
                .andExpect(jsonPath("$.urgente").value(true))
                .andExpect(jsonPath("$.horaFin").value("10:50"));

        // La urgencia no le quita el espacio a nadie: el horario de las 11:00 sigue disponible.
        mvc.perform(get("/api/agenda/disponibilidad").param("fecha", HOY.toString()))
                .andExpect(jsonPath("$.espacios[?(@.hora=='10:30')].urgencias[0].pacienteNombre").value("María García"))
                .andExpect(jsonPath("$.espacios[?(@.hora=='11:00')].estado").value("DISPONIBLE"));
        crear(maria, HOY, "11:00").andExpect(status().isCreated());
    }

    @Test
    void lasUrgenciasRespetanElHorarioDeAtencion() throws Exception {
        urgencia(juan, HOY, "10:00", 15).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("ya pasó")));
        urgencia(juan, HOY, "18:55", 15).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("horario de atención")));
        urgencia(juan, HOY, "10:12", 15).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("múltiplos de 5")));
        urgencia(juan, DOMINGO, "11:00", 15).andExpect(status().isUnprocessableContent());
        urgencia(juan, HOY, "10:10", 20).andExpect(status().isCreated());
    }

    @Test
    void unPacienteNoPuedeTenerUrgenciaYCitaAlMismoTiempo() throws Exception {
        crear(juan, HOY, "11:00").andExpect(status().isCreated());
        urgencia(juan, HOY, "11:10", 15).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("Juan Pérez ya tiene una cita")));
    }

    /* ---------- Expediente y odontograma ---------- */

    @Test
    void guardaElExpedienteYElOdontograma() throws Exception {
        mvc.perform(put("/api/pacientes/" + juan.getId() + "/expediente").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alergias\":\"Penicilina\",\"antecedentesPersonales\":\"Diabetes tipo 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergias").value("Penicilina"));

        String base = "/api/pacientes/" + juan.getId();
        // Endodoncia en la pieza y, además, caries en la cara oclusal y resina en la mesial.
        mvc.perform(put(base + "/odontograma/16").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Endodoncia\",\"caras\":{\"M\":\"Restaurado\",\"O\":\"Caries\"},\"usuarioId\":" + dra.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Endodoncia"))
                .andExpect(jsonPath("$.caras.O").value("Caries"));
        // Después del tratamiento la caries queda restaurada.
        mvc.perform(put(base + "/odontograma/16").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Endodoncia\",\"caras\":{\"M\":\"Restaurado\",\"O\":\"Restaurado\"},\"descripcion\":\"Resina oclusal\"}"))
                .andExpect(jsonPath("$.caras.O").value("Restaurado"));

        mvc.perform(get(base + "/expediente"))
                .andExpect(jsonPath("$.dientes", hasSize(1)))
                .andExpect(jsonPath("$.dientes[0].numero").value(16))
                .andExpect(jsonPath("$.dientes[0].caras.M").value("Restaurado"));

        mvc.perform(get(base + "/odontograma/historial"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].antes").value("Endodoncia · Caries (O) · Restaurado (M)"))
                .andExpect(jsonPath("$[0].despues").value("Endodoncia · Restaurado (O, M)"))
                .andExpect(jsonPath("$[1].antes").value("Sano"))
                .andExpect(jsonPath("$[1].usuarioNombre").value("Dra. Ana López"));

        // Ayer la pieza 16 todavía estaba sana: el odontograma de esa fecha no la muestra.
        mvc.perform(get(base + "/expediente").param("fecha", HOY.minusDays(1).toString()))
                .andExpect(jsonPath("$.dientes", hasSize(0)));

        mvc.perform(put(base + "/odontograma/21").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"Caries\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(put(base + "/odontograma/21").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"Sano\",\"caras\":{\"X\":\"Caries\"}}")).andExpect(status().isUnprocessableContent());
        mvc.perform(put(base + "/odontograma/19").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"Corona\"}")).andExpect(status().isUnprocessableContent());
    }

    /* ---------- Módulo técnico ---------- */

    @Test
    void registraLaFichaTecnicaDeUnaCorona() throws Exception {
        String ficha = """
                {"pacienteId":%d,"odontologoId":%d,"tipoTrabajo":"Corona","piezas":"14 ,15,16","material":"Zirconia",
                 "colorVita":"A2","anchoMm":10.5,"altoMm":7.2,"grosorMm":1.5,"terminacion":"Chamfer",
                 "fechaToma":"%s","fechaEntrega":"%s"}
                """.formatted(juan.getId(), dra.getId(), HOY, HOY.plusDays(10));
        mvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON).content(ficha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.piezas").value("14, 15, 16"))
                .andExpect(jsonPath("$.anchoMm").value(10.5));
        mvc.perform(get("/api/fichas-tecnicas").param("pacienteId", juan.getId().toString()))
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON)
                        .content(ficha.replace("\"A2\"", "\"Z9\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.colorVita").exists());
        mvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON)
                        .content(ficha.replace(HOY.plusDays(10).toString(), HOY.minusDays(1).toString())))
                .andExpect(status().isUnprocessableContent());
    }

    /* ---------- Estudios ---------- */

    @Test
    void subeYMuestraUnaRadiografia() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        String respuesta = mvc.perform(multipart("/api/pacientes/" + juan.getId() + "/estudios")
                        .file(new MockMultipartFile("archivo", "rx.png", "image/png", png))
                        .param("tipo", "Radiografía").param("descripcion", "Panorámica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tamano").value(4))
                .andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(respuesta.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/estudios/" + id + "/archivo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mvc.perform(get("/api/pacientes/" + juan.getId() + "/estudios")).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(multipart("/api/pacientes/" + juan.getId() + "/estudios")
                        .file(new MockMultipartFile("archivo", "virus.exe", "application/octet-stream", png))
                        .param("tipo", "Radiografía"))
                .andExpect(status().isUnprocessableContent());
    }

    private ResultActions urgencia(Paciente p, LocalDate fecha, String hora, int minutos) throws Exception {
        String cuerpo = """
                {"pacienteId":%d,"odontologoId":%d,"fecha":"%s","hora":"%s","duracionMinutos":%d,
                 "motivo":"Urgencia: bracket despegado","urgente":true}
                """.formatted(p.getId(), dra.getId(), fecha, hora, minutos);
        return mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions crear(Paciente p, LocalDate fecha, String hora) throws Exception {
        return mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(json(p, fecha, hora)));
    }

    private ResultActions cambiarEstado(int id, String estado) throws Exception {
        return mvc.perform(patch("/api/citas/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}"));
    }

    private String json(Paciente p, LocalDate fecha, String hora) {
        return json(p, fecha, hora, 30);
    }

    private String json(Paciente p, LocalDate fecha, String hora, int duracion) {
        return """
                {"pacienteId":%d,"odontologoId":%d,"fecha":"%s","hora":"%s","duracionMinutos":%d,"motivo":"Limpieza dental"}
                """.formatted(p.getId(), dra.getId(), fecha, hora, duracion);
    }

    private Paciente paciente(String nombre, String telefono) {
        Paciente p = new Paciente();
        p.setNombreCompleto(nombre);
        p.setTelefono(telefono);
        return pacientes.save(p);
    }

    private Cita citaGuardada(Paciente p, LocalDate fecha, LocalTime hora, EstadoCita estado) {
        Cita c = new Cita();
        c.setPaciente(p);
        c.setOdontologo(dra);
        c.setFecha(fecha);
        c.setHora(hora);
        c.setMotivo("Revisión");
        c.setEstado(estado);
        return citas.save(c);
    }
}
