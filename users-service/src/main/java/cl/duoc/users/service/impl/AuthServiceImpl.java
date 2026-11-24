package cl.duoc.users.service.impl;

import cl.duoc.users.dto.LoginRequest;
import cl.duoc.users.dto.LoginResponse;
import cl.duoc.users.exception.UnauthorizedException;
import cl.duoc.users.model.Usuario;
import cl.duoc.users.repository.UsuarioRepository;
import cl.duoc.users.security.JwtService;
import cl.duoc.users.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login real contra la tabla "usuarios".
 *
 * A diferencia de la EP2 (que comparaba contra dos credenciales fijas del
 * application.yml y no emitia token), aqui se busca el usuario por email o por
 * nombre de usuario, se verifica la password con BCrypt y se devuelve un JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String CREDENCIALES_INVALIDAS = "Usuario o password incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identificador = request.usuario().trim();

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(identificador)
                .orElseThrow(() -> {
                    log.debug("Login fallido: no existe el usuario {}", identificador);
                    return new UnauthorizedException(CREDENCIALES_INVALIDAS);
                });

        if (Boolean.FALSE.equals(usuario.getActive())) {
            throw new UnauthorizedException("El usuario esta desactivado");
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            log.debug("Login fallido: password incorrecta para {}", identificador);
            throw new UnauthorizedException(CREDENCIALES_INVALIDAS);
        }

        String token = jwtService.generarToken(usuario);
        log.info("Login exitoso de {} con rol {}", usuario.getEmail(), usuario.getRol());

        return LoginResponse.exitoso(usuario.getEmail(), usuario.getRol(),
                token, jwtService.getExpiracionSegundos());
    }
}
