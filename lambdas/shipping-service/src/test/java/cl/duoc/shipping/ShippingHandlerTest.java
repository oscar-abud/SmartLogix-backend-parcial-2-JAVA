package cl.duoc.shipping;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingHandlerTest {

    /** Doble de prueba del repositorio: evita depender de un Atlas real en el pipeline. */
    private static class RepositorioEnMemoria implements EnvioRepository {
        final List<Document> guardados = new ArrayList<>();

        @Override
        public boolean guardarSiNoExiste(Document envio) {
            boolean existe = guardados.stream()
                    .anyMatch(d -> d.get("pedidoId").equals(envio.get("pedidoId")));
            if (existe) {
                return false;
            }
            guardados.add(envio);
            return true;
        }
    }

    private static SQSEvent eventoCon(String... cuerpos) {
        SQSEvent evento = new SQSEvent();
        List<SQSEvent.SQSMessage> mensajes = new ArrayList<>();
        for (String cuerpo : cuerpos) {
            SQSEvent.SQSMessage mensaje = new SQSEvent.SQSMessage();
            mensaje.setBody(cuerpo);
            mensajes.add(mensaje);
        }
        evento.setRecords(mensajes);
        return evento;
    }

    private static final String PEDIDO_JSON = """
            {"pedidoId":7,"usuarioId":1,"montoTotal":25990.0,"estado":"PENDIENTE",
             "fecha":"2026-11-22T10:15:30",
             "items":[{"productoId":1,"cantidad":2,"precioUnitario":12995.0}]}
            """;

    @Test
    @DisplayName("Un evento de pedido genera un envio EN_PREPARACION con tracking")
    void creaEnvioDesdeEvento() {
        var repo = new RepositorioEnMemoria();
        new ShippingHandler(repo).handleRequest(eventoCon(PEDIDO_JSON), null);

        assertEquals(1, repo.guardados.size());
        Document envio = repo.guardados.get(0);
        assertEquals(7L, envio.get("pedidoId"));
        assertEquals(1L, envio.get("usuarioId"));
        assertEquals("EN_PREPARACION", envio.getString("estado"));
        assertEquals(25990.0, envio.getDouble("montoTotal"));
        assertTrue(envio.getString("trackingCode").startsWith("SL-7-"));

        @SuppressWarnings("unchecked")
        List<Document> items = (List<Document>) envio.get("items");
        assertEquals(1, items.size());
        assertEquals(2, items.get(0).getInteger("cantidad"));
    }

    @Test
    @DisplayName("SQS entrega al menos una vez: el mismo pedido no duplica el envio")
    void esIdempotente() {
        var repo = new RepositorioEnMemoria();
        new ShippingHandler(repo).handleRequest(eventoCon(PEDIDO_JSON, PEDIDO_JSON), null);

        assertEquals(1, repo.guardados.size());
    }

    @Test
    @DisplayName("Un mensaje con JSON invalido se descarta sin romper el lote")
    void jsonInvalidoNoRompeElLote() {
        var repo = new RepositorioEnMemoria();
        new ShippingHandler(repo).handleRequest(eventoCon("{esto no es json", PEDIDO_JSON), null);

        assertEquals(1, repo.guardados.size());
        assertEquals(7L, repo.guardados.get(0).get("pedidoId"));
    }

    @Test
    @DisplayName("Un evento sin pedidoId se descarta")
    void sinPedidoIdSeDescarta() {
        var repo = new RepositorioEnMemoria();
        new ShippingHandler(repo).handleRequest(eventoCon("{\"usuarioId\":1}"), null);

        assertTrue(repo.guardados.isEmpty());
    }

    @Test
    @DisplayName("Los campos desconocidos del productor no rompen al consumidor")
    void ignoraCamposDesconocidos() {
        var repo = new RepositorioEnMemoria();
        new ShippingHandler(repo).handleRequest(
                eventoCon("{\"pedidoId\":9,\"campoNuevo\":\"x\",\"items\":[]}"), null);

        assertEquals(1, repo.guardados.size());
        assertEquals(9L, repo.guardados.get(0).get("pedidoId"));
    }
}
