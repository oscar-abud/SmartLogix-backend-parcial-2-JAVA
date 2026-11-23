package cl.duoc.shipping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mensaje que orders-service publica en SQS. Debe mantenerse alineado con
 * cl.duoc.orders.messaging.OrderCreatedEvent del microservicio de pedidos.
 *
 * Se ignoran las propiedades desconocidas para que anadir campos en el productor
 * no rompa a este consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        Long pedidoId,
        Long usuarioId,
        Double montoTotal,
        String estado,
        String fecha,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productoId, Integer cantidad, Double precioUnitario) {
    }
}
