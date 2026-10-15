package com.gdi.citas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.*;
import java.util.List;

@SpringBootApplication
public class CitasApplication {
    public static void main(String[] args) {
        SpringApplication.run(CitasApplication.class, args);
    }
}

@Entity
@Table(name = "citas")
class Cita {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @Column(name = "paciente_id")
    public Integer pacienteId = 1; 
    
    public String fecha;
    public String hora;
    public String motivo;
    public String estado = "Programada";
}

@Repository
interface CitaRepository extends JpaRepository<Cita, Long> {}

@RestController
@RequestMapping("/api/citas")
@CrossOrigin(origins = "*")
class CitaController {
    private final CitaRepository repository;
    public CitaController(CitaRepository repository) { this.repository = repository; }

    @GetMapping
    public List<Cita> obtenerCitas() { return repository.findAll(); }

    @PostMapping
    public Cita crearCita(@RequestBody Cita cita) { return repository.save(cita); }
}