# orders-service

Microservicio de pedidos. Puerto **3003**, PostgreSQL `orders_db`.

Es la pieza que conecta los dos estilos de comunicación del sistema: llama a
`inventory-service` de forma **síncrona** y publica en **SQS** de forma asíncrona.

> Documentación general: [`../README.md`](../README.md) ·
> Arquitectura: [`../ARQUITECTURA.md`](../ARQUITECTURA.md)

---

## Qué ocurre al crear un pedido

```
POST /api/orders
      │
      ├─1─> GET /api/products/{id} en inventory-service   (SÍNCRONO)
      │        propaga el JWT del cliente; obtiene el precio REAL
      │
      ├─2─> calcula montoTotal con BigDecimal y guarda en Postgres
      │
      └─3─> publica OrderCreatedEvent en SQS              (ASÍNCRONO)
                    │
                    └──> Lambda shipping ──> MongoDB Atlas
```

1. **El cliente nunca envía precios.** Solo `productoId` y `cantidad`. El precio se
   consulta a `inventory-service`, así que no se puede manipular el total.
2. **El monto se calcula con `BigDecimal`** y se redondea a 2 decimales (`HALF_UP`).
3. **La publicación en SQS va después del guardado y nunca lanza excepción.** Si la
   mensajería falla, el error se registra pero el pedido queda creado: perder una
   venta por un problema de cola sería peor que retrasar un envío.

Por qué una cola y no otra llamada HTTP: ver
[`../ARQUITECTURA.md` §6](../ARQUITECTURA.md).

---

## Endpoints

Todos exigen `Authorization: Bearer <token>`. Tanto ADMIN como OPERADOR pueden
operar con pedidos.

| Método | Ruta | Respuestas |
|---|---|---|
| POST | `/api/orders` | 201 + `Location`, 400, 401, 404, 503 |
| GET | `/api/orders/{id}` | 200, 401, 404 |
| PATCH | `/api/orders/{id}/status` | 200, 400, 401, 404 |
| GET | `/actuator/health` | 200 (público) |

Estados válidos: `PENDIENTE` (inicial), `PAGADO`, `CANCELADO`.

| Código | Cuándo |
|---|---|
| 404 | El producto no existe en `inventory-service`, o el pedido no existe |
| 503 | `inventory-service` no responde o devuelve un error |

---

## Ejemplos

```bash
TOKEN=$(curl -s -X POST http://localhost:3001/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"admin@smartlogix.cl","password":"admin123"}' | jq -r .token)
A="Authorization: Bearer $TOKEN"

# Crear pedido
curl -s -X POST http://localhost:3003/api/orders -H "$A" \
  -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"items":[{"productoId":1,"cantidad":2}]}' | jq
```

```json
{
  "id": 1,
  "usuarioId": 1,
  "fecha": "2026-11-22T22:04:29.346",
  "montoTotal": 59980.0,
  "estado": "PENDIENTE",
  "detalles": [
    { "id": 1, "productoId": 1, "cantidad": 2, "precioUnitario": 29990.0, "subtotal": 59980.0 }
  ]
}
```

```bash
curl -s http://localhost:3003/api/orders/1 -H "$A" | jq

curl -s -X PATCH http://localhost:3003/api/orders/1/status -H "$A" \
  -H 'Content-Type: application/json' -d '{"estado":"PAGADO"}' | jq
```

### Errores

```bash
curl -i http://localhost:3003/api/orders/1                       # 401 sin token

curl -i -X POST http://localhost:3003/api/orders -H "$A" \
  -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"items":[{"productoId":999999,"cantidad":1}]}'   # 404

curl -i -X POST http://localhost:3003/api/orders -H "$A" \
  -H 'Content-Type: application/json' -d '{"usuarioId":1,"items":[]}' # 400

# Degradación cuando inventory cae -> 503, no un 500 genérico
docker compose stop inventory-service
curl -i -X POST http://localhost:3003/api/orders -H "$A" \
  -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"items":[{"productoId":1,"cantidad":1}]}'
docker compose start inventory-service
```

---

## Configuración

| Variable | Por defecto | Descripción |
|---|---|---|
| `SERVER_PORT` | `3003` | |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5434` / `orders_db` | |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | |
| `JWT_SECRET` | secreto de desarrollo | **Debe coincidir** con el de `users-service` |
| `INVENTORY_URL` | `http://localhost:3002` | En Docker: `http://inventory-service:3002` |
| `SQS_QUEUE_URL` | *(vacío)* | URL de la cola. **Vacío = publicación desactivada** |
| `AWS_REGION` | `us-east-1` | |

### Trabajar en local sin AWS

Si `SQS_QUEUE_URL` está vacío, `ShippingEventPublisher` registra un *warning* al
arrancar y omite las publicaciones. El servicio funciona con normalidad sin
credenciales de AWS; lo único que no ocurre es la generación del envío.

En EC2 no hacen falta *access keys*: el `DefaultCredentialsProvider` las resuelve
desde el **instance profile** (`LabInstanceProfile`).

---

## Ejecución

```bash
docker compose up -d --build     # desde la raíz, todo el sistema
./mvnw spring-boot:run           # solo este servicio (necesita inventory arriba)
./mvnw -B verify                 # tests (4)
```

Los tests comprueban que el monto usa el precio del inventario y no el del cliente,
y que al crear un pedido se publica el evento. Requieren **JDK 17**.

### Clases relevantes

| Clase | Rol |
|---|---|
| `client/InventoryClient` | Llamada síncrona con propagación del JWT y timeouts (3 s / 5 s) |
| `messaging/OrderCreatedEvent` | Contrato del mensaje; su gemelo está en `lambdas/shipping-service` |
| `messaging/ShippingEventPublisher` | Productor SQS; tolera que la cola no esté configurada |
| `service/impl/PedidoServiceImpl` | Cálculo del total y disparo del evento |
