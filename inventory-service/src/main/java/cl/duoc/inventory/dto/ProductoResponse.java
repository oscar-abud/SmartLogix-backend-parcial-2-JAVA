package cl.duoc.inventory.dto;

import cl.duoc.inventory.model.Producto;

public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        String descripcion,
        Double precio,
        Integer stock,
        CategoriaResponse categoria
) {
    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(p.getId(), p.getSku(), p.getNombre(), p.getDescripcion(),
                p.getPrecio(), p.getStock(), CategoriaResponse.from(p.getCategoria()));
    }
}
