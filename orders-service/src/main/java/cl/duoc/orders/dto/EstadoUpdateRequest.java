package cl.duoc.orders.dto;

import cl.duoc.orders.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record EstadoUpdateRequest(
        @NotNull(message = "El estado es obligatorio (PENDIENTE, PAGADO o CANCELADO)")
        EstadoPedido estado
) {
}
