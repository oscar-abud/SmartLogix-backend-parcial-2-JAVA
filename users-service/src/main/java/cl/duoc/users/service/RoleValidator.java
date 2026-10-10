package cl.duoc.users.service;

import cl.duoc.users.exception.BadRequestException;
import cl.duoc.users.exception.ForbiddenException;
import cl.duoc.users.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Valida el rol del solicitante recibido en la cabecera {@code X-User-Role}.
 */
@Component
public class RoleValidator {

    public static final String ADMIN = "ADMIN";
    public static final String OPERADOR = "OPERADOR";
    private static final Set<String> ROLES_VALIDOS = Set.of(ADMIN, OPERADOR);

    /**
     * @throws UnauthorizedException 401 si no se envia el rol
     * @throws BadRequestException   400 si el rol no es ADMIN ni OPERADOR
     * @throws ForbiddenException    403 si el rol no es ADMIN
     */
    public void requireAdmin(String role) {
        if (role == null || role.isBlank()) {
            throw new UnauthorizedException("Debe enviar la cabecera X-User-Role para identificar su rol");
        }
        String normalized = role.trim().toUpperCase();
        if (!ROLES_VALIDOS.contains(normalized)) {
            throw new BadRequestException("Rol invalido: '" + role + "'. Valores permitidos: ADMIN, OPERADOR");
        }
        if (!ADMIN.equals(normalized)) {
            throw new ForbiddenException("Acceso denegado: esta operacion requiere rol ADMIN");
        }
    }
}
