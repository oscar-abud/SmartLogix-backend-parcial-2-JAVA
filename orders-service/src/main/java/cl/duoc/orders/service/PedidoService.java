package cl.duoc.orders.service;

import cl.duoc.orders.dto.PedidoCreateRequest;
import cl.duoc.orders.dto.PedidoResponse;
import cl.duoc.orders.model.EstadoPedido;

public interface PedidoService {

    PedidoResponse obtenerPorId(Long id);

    PedidoResponse crear(PedidoCreateRequest request);

    PedidoResponse actualizarEstado(Long id, EstadoPedido estado);
}
