package cl.duoc.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el JWT de la cabecera "Authorization: Bearer <token>" y, si es valido,
 * deja autenticado al usuario en el SecurityContext con la autoridad ROLE_<rol>.
 *
 * Si no hay token o el token es invalido NO corta la peticion aqui: deja el contexto
 * vacio y es la cadena de autorizacion la que responde 401 via JwtAuthEntryPoint.
 * Asi los endpoints publicos (login, health) siguen funcionando.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenParser jwtTokenParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtTokenParser.parse(token);
                String email = claims.getSubject();
                String rol = claims.get("rol", String.class);

                var authority = new SimpleGrantedAuthority("ROLE_" + rol);
                var authentication = new UsernamePasswordAuthenticationToken(
                        email, null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                request.setAttribute("jwt.uid", claims.get("uid"));
                request.setAttribute("jwt.rol", rol);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Token JWT rechazado en {}: {}", request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
