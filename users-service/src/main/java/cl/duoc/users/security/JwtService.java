package cl.duoc.users.security;

import cl.duoc.users.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emisor de los JWT del sistema. Solo users-service firma tokens;
 * inventory-service, orders-service y la Lambda authorizer unicamente los validan.
 *
 * Algoritmo HS256 con secreto compartido (jwt.secret / variable de entorno JWT_SECRET).
 * Claims emitidos:
 *   sub    email del usuario
 *   uid    id del usuario en la tabla "usuarios"
 *   nombre nombre para mostrar
 *   rol    ADMIN u OPERADOR
 *   iss    smartlogix-users
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs,
                      @Value("${jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(expirationMs)))
                // Se fija HS256 de forma explicita. Sin indicarlo, jjwt elige el algoritmo
                // mas fuerte que admita la clave (HS384 o HS512 segun su longitud), y el
                // sistema dejaria de coincidir con lo documentado.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Segundos de vida del token, para informarlo en la respuesta del login. */
    public long getExpiracionSegundos() {
        return expirationMs / 1000;
    }
}
