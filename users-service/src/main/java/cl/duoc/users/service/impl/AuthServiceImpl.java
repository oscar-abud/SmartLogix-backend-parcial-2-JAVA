package cl.duoc.users.service.impl;

import cl.duoc.users.dto.LoginRequest;
import cl.duoc.users.dto.LoginResponse;
import cl.duoc.users.exception.UnauthorizedException;
import cl.duoc.users.service.AuthService;
import cl.duoc.users.service.RoleValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Login con credenciales fijas para cada rol (definidas en application.yml, seccion "auth").
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final String usuarioAdmin;
    private final String passwordAdmin;
    private final String usuarioOperador;
    private final String passwordOperador;

    public AuthServiceImpl(@Value("${auth.admin.usuario}") String usuarioAdmin,
                           @Value("${auth.admin.password}") String passwordAdmin,
                           @Value("${auth.operador.usuario}") String usuarioOperador,
                           @Value("${auth.operador.password}") String passwordOperador) {
        this.usuarioAdmin = usuarioAdmin;
        this.passwordAdmin = passwordAdmin;
        this.usuarioOperador = usuarioOperador;
        this.passwordOperador = passwordOperador;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String usuario = request.usuario();
        String password = request.password();

        if (usuario.equals(usuarioAdmin) && password.equals(passwordAdmin)) {
            return new LoginResponse("Login exitoso", usuario, RoleValidator.ADMIN);
        } else if (usuario.equals(usuarioOperador) && password.equals(passwordOperador)) {
            return new LoginResponse("Login exitoso", usuario, RoleValidator.OPERADOR);
        } else {
            throw new UnauthorizedException("Usuario o password incorrectos");
        }
    }
}
