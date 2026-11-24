package cl.duoc.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * Productor del flujo asincrono: publica en Amazon SQS el evento de pedido creado.
 *
 * Decisiones de diseno:
 *  - Las credenciales salen del DefaultCredentialsProvider, de modo que en EC2 se
 *    resuelven solas por el instance profile (LabInstanceProfile) sin access keys.
 *  - Si sqs.shipping-queue-url viene vacio se desactiva la publicacion. Asi el
 *    entorno local con docker compose arranca sin credenciales de AWS.
 *  - Un fallo al publicar se registra pero NUNCA propaga: el pedido ya se guardo
 *    y no debe perderse porque la mensajeria este caida.
 */
@Slf4j
@Component
public class ShippingEventPublisher {

    private final String queueUrl;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient;

    public ShippingEventPublisher(@Value("${sqs.shipping-queue-url:}") String queueUrl,
                                  @Value("${sqs.region:us-east-1}") String region,
                                  ObjectMapper objectMapper) {
        this.queueUrl = queueUrl == null ? "" : queueUrl.trim();
        this.objectMapper = objectMapper;
        if (this.queueUrl.isEmpty()) {
            this.sqsClient = null;
            log.warn("sqs.shipping-queue-url no configurada: los eventos de envio NO se publicaran "
                    + "(modo local). Defina SQS_QUEUE_URL para activar la integracion con SQS.");
        } else {
            this.sqsClient = SqsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            log.info("Publicador SQS activo sobre la cola {}", this.queueUrl);
        }
    }

    public void publicar(OrderCreatedEvent evento) {
        if (sqsClient == null) {
            log.debug("Publicacion omitida para el pedido {}: SQS desactivado", evento.pedidoId());
            return;
        }
        try {
            String cuerpo = objectMapper.writeValueAsString(evento);
            SendMessageResponse respuesta = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(cuerpo)
                    .build());
            log.info("Evento de pedido {} publicado en SQS (messageId={})",
                    evento.pedidoId(), respuesta.messageId());
        } catch (Exception ex) {
            log.error("No se pudo publicar el evento del pedido {} en SQS. "
                    + "El pedido quedo creado igualmente.", evento.pedidoId(), ex);
        }
    }

    @PreDestroy
    void cerrar() {
        if (sqsClient != null) {
            sqsClient.close();
        }
    }
}
