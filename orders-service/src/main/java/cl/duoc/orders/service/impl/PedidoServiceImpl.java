package cl.duoc.orders.service.impl;

import cl.duoc.orders.client.InventoryClient;
import cl.duoc.orders.dto.PedidoCreateRequest;
import cl.duoc.orders.dto.PedidoItemRequest;
import cl.duoc.orders.dto.PedidoResponse;
import cl.duoc.orders.dto.ProductoInventarioDto;
import cl.duoc.orders.exception.ResourceNotFoundException;
import cl.duoc.orders.exception.ServiceUnavailableException;
import cl.duoc.orders.model.DetallePedido;
import cl.duoc.orders.model.EstadoPedido;
import cl.duoc.orders.model.Pedido;
import cl.duoc.orders.repository.PedidoRepository;
import cl.duoc.orders.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {

    private final PedidoRepository pedidoRepository;
    private final InventoryClient inventoryClient;

    @Override
    @Transactional(readOnly = true)
    public PedidoResponse obtenerPorId(Long id) {
        return PedidoResponse.from(buscar(id));
    }

    /**
     * El monto total se calcula en el backend: el precio unitario de cada item se obtiene
     * desde inventory-service y se ignora cualquier precio que pudiera enviar el cliente.
     * Las llamadas HTTP se hacen fuera de la transaccion; el guardado (pedido + detalles en
     * cascada) ocurre en una sola transaccion dentro de save().
     */
    @Override
    public PedidoResponse crear(PedidoCreateRequest request) {
        Pedido pedido = Pedido.builder()
                .usuarioId(request.usuarioId())
                .estado(EstadoPedido.PENDIENTE)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (PedidoItemRequest item : request.items()) {
            ProductoInventarioDto producto = inventoryClient.obtenerProducto(item.productoId());
            if (producto == null || producto.precio() == null) {
                throw new ServiceUnavailableException(
                        "inventory-service no entrego el precio del producto " + item.productoId());
            }

            pedido.addDetalle(DetallePedido.builder()
                    .productoId(item.productoId())
                    .cantidad(item.cantidad())
                    .precioUnitario(producto.precio())
                    .build());

            BigDecimal subtotal = BigDecimal.valueOf(producto.precio())
                    .multiply(BigDecimal.valueOf(item.cantidad()));
            total = total.add(subtotal);
        }
        pedido.setMontoTotal(total.setScale(2, RoundingMode.HALF_UP).doubleValue());

        return PedidoResponse.from(pedidoRepository.save(pedido));
    }

    @Override
    @Transactional
    public PedidoResponse actualizarEstado(Long id, EstadoPedido estado) {
        Pedido pedido = buscar(id);
        pedido.setEstado(estado);
        return PedidoResponse.from(pedido);
    }

    private Pedido buscar(Long id) {
        return pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido con id " + id + " no encontrado"));
    }
}
