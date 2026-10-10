package cl.duoc.inventory.service;

import cl.duoc.inventory.dto.BulkStockItem;
import cl.duoc.inventory.dto.ProductoRequest;
import cl.duoc.inventory.dto.ProductoResponse;

import java.util.List;

public interface ProductoService {

    List<ProductoResponse> listar();

    ProductoResponse obtenerPorId(Long id);

    ProductoResponse crear(ProductoRequest request);

    ProductoResponse actualizarStock(Long id, Integer stock);

    List<ProductoResponse> actualizarStockMasivo(List<BulkStockItem> items);

    void eliminar(Long id);
}
