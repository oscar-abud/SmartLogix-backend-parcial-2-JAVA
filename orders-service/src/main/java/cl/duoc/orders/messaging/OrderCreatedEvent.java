package cl.duoc.orders.messaging;

import cl.duoc.orders.model.DetallePedido;
import cl.duoc.orders.model.Pedido;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mensaje que orders-service publica en SQS cuando se crea un pedido.
 * Lo consume la funcion Lambda "shipping", que genera el envio en MongoDB Atlas.
 *
 * Es un contrato entre dos servicios: cualquier cambio de campos obliga a
 * actualizar tambien lambdas/shipping-service.
 */
public record OrderCreatedEvent(
        Long pedidoId,
        Long usuarioId,
        Double montoTotal,
        String estado,
        LocalDateTime fecha,
        List<Item> items
) {

    public record Item(Long productoId, Integer cantidad, Double precioUnitario) {
    }

    public static OrderCreatedEvent from(Pedido pedido) {
        List<Item> items = pedido.getDetalles().stream()
                .map(OrderCreatedEvent::toItem)
                .toList();
        return new OrderCreatedEvent(
                pedido.getId(),
                pedido.getUsuarioId(),
                pedido.getMontoTotal(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null,
                pedido.getFecha(),
                items);
    }

    private static Item toItem(DetallePedido detalle) {
        return new Item(detalle.getProductoId(), detalle.getCantidad(), detalle.getPrecioUnitario());
    }
}
