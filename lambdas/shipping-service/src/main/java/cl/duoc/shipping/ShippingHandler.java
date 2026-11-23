package cl.duoc.shipping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Microservicio de envios implementado como funcion serverless (FaaS).
 *
 * Es el consumidor del flujo asincrono: SQS lo invoca con los eventos que
 * orders-service publico al crear un pedido, y por cada uno genera el envio
 * correspondiente en MongoDB Atlas.
 *
 * Variables de entorno:
 *   MONGODB_URI  cadena de conexion de Atlas (obligatoria)
 *   MONGODB_DB   base de datos, por defecto "smartlogix"
 *
 * Semantica de errores: si el handler lanza una excepcion, SQS reintrega el lote.
 * Tras los reintentos configurados en la RedrivePolicy el mensaje acaba en la DLQ
 * smartlogix-shipping-dlq. Un mensaje con JSON invalido NO se reintenta (seria un
 * bucle infinito): se registra y se descarta.
 */
public class ShippingHandler implements RequestHandler<SQSEvent, Void> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final EnvioRepository repositorio;

    public ShippingHandler() {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno MONGODB_URI");
        }
        this.repositorio = new MongoEnvioRepository(uri, System.getenv("MONGODB_DB"));
    }

    /** Constructor visible para los tests. */
    ShippingHandler(EnvioRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        if (event == null || event.getRecords() == null) {
            return null;
        }
        for (SQSEvent.SQSMessage mensaje : event.getRecords()) {
            procesar(mensaje, context);
        }
        return null;
    }

    private void procesar(SQSEvent.SQSMessage mensaje, Context context) {
        OrderCreatedEvent evento;
        try {
            evento = MAPPER.readValue(mensaje.getBody(), OrderCreatedEvent.class);
        } catch (Exception ex) {
            // Mensaje corrupto: reintentarlo no lo va a arreglar.
            log(context, "Mensaje descartado por JSON invalido: " + ex.getMessage());
            return;
        }

        if (evento.pedidoId() == null) {
            log(context, "Mensaje descartado: no trae pedidoId");
            return;
        }

        Document envio = construirEnvio(evento);
        boolean insertado = repositorio.guardarSiNoExiste(envio);

        if (insertado) {
            log(context, "Envio creado para el pedido " + evento.pedidoId()
                    + " con tracking " + envio.getString("trackingCode"));
        } else {
            log(context, "El pedido " + evento.pedidoId()
                    + " ya tenia envio registrado, se ignora la reentrega");
        }
    }

    static Document construirEnvio(OrderCreatedEvent evento) {
        List<Document> items = new ArrayList<>();
        if (evento.items() != null) {
            for (OrderCreatedEvent.Item item : evento.items()) {
                items.add(new Document("productoId", item.productoId())
                        .append("cantidad", item.cantidad())
                        .append("precioUnitario", item.precioUnitario()));
            }
        }
        return new Document("pedidoId", evento.pedidoId())
                .append("usuarioId", evento.usuarioId())
                .append("estado", "EN_PREPARACION")
                .append("trackingCode", generarTracking(evento.pedidoId()))
                .append("montoTotal", evento.montoTotal())
                .append("fechaPedido", evento.fecha())
                .append("fechaCreacion", Instant.now().toString())
                .append("items", items);
    }

    private static String generarTracking(Long pedidoId) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "SL-" + pedidoId + "-" + sufijo;
    }

    private void log(Context context, String mensaje) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(mensaje);
        } else {
            System.out.println(mensaje);
        }
    }
}
