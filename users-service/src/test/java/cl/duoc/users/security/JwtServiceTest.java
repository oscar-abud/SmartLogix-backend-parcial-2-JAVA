package cl.duoc.users.security;

import cl.duoc.users.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El contrato de estos tests es el mismo que valida la Lambda authorizer del
 * API Gateway, por eso se comprueban firma, expiracion y emisor.
 */
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-al-menos-32-bytes-de-longitud";
    private static final String OTRO_SECRETO = "otro-secreto-distinto-con-32-bytes-o-mas-de-largo";
    private static final String ISSUER = "smartlogix-users";

    private final JwtService jwtService = new JwtService(SECRETO, 3_600_000L, ISSUER);
    private final JwtTokenParser parser = new JwtTokenParser(SECRETO, ISSUER);

    private static Usuario usuario() {
        return Usuario.builder()
                .id(42L)
                .nombre("Administrador SmartLogix")
                .email("admin@smartlogix.cl")
                .rut("11111111-1")
                .password("irrelevante")
                .rol("ADMIN")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("El token emitido se valida y conserva los claims del usuario")
    void generaYValida() {
        String token = jwtService.generarToken(usuario());

        Claims claims = parser.parse(token);
        assertEquals("admin@smartlogix.cl", claims.getSubject());
        assertEquals("ADMIN", claims.get("rol", String.class));
        assertEquals(42, claims.get("uid", Integer.class));
        assertEquals(ISSUER, claims.getIssuer());
    }

    @Test
    @DisplayName("El token se firma con HS256, el algoritmo que documenta el sistema")
    void usaHs256() {
        String cabecera = jwtService.generarToken(usuario()).split("\\.")[0];
        String json = new String(java.util.Base64.getUrlDecoder().decode(cabecera),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(json.contains("HS256"), "Se esperaba alg HS256 pero la cabecera fue " + json);
    }

    @Test
    @DisplayName("Un token firmado con otro secreto se rechaza")
    void rechazaFirmaAjena() {
        String tokenAjeno = new JwtService(OTRO_SECRETO, 3_600_000L, ISSUER).generarToken(usuario());

        assertThrows(JwtException.class, () -> parser.parse(tokenAjeno));
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void rechazaTokenExpirado() {
        String expirado = new JwtService(SECRETO, -1_000L, ISSUER).generarToken(usuario());

        assertThrows(ExpiredJwtException.class, () -> parser.parse(expirado));
    }

    @Test
    @DisplayName("Un token de otro emisor se rechaza")
    void rechazaOtroEmisor() {
        String otroEmisor = new JwtService(SECRETO, 3_600_000L, "emisor-falso").generarToken(usuario());

        assertThrows(JwtException.class, () -> parser.parse(otroEmisor));
    }

    @Test
    @DisplayName("Un texto cualquiera no es un token valido")
    void rechazaBasura() {
        assertThrows(RuntimeException.class, () -> parser.parse("esto.no.es-un-jwt"));
    }

    @Test
    @DisplayName("La expiracion se informa en segundos para la respuesta del login")
    void informaExpiracionEnSegundos() {
        assertEquals(3600L, jwtService.getExpiracionSegundos());
        assertTrue(jwtService.generarToken(usuario()).split("\\.").length == 3);
    }
}
