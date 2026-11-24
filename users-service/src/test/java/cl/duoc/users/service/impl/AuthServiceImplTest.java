package cl.duoc.users.service.impl;

import cl.duoc.users.dto.LoginRequest;
import cl.duoc.users.dto.LoginResponse;
import cl.duoc.users.exception.UnauthorizedException;
import cl.duoc.users.model.Usuario;
import cl.duoc.users.repository.UsuarioRepository;
import cl.duoc.users.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El login de la EP2 comparaba credenciales fijas del application.yml y no emitia
 * token. Estos tests fijan el comportamiento nuevo: busqueda en base de datos,
 * verificacion con BCrypt y emision de JWT.
 */
class AuthServiceImplTest {

    private static final String PASSWORD = "admin123";

    private UsuarioRepository usuarioRepository;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        JwtService jwtService = new JwtService(
                "secreto-de-pruebas-con-al-menos-32-bytes-de-longitud", 3_600_000L, "smartlogix-users");
        authService = new AuthServiceImpl(usuarioRepository, passwordEncoder, jwtService);
    }

    private Usuario usuarioActivo() {
        return Usuario.builder()
                .id(1L)
                .nombre("Administrador SmartLogix")
                .email("admin@smartlogix.cl")
                .rut("11111111-1")
                .password(new BCryptPasswordEncoder().encode(PASSWORD))
                .rol("ADMIN")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Credenciales correctas devuelven un token Bearer con el rol del usuario")
    void loginCorrectoDevuelveToken() {
        when(usuarioRepository.findByEmailIgnoreCase("admin@smartlogix.cl"))
                .thenReturn(Optional.of(usuarioActivo()));

        LoginResponse respuesta = authService.login(
                new LoginRequest("admin@smartlogix.cl", PASSWORD));

        assertEquals("Login exitoso", respuesta.mensaje());
        assertEquals("admin@smartlogix.cl", respuesta.usuario());
        assertEquals("ADMIN", respuesta.rol());
        assertEquals("Bearer", respuesta.tipo());
        assertEquals(3600L, respuesta.expiraEnSegundos());
        assertNotNull(respuesta.token());
        assertEquals(3, respuesta.token().split("\\.").length);
    }

    @Test
    @DisplayName("Password incorrecta responde 401 sin revelar si el usuario existe")
    void passwordIncorrectaRechaza() {
        when(usuarioRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(Optional.of(usuarioActivo()));

        var ex = assertThrows(UnauthorizedException.class, () -> authService.login(
                new LoginRequest("admin@smartlogix.cl", "password-equivocada")));
        assertEquals("Usuario o password incorrectos", ex.getMessage());
    }

    @Test
    @DisplayName("Usuario inexistente responde el mismo 401 generico")
    void usuarioInexistenteRechaza() {
        when(usuarioRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        var ex = assertThrows(UnauthorizedException.class, () -> authService.login(
                new LoginRequest("fantasma@smartlogix.cl", PASSWORD)));
        assertEquals("Usuario o password incorrectos", ex.getMessage());
    }

    @Test
    @DisplayName("Un usuario dado de baja no puede obtener token aunque la password sea correcta")
    void usuarioInactivoRechaza() {
        Usuario inactivo = usuarioActivo();
        inactivo.setActive(false);
        when(usuarioRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(inactivo));

        var ex = assertThrows(UnauthorizedException.class, () -> authService.login(
                new LoginRequest("admin@smartlogix.cl", PASSWORD)));
        assertEquals("El usuario esta desactivado", ex.getMessage());
    }
}
