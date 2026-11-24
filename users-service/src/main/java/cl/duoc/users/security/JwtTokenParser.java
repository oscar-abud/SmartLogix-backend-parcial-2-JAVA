package cl.duoc.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Valida la firma HS256, la expiracion y el emisor de un JWT.
 * El secreto es compartido por users-service (emisor), inventory-service, orders-service
 * y la Lambda authorizer que protege el API Gateway.
 */
@Component
public class JwtTokenParser {

    private final SecretKey key;
    private final String issuer;

    public JwtTokenParser(@Value("${jwt.secret}") String secret,
                          @Value("${jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /**
     * @return los claims del token si es valido
     * @throws JwtException si la firma no corresponde, el token expiro o el emisor no coincide
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
