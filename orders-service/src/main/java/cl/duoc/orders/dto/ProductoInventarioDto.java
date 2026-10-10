package cl.duoc.orders.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subconjunto de la respuesta de inventory-service (GET /api/products/{id}) que necesita este servicio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoInventarioDto(Long id, String sku, String nombre, Double precio, Integer stock) {
}
