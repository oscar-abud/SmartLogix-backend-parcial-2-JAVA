package cl.duoc.orders.dto;

import cl.duoc.orders.model.DetallePedido;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record DetallePedidoResponse(
        Long id,
        Long productoId,
        Integer cantidad,
        Double precioUnitario,
        Double subtotal
) {
    public static DetallePedidoResponse from(DetallePedido d) {
        double subtotal = BigDecimal.valueOf(d.getPrecioUnitario())
                .multiply(BigDecimal.valueOf(d.getCantidad()))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return new DetallePedidoResponse(d.getId(), d.getProductoId(), d.getCantidad(),
                d.getPrecioUnitario(), subtotal);
    }
}
