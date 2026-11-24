package cl.duoc.inventory.controller;

import cl.duoc.inventory.dto.CategoriaResponse;
import cl.duoc.inventory.dto.ProductoResponse;
import cl.duoc.inventory.security.JwtAuthEntryPoint;
import cl.duoc.inventory.security.JwtAuthenticationFilter;
import cl.duoc.inventory.security.JwtTokenParser;
import cl.duoc.inventory.security.SecurityConfig;
import cl.duoc.inventory.service.ProductoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica las reglas de autorizacion que sustituyen a la antigua cabecera X-User-Role:
 * lectura para cualquier usuario autenticado, escritura reservada a ADMIN,
 * y 401 cuando no hay token.
 *
 * Se importa SecurityConfig a proposito; sin ella el slice de @WebMvcTest no
 * cargaria la cadena de filtros y los tests pasarian sin probar nada.
 */
@WebMvcTest(ProductoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthEntryPoint.class})
class ProductoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService productoService;

    @MockBean
    private JwtTokenParser jwtTokenParser;

    private static ProductoResponse producto() {
        return new ProductoResponse(1L, "SKU-001", "Teclado mecanico", "RGB", 29990.0, 10,
                new CategoriaResponse(1L, "Electronica", "Equipos y dispositivos"));
    }

    @Test
    @DisplayName("Sin token, GET /api/products responde 401")
    void listarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Un OPERADOR autenticado puede leer el catalogo")
    void operadorPuedeListar() throws Exception {
        when(productoService.listar()).thenReturn(List.of(producto()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"));
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Un OPERADOR no puede crear productos: 403")
    void operadorNoPuedeCrear() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-002","nombre":"Mouse","descripcion":"",
                                 "precio":9990,"stock":5,"categoriaId":1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Un OPERADOR no puede eliminar productos: 403")
    void operadorNoPuedeEliminar() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un ADMIN si puede eliminar productos: 204")
    void adminPuedeEliminar() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/products/{id} sigue devolviendo el producto para orders-service")
    void adminObtienePorId() throws Exception {
        when(productoService.obtenerPorId(anyLong())).thenReturn(producto());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(29990.0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un ADMIN puede crear productos: 201")
    void adminPuedeCrear() throws Exception {
        when(productoService.crear(any())).thenReturn(producto());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-001","nombre":"Teclado mecanico","descripcion":"RGB",
                                 "precio":29990,"stock":10,"categoriaId":1}
                                """))
                .andExpect(status().isCreated());
    }
}
