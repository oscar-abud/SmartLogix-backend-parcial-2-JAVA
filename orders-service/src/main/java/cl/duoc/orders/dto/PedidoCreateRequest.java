package cl.duoc.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * El cliente solo envia el usuario y los items (productoId + cantidad).
 * Precios y monto total se calculan en el backend.
 */
public record PedidoCreateRequest(
        @NotNull(message = "El usuarioId es obligatorio")
        Long usuarioId,

        @NotEmpty(message = "El pedido debe tener al menos un item")
        List<@Valid @NotNull(message = "Los items no pueden ser nulos") PedidoItemRequest> items
) {
}
