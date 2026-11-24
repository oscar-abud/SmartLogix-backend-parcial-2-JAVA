package cl.duoc.users.dto;

/**
 * Resultado de un login exitoso.
 *
 * El {@code token} debe enviarse en las siguientes peticiones como
 * {@code Authorization: Bearer <token>}. Sustituye a la antigua cabecera
 * {@code X-User-Role} de la EP2, que el cliente podia falsificar.
 */
public record LoginResponse(
        String mensaje,
        String usuario,
        String rol,
        String token,
        String tipo,
        long expiraEnSegundos
) {
    public static LoginResponse exitoso(String usuario, String rol, String token, long expiraEnSegundos) {
        return new LoginResponse("Login exitoso", usuario, rol, token, "Bearer", expiraEnSegundos);
    }
}
