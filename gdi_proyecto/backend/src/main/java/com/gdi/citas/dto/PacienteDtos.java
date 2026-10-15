package com.gdi.citas.dto;

import java.time.LocalDate;

import com.gdi.citas.model.Paciente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PacienteDtos {

    private PacienteDtos() {
    }

    public record PacienteRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 150, message = "El nombre no puede exceder 150 caracteres") String nombreCompleto,
            @NotBlank(message = "El teléfono es obligatorio para confirmar citas")
            @Pattern(regexp = "^[0-9+()\\-\\s]{8,20}$", message = "Teléfono no válido") String telefono,
            @Past(message = "La fecha de nacimiento debe ser anterior a hoy") LocalDate fechaNacimiento,
            @Email(message = "Correo no válido") @Size(max = 100) String email) {
    }

    public record PacienteResponse(Integer id, String nombreCompleto, String telefono,
                                   LocalDate fechaNacimiento, String email) {
        public static PacienteResponse de(Paciente p) {
            return new PacienteResponse(p.getId(), p.getNombreCompleto(), p.getTelefono(),
                    p.getFechaNacimiento(), p.getEmail());
        }
    }
}
