package cl.duoc.inventory.dto;

/**
 * Par {id, newStock} para la actualizacion masiva de stock.
 * Se valida en la capa de servicio porque llega como arreglo JSON.
 */
public record BulkStockItem(Long id, Integer newStock) {
}
