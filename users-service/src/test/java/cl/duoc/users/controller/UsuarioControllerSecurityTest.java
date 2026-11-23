package cl.duoc.users.controller;

import cl.duoc.users.dto.UsuarioResponse;
import cl.duoc.users.exception.GlobalExceptionHandler;
import cl.duoc.users.security.JwtAuthEntryPoint;
import cl.duoc.users.security.JwtAuthenticationFilter;
import cl.duoc.users.security.JwtTokenParser;
import cl.duoc.users.security.SecurityConfig;
import cl.duoc.users.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las operaciones de ADMIN se protegen con PreAuthorize, es decir con seguridad a
 * nivel de metodo, no en la cadena de filtros.
 *
 * Eso tiene una consecuencia facil de pasar por alto: la AccessDeniedException se
 * lanza DENTRO del servlet y la recoge GlobalExceptionHandler. Si ese manejador no
 * la trata de forma explicita, cae en el handler generico de Exception y el cliente
 * recibe un 500 en lugar de un 403. Estos tests fijan ese comportamiento.
 */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthEntryPoint.class,
        GlobalExceptionHandler.class})
class UsuarioControllerSecurityTest {

    private static final String USUARIO_JSON = """
            {"nombre":"Camila Rojas","email":"camila.rojas@smartlogix.cl","rut":"19876543-2",
             "telefono":"+56912345678","password":"camila123","rol":"OPERADOR"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private JwtTokenParser jwtTokenParser;

    @MockBean
    private PasswordEncoder passwordEncoder;

    private static UsuarioResponse usuario() {
        return new UsuarioResponse(5L, "Camila Rojas", "camila.rojas@smartlogix.cl",
                "19876543-2", "+56912345678", "OPERADOR", true);
    }

    @Test
    @DisplayName("Sin token, GET /api/users responde 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La cabecera X-User-Role de la EP2 ya no concede permisos")
    void cabeceraDeRolFalsificadaNoSirve() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USUARIO_JSON))
                .andExpect(status().isUnauthorized());

        verify(usuarioService, never()).crear(any());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Un OPERADOR que intenta crear un usuario recibe 403, no 500")
    void operadorRecibe403NoError500() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USUARIO_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(usuarioService, never()).crear(any());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Un OPERADOR tampoco puede eliminar ni desactivar usuarios")
    void operadorNoPuedeEliminar() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Un OPERADOR si puede consultar usuarios")
    void operadorPuedeLeer() throws Exception {
        when(usuarioService.listarActivos()).thenReturn(List.of(usuario()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("camila.rojas@smartlogix.cl"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un ADMIN si puede crear usuarios")
    void adminPuedeCrear() throws Exception {
        when(usuarioService.crear(any())).thenReturn(usuario());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USUARIO_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un ADMIN si puede eliminar usuarios")
    void adminPuedeEliminar() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(usuarioService).eliminar(anyLong());
    }
}
