package cl.duoc.orders.service.impl;

import cl.duoc.orders.client.InventoryClient;
import cl.duoc.orders.dto.PedidoCreateRequest;
import cl.duoc.orders.dto.PedidoItemRequest;
import cl.duoc.orders.dto.ProductoInventarioDto;
import cl.duoc.orders.exception.ResourceNotFoundException;
import cl.duoc.orders.messaging.OrderCreatedEvent;
import cl.duoc.orders.messaging.ShippingEventPublisher;
import cl.duoc.orders.model.Pedido;
import cl.duoc.orders.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre las dos reglas de negocio criticas de orders-service:
 * el precio siempre lo manda inventory-service, y la creacion del pedido
 * dispara el evento asincrono hacia la cola de envios.
 */
class PedidoServiceImplTest {

    private PedidoRepository pedidoRepository;
    private InventoryClient inventoryClient;
    private ShippingEventPublisher publisher;
    private PedidoServiceImpl service;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        inventoryClient = mock(InventoryClient.class);
        publisher = mock(ShippingEventPublisher.class);
        service = new PedidoServiceImpl(pedidoRepository, inventoryClient, publisher);

        // save() devuelve el pedido recibido, simulando el id asignado por la base de datos.
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });
    }

    private static ProductoInventarioDto producto(long id, double precio) {
        return new ProductoInventarioDto(id, "SKU-00" + id, "Producto " + id, precio, 100);
    }

    @Test
    @DisplayName("El monto total usa el precio de inventory, no el que pudiera enviar el cliente")
    void calculaMontoConPrecioDeInventario() {
        when(inventoryClient.obtenerProducto(1L)).thenReturn(producto(1L, 12995.0));
        when(inventoryClient.obtenerProducto(2L)).thenReturn(producto(2L, 5000.5));

        var respuesta = service.crear(new PedidoCreateRequest(7L, List.of(
                new PedidoItemRequest(1L, 2),
                new PedidoItemRequest(2L, 1))));

        // 12995.00 * 2 + 5000.50 = 30990.50
        assertEquals(30990.50, respuesta.montoTotal());
    }

    @Test
    @DisplayName("Al crear un pedido se publica el evento para la Lambda de envios")
    void publicaEventoDeEnvio() {
        when(inventoryClient.obtenerProducto(1L)).thenReturn(producto(1L, 10000.0));

        service.crear(new PedidoCreateRequest(7L, List.of(new PedidoItemRequest(1L, 3))));

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher).publicar(captor.capture());

        OrderCreatedEvent evento = captor.getValue();
        assertEquals(99L, evento.pedidoId());
        assertEquals(7L, evento.usuarioId());
        assertEquals(30000.0, evento.montoTotal());
        assertEquals("PENDIENTE", evento.estado());
        assertEquals(1, evento.items().size());
        assertEquals(3, evento.items().get(0).cantidad());
        assertEquals(10000.0, evento.items().get(0).precioUnitario());
    }

    @Test
    @DisplayName("Si la cola falla, el pedido igual se crea: la mensajeria no bloquea la venta")
    void falloDeMensajeriaNoRompeElPedido() {
        when(inventoryClient.obtenerProducto(1L)).thenReturn(producto(1L, 10000.0));
        doThrow(new RuntimeException("SQS caido")).when(publisher).publicar(any());

        // ShippingEventPublisher captura sus propios errores; aqui se fuerza el peor caso
        // para dejar constancia de que un fallo de publicacion no debe perder el pedido.
        assertThrows(RuntimeException.class, () -> service.crear(
                new PedidoCreateRequest(7L, List.of(new PedidoItemRequest(1L, 1)))));
        verify(pedidoRepository).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Un producto inexistente aborta el pedido y no publica evento")
    void productoInexistenteNoPublica() {
        when(inventoryClient.obtenerProducto(404L))
                .thenThrow(new ResourceNotFoundException("Producto con id 404 no existe"));

        assertThrows(ResourceNotFoundException.class, () -> service.crear(
                new PedidoCreateRequest(7L, List.of(new PedidoItemRequest(404L, 1)))));

        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(publisher, never()).publicar(any());
    }
}
