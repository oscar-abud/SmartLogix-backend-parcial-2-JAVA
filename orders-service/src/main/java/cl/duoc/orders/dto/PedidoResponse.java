package cl.duoc.orders.dto;

import cl.duoc.orders.model.EstadoPedido;
import cl.duoc.orders.model.Pedido;

import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Long usuarioId,
        LocalDateTime fecha,
        Double montoTotal,
        EstadoPedido estado,
        List<DetallePedidoResponse> detalles
) {
    public static PedidoResponse from(Pedido p) {
        return new PedidoResponse(p.getId(), p.getUsuarioId(), p.getFecha(), p.getMontoTotal(),
                p.getEstado(), p.getDetalles().stream().map(DetallePedidoResponse::from).toList());
    }
}
