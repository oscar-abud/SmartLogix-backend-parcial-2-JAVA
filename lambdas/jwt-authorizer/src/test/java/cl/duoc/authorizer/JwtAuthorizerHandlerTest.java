package cl.duoc.authorizer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthorizerHandlerTest {

    private static final String SECRETO = "secreto-de-pruebas-con-al-menos-32-bytes-de-longitud";
    private static final String OTRO_SECRETO = "otro-secreto-distinto-con-32-bytes-o-mas-de-largo";
    private static final String ISSUER = "smartlogix-users";
    private static final String METHOD_ARN =
            "arn:aws:execute-api:us-east-1:123456789012:abc123xyz/prod/GET/api/products";

    private final JwtAuthorizerHandler handler = new JwtAuthorizerHandler(SECRETO, ISSUER);

    private static String token(String secreto, String issuer, long vidaMs, String rol) {
        SecretKey key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(issuer)
                .subject("admin@smartlogix.cl")
                .claim("rol", rol)
                .claim("uid", 1L)
                .issuedAt(new Date(ahora - 1000))
                .expiration(new Date(ahora + vidaMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private Map<String, Object> evento(String cabecera) {
        return Map.of("type", "TOKEN",
                "authorizationToken", cabecera,
                "methodArn", METHOD_ARN);
    }

    @Test
    @DisplayName("Un token valido devuelve una politica Allow y expone el rol en el contexto")
    void tokenValidoAutoriza() {
        var respuesta = handler.handleRequest(
                evento("Bearer " + token(SECRETO, ISSUER, 60_000, "ADMIN")), null);

        assertEquals("admin@smartlogix.cl", respuesta.get("principalId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contexto = (Map<String, Object>) respuesta.get("context");
        assertEquals("ADMIN", contexto.get("rol"));
        assertEquals("admin@smartlogix.cl", contexto.get("email"));
        assertEquals("1", contexto.get("uid"));

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) respuesta.get("policyDocument");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> statements = (List<Map<String, Object>>) policy.get("Statement");
        assertEquals("Allow", statements.get(0).get("Effect"));
    }

    @Test
    @DisplayName("La politica cubre toda la API para que la cache del authorizer no provoque 403")
    void politicaUsaComodin() {
        var respuesta = handler.handleRequest(
                evento("Bearer " + token(SECRETO, ISSUER, 60_000, "ADMIN")), null);

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) respuesta.get("policyDocument");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> statements = (List<Map<String, Object>>) policy.get("Statement");
        String recurso = (String) statements.get(0).get("Resource");

        assertEquals("arn:aws:execute-api:us-east-1:123456789012:abc123xyz/prod/*/*", recurso);
        assertTrue(recurso.endsWith("/*/*"));
    }

    @Test
    @DisplayName("Sin cabecera Bearer se responde Unauthorized")
    void sinBearerRechaza() {
        var ex = assertThrows(RuntimeException.class,
                () -> handler.handleRequest(evento("token-suelto-sin-prefijo"), null));
        assertEquals("Unauthorized", ex.getMessage());
    }

    @Test
    @DisplayName("Un token firmado con otro secreto se rechaza")
    void firmaAjenaRechaza() {
        var ex = assertThrows(RuntimeException.class, () -> handler.handleRequest(
                evento("Bearer " + token(OTRO_SECRETO, ISSUER, 60_000, "ADMIN")), null));
        assertEquals("Unauthorized", ex.getMessage());
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void tokenExpiradoRechaza() {
        var ex = assertThrows(RuntimeException.class, () -> handler.handleRequest(
                evento("Bearer " + token(SECRETO, ISSUER, -60_000, "ADMIN")), null));
        assertEquals("Unauthorized", ex.getMessage());
    }

    @Test
    @DisplayName("Un token de otro emisor se rechaza")
    void issuerDistintoRechaza() {
        var ex = assertThrows(RuntimeException.class, () -> handler.handleRequest(
                evento("Bearer " + token(SECRETO, "otro-emisor", 60_000, "ADMIN")), null));
        assertEquals("Unauthorized", ex.getMessage());
    }

    @Test
    @DisplayName("Un methodArn con formato inesperado degrada a comodin total")
    void arnInvalidoDevuelveComodin() {
        assertEquals("*", JwtAuthorizerHandler.wildcardArn(null));
        assertEquals("*", JwtAuthorizerHandler.wildcardArn("no-es-un-arn"));
    }
}
