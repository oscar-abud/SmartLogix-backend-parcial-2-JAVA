package cl.duoc.users.dto;

import cl.duoc.users.model.Usuario;

/**
 * Respuesta publica del usuario. Nunca expone la password.
 */
public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        String rut,
        String telefono,
        String rol,
        Boolean active
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRut(),
                u.getTelefono(), u.getRol(), u.getActive());
    }
}
