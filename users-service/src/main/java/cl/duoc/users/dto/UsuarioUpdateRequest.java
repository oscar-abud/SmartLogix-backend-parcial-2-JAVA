package cl.duoc.users.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Actualizacion parcial del perfil: solo se modifican los campos que vienen con valor.
 */
public record UsuarioUpdateRequest(
        @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres")
        @Pattern(regexp = ".*\\S.*", message = "El nombre no puede estar vacio")
        String nombre,

        @Size(max = 20, message = "El telefono no puede superar 20 caracteres")
        String telefono,

        @Size(min = 6, message = "La password debe tener al menos 6 caracteres")
        String password
) {
}
