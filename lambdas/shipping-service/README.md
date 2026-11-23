# shipping-service — Microservicio de envíos (AWS Lambda)

Función serverless en Java 17 que completa la arquitectura de SmartLogix: es el
**consumidor** del flujo asíncrono. SQS la invoca con los eventos de pedido creado
que publica `orders-service`, y por cada uno genera el envío correspondiente en
**MongoDB Atlas**.

```
orders-service ──publica──> SQS smartlogix-shipping-queue ──dispara──> ESTA FUNCIÓN ──> MongoDB Atlas
                                      │                                                  colección "envios"
                                      └── tras 3 fallos ──> smartlogix-shipping-dlq
```

## Configuración

| Variable de entorno | Obligatoria | Descripción |
|---|:---:|---|
| `MONGODB_URI` | sí | Cadena de conexión de Atlas (`mongodb+srv://...`) |
| `MONGODB_DB` | no | Base de datos. Por defecto `smartlogix` |

| Ajuste de la función | Valor |
|---|---|
| Runtime | `java17` |
| Handler | `cl.duoc.shipping.ShippingHandler::handleRequest` |
| Memoria | 512 MB |
| Timeout | 30 s (menor que el *visibility timeout* de 60 s de la cola) |
| Rol | `LabRole` (Learner Lab no permite crear roles IAM) |

> **MongoDB Atlas**: la función corre fuera de VPC, así que su IP de salida no es
> fija. En *Network Access* hay que permitir `0.0.0.0/0`, o los intentos de conexión
> se quedarán colgados hasta agotar el timeout.

## Documento que escribe

```json
{
  "pedidoId": 7,
  "usuarioId": 1,
  "estado": "EN_PREPARACION",
  "trackingCode": "SL-7-A3F91C20",
  "montoTotal": 25990.0,
  "fechaPedido": "2026-11-22T10:15:30",
  "fechaCreacion": "2026-11-22T13:40:11.502Z",
  "items": [ { "productoId": 1, "cantidad": 2, "precioUnitario": 12995.0 } ]
}
```

## Decisiones de diseño

- **Idempotencia.** SQS garantiza entrega *al menos una vez*, así que un mismo
  mensaje puede llegar dos veces. Hay un índice único sobre `pedidoId`: la segunda
  entrega se detecta, se registra y se ignora en vez de duplicar el envío.
- **Conexión reutilizada.** El `MongoClient` es estático. Lambda reutiliza el
  contenedor entre invocaciones calientes, de modo que el *handshake* TLS contra
  Atlas se paga una sola vez y no en cada mensaje.
- **Mensajes corruptos no se reintentan.** Un JSON inválido se registra y se
  descarta: reintentarlo produciría un bucle hasta agotar los 3 intentos sin
  ninguna posibilidad de éxito. Los fallos *transitorios* (Atlas caído) sí
  propagan la excepción, y entonces SQS reintenta y acaba usando la DLQ.
- **Tolerante a cambios del productor.** `OrderCreatedEvent` ignora las propiedades
  desconocidas, así que `orders-service` puede añadir campos al evento sin que haya
  que desplegar los dos lados a la vez.

## Build

```bash
./mvnw -B clean package          # genera target/shipping-service.jar (uber-jar)
./mvnw -B test                   # 5 tests, sin necesidad de Atlas
```

Los tests usan un doble en memoria de `EnvioRepository`, por eso el pipeline no
necesita credenciales de MongoDB.

## Despliegue

Lo normal es que lo haga el pipeline (`.github/workflows/ci-cd.yml`, jobs
`lambda-package` y `provision-cloud`). A mano:

```bash
./mvnw -B clean package
aws lambda update-function-code \
  --function-name smartlogix-shipping \
  --zip-file fileb://target/shipping-service.jar
```

## Pruebas

**Invocación directa con un evento SQS simulado:**

```bash
cat > /tmp/evento.json <<'EOF'
{"Records":[{"messageId":"1","body":"{\"pedidoId\":999,\"usuarioId\":1,\"montoTotal\":1000.0,\"estado\":\"PENDIENTE\",\"items\":[{\"productoId\":1,\"cantidad\":1,\"precioUnitario\":1000.0}]}"}]}
EOF

aws lambda invoke --function-name smartlogix-shipping \
  --payload fileb:///tmp/evento.json /tmp/salida.json
cat /tmp/salida.json
```

**A través de la cola, que es el camino real:**

```bash
Q=$(aws cloudformation describe-stacks --stack-name smartlogix-infra \
  --query "Stacks[0].Outputs[?OutputKey=='ShippingQueueUrl'].OutputValue" --output text)

aws sqs send-message --queue-url "$Q" \
  --message-body '{"pedidoId":999,"usuarioId":1,"montoTotal":1000.0,"items":[]}'

aws logs tail /aws/lambda/smartlogix-shipping --follow
```

**Comprobar que la cola se vació** (es la prueba de que el consumidor funciona):

```bash
aws sqs get-queue-attributes --queue-url "$Q" \
  --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible
```

**Revisar los mensajes que fallaron**, en la DLQ:

```bash
DLQ=$(aws cloudformation describe-stacks --stack-name smartlogix-infra \
  --query "Stacks[0].Outputs[?OutputKey=='ShippingDlqUrl'].OutputValue" --output text)
aws sqs receive-message --queue-url "$DLQ" --max-number-of-messages 10
```

## Problemas frecuentes

| Síntoma | Causa |
|---|---|
| La función agota el timeout sin registrar nada | Atlas no permite la IP de salida: revisa *Network Access* |
| `Falta la variable de entorno MONGODB_URI` | La variable no llegó a la función; revisa el parámetro `MongoUri` del stack |
| Los mensajes acaban en la DLQ | Mira los logs: `aws logs tail /aws/lambda/smartlogix-shipping` |
| La cola no se vacía nunca | El `EventSourceMapping` está deshabilitado o el rol no tiene permisos sobre SQS |
