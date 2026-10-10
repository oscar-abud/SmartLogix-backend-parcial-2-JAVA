package cl.duoc.inventory.service.impl;

import cl.duoc.inventory.dto.BulkStockItem;
import cl.duoc.inventory.dto.ProductoRequest;
import cl.duoc.inventory.dto.ProductoResponse;
import cl.duoc.inventory.exception.BadRequestException;
import cl.duoc.inventory.exception.ConflictException;
import cl.duoc.inventory.exception.ResourceNotFoundException;
import cl.duoc.inventory.model.Categoria;
import cl.duoc.inventory.model.Producto;
import cl.duoc.inventory.repository.CategoriaRepository;
import cl.duoc.inventory.repository.ProductoRepository;
import cl.duoc.inventory.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponse> listar() {
        return productoRepository.findAllByOrderByIdAsc().stream()
                .map(ProductoResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorId(Long id) {
        return ProductoResponse.from(buscar(id));
    }

    @Override
    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        if (productoRepository.existsBySku(request.sku())) {
            throw new ConflictException("Ya existe un producto con el sku " + request.sku());
        }
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoria con id " + request.categoriaId() + " no encontrada"));

        Producto producto = Producto.builder()
                .sku(request.sku())
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .precio(request.precio())
                .stock(request.stock())
                .categoria(categoria)
                .build();
        return ProductoResponse.from(productoRepository.save(producto));
    }

    @Override
    @Transactional
    public ProductoResponse actualizarStock(Long id, Integer stock) {
        Producto producto = buscar(id);
        producto.setStock(stock);
        return ProductoResponse.from(producto);
    }

    /**
     * Operacion "todo o nada": si un item es invalido o no existe, se revierte toda la transaccion.
     */
    @Override
    @Transactional
    public List<ProductoResponse> actualizarStockMasivo(List<BulkStockItem> items) {
        validarItems(items);
        List<ProductoResponse> actualizados = new ArrayList<>();
        for (BulkStockItem item : items) {
            Producto producto = buscar(item.id());
            producto.setStock(item.newStock());
            actualizados.add(ProductoResponse.from(producto));
        }
        return actualizados;
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto con id " + id + " no encontrado"));
        productoRepository.delete(producto);
    }

    private Producto buscar(Long id) {
        return productoRepository.findWithCategoriaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto con id " + id + " no encontrado"));
    }

    private void validarItems(List<BulkStockItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("La lista de productos a actualizar no puede estar vacia");
        }
        for (int i = 0; i < items.size(); i++) {
            BulkStockItem item = items.get(i);
            if (item == null || item.id() == null || item.newStock() == null) {
                throw new BadRequestException("El elemento [" + i + "] debe incluir los campos id y newStock");
            }
            if (item.newStock() < 0) {
                throw new BadRequestException("El elemento [" + i + "] tiene un stock negativo: " + item.newStock());
            }
        }
    }
}
