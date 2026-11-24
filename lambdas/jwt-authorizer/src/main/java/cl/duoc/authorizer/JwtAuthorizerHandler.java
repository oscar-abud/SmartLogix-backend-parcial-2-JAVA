package cl.duoc.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda Authorizer de tipo TOKEN para el API Gateway REST de SmartLogix.
 *
 * Es el "validador externo" del JWT: el API Gateway lo invoca ANTES de enrutar la
 * peticion hacia los microservicios en EC2, de modo que un token invalido nunca
 * llega al backend.
 *
 * Entrada (evento TOKEN):
 *   { "type": "TOKEN", "authorizationToken": "Bearer eyJ...", "methodArn": "arn:aws:execute-api:..." }
 *
 * Salida: un documento de politica IAM. Si el token no es valido se lanza una
 * excepcion cuyo mensaje es exactamente "Unauthorized", que es como API Gateway
 * sabe que debe responder 401 (cualquier otro mensaje produce un 500).
 *
 * Variables de entorno:
 *   JWT_SECRET  secreto HS256 compartido con users-service (obligatoria)
 *   JWT_ISSUER  emisor esperado, por defecto "smartlogix-users"
 */
public class JwtAuthorizerHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED = "Unauthorized";

    private final SecretKey key;
    private final String issuer;

    public JwtAuthorizerHandler() {
        this(System.getenv("JWT_SECRET"),
                System.getenv().getOrDefault("JWT_ISSUER", "smartlogix-users"));
    }

    /** Constructor visible para los tests. */
    JwtAuthorizerHandler(String secret, String issuer) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String header = event == null ? null : (String) event.get("authorizationToken");
        String methodArn = event == null ? null : (String) event.get("methodArn");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            log(context, "Peticion sin cabecera Authorization Bearer");
            throw new RuntimeException(UNAUTHORIZED);
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log(context, "Token rechazado: " + ex.getMessage());
            throw new RuntimeException(UNAUTHORIZED);
        }

        String email = claims.getSubject();
        String rol = claims.get("rol", String.class);
        Object uid = claims.get("uid");

        log(context, "Token valido para " + email + " (rol " + rol + ")");

        // El contexto viaja al backend como cabeceras, segun el mapeo del API Gateway.
        Map<String, Object> authContext = new HashMap<>();
        authContext.put("email", email == null ? "" : email);
        authContext.put("rol", rol == null ? "" : rol);
        authContext.put("uid", uid == null ? "" : String.valueOf(uid));

        return allowPolicy(email, methodArn, authContext);
    }

    /**
     * Construye una politica Allow sobre TODOS los metodos de la API, no solo sobre
     * el methodArn recibido.
     *
     * Es deliberado: con AuthorizerResultTtlInSeconds activo, API Gateway cachea la
     * politica por token. Si el recurso fuera el methodArn concreto, la primera
     * llamada autorizaria solo ese endpoint y las siguientes a otras rutas darian 403
     * durante toda la vida de la cache.
     */
    private Map<String, Object> allowPolicy(String principalId, String methodArn,
                                            Map<String, Object> authContext) {
        Map<String, Object> statement = new HashMap<>();
        statement.put("Action", "execute-api:Invoke");
        statement.put("Effect", "Allow");
        statement.put("Resource", wildcardArn(methodArn));

        Map<String, Object> policyDocument = new HashMap<>();
        policyDocument.put("Version", "2012-10-17");
        policyDocument.put("Statement", List.of(statement));

        Map<String, Object> response = new HashMap<>();
        response.put("principalId", principalId == null ? "anonimo" : principalId);
        response.put("policyDocument", policyDocument);
        response.put("context", authContext);
        return response;
    }

    /**
     * Toma el methodArn recibido, por ejemplo
     * {@code arn:aws:execute-api:us-east-1:123:abc123/prod/GET/api/products},
     * y conserva solo la API y el stage, sustituyendo el metodo y la ruta por comodines.
     * Devuelve {@code "*"} si el ARN no tiene el formato esperado.
     */
    static String wildcardArn(String methodArn) {
        if (methodArn == null || methodArn.isBlank()) {
            return "*";
        }
        String[] partes = methodArn.split(":", 6);
        if (partes.length < 6) {
            return "*";
        }
        String[] apiPartes = partes[5].split("/");
        if (apiPartes.length < 2) {
            return "*";
        }
        String apiId = apiPartes[0];
        String stage = apiPartes[1];
        return String.join(":", partes[0], partes[1], partes[2], partes[3], partes[4],
                apiId + "/" + stage + "/*/*");
    }

    private void log(Context context, String mensaje) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(mensaje);
        }
    }
}
