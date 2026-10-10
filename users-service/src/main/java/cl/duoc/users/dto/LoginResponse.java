package cl.duoc.users.dto;

/**
 * Resultado de un login exitoso. El valor de {@code rol} es el que se debe enviar
 * en la cabecera {@code X-User-Role} de las siguientes peticiones.
 */
public record LoginResponse(
        String mensaje,
        String usuario,
        String rol
) {
}
