# orders-service — Gestión de Pedidos

Microservicio Spring Boot 3.3 / Java 17 que registra los pedidos y sus detalles.
Usa su propia base de datos PostgreSQL (patrón *Database per Service*).

| Item | Valor |
|---|---|
| Puerto de la API | `8083` |
| Base de datos | PostgreSQL 16, BD `orders_db`, puerto host `5443` |
| Dependencia | **inventory-service** (`http://localhost:8082`) debe estar corriendo |

## Lógica de negocio clave

En `POST /api/orders` el cliente envía **solo** `usuarioId` y los items (`productoId`, `cantidad`).
Por seguridad, el backend:
1. Consulta cada producto en **inventory-service** (`GET /api/products/{id}`) para obtener su **precio real**. Así el cliente no puede imponer el precio.
2. Guarda ese precio como `precioUnitario` de cada `DetallePedido`.
3. Calcula `montoTotal = Σ (cantidad × precioUnitario)` con `BigDecimal`, redondeado a 2 decimales.
4. Guarda el `Pedido` con estado `PENDIENTE`; sus detalles se persisten en cascada (`@OneToMany(cascade = CascadeType.ALL)`).

Si un producto no existe → **404**. Si inventory-service no responde → **503**.

## Arquitectura en capas

```
cl.duoc.orders
├── controller   → PedidoController
├── service      → PedidoService + impl/PedidoServiceImpl (calcula el monto total)
├── client       → InventoryClient (RestClient hacia inventory-service)
├── repository   → PedidoRepository (@EntityGraph para traer los detalles)
├── model        → Pedido ──@OneToMany(cascade ALL)──▶ DetallePedido, EstadoPedido (enum)
├── dto          → PedidoCreateRequest, PedidoItemRequest, EstadoUpdateRequest, PedidoResponse, DetallePedidoResponse
└── exception    → GlobalExceptionHandler (@RestControllerAdvice), ErrorResponse y excepciones propias
```

## Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/api/orders/{id}` | Consulta un pedido con sus detalles | 200, 404 |
| POST | `/api/orders` | Crea el pedido calculando el monto en el backend | **201**, 400, 404, 503 |
| PATCH | `/api/orders/{id}/status` | Cambia solo el estado (`PENDIENTE`, `PAGADO`, `CANCELADO`) | 200, 400, 404 |

---

## Despliegue local paso a paso

### Requisitos
- Docker Desktop (en ejecución)
- **inventory-service levantado en el puerto 8082** (ver su README) y con productos creados
- Opción A además: JDK 17. **No hace falta instalar Maven**: el proyecto incluye Maven Wrapper (`mvnw` / `mvnw.cmd`).

### Opción A — Base de datos en Docker + aplicación con Maven
```bash
# 1. Levantar solo la base de datos PostgreSQL
docker compose up -d db

# 2. Compilar y empaquetar (genera target/orders-service-1.0.0.jar)
./mvnw clean package          # Windows CMD/PowerShell: mvnw.cmd clean package
#   (con Maven instalado también sirve: mvn clean package)

# 3. Ejecutar (por defecto busca inventory-service en http://localhost:8082)
java -jar target/orders-service-1.0.0.jar
```

### Opción B — Todo en Docker (BD + aplicación)
```bash
docker compose up -d --build
docker compose logs -f app
```
Dentro de Docker, la app llega a inventory-service mediante `INVENTORY_URL=http://host.docker.internal:8082`, configurado en `docker-compose.yml`.
Si inventory corre en otra URL, cambia esa variable (o `inventory.base-url` en `application.yml`).

### Detener y limpiar
```bash
docker compose down        # detiene los contenedores
docker compose down -v     # además borra los datos
```

---

## Pruebas con cURL

> En Windows, ejecuta los comandos en **Git Bash** (o impórtalos en Postman con *Import → Raw text*).
> Los ejemplos asumen que en inventory-service existen el producto 1 (Notebook, $549.990) y el producto 2 (Mouse, $12.990,5). Ver el README de inventory.

### Crear pedido → 201 Created
```bash
curl -i -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "usuarioId": 1,
    "items": [
      { "productoId": 1, "cantidad": 2 },
      { "productoId": 2, "cantidad": 3 }
    ]
  }'
```
Respuesta, con el monto calculado en el backend (2 × 549990 + 3 × 12990,5 = 1138951,5):
```json
{
  "id": 1,
  "usuarioId": 1,
  "fecha": "2026-10-10T22:45:48.893181",
  "montoTotal": 1138951.5,
  "estado": "PENDIENTE",
  "detalles": [
    { "id": 1, "productoId": 1, "cantidad": 2, "precioUnitario": 549990.0, "subtotal": 1099980.0 },
    { "id": 2, "productoId": 2, "cantidad": 3, "precioUnitario": 12990.5, "subtotal": 38971.5 }
  ]
}
```
La respuesta incluye la cabecera `Location: http://localhost:8083/api/orders/1`.

### Consultar pedido con detalles → 200 OK
```bash
curl -i http://localhost:8083/api/orders/1
```

### Cambiar estado → 200 OK
```bash
curl -i -X PATCH http://localhost:8083/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "estado": "PAGADO" }'
```
Valores válidos: `PENDIENTE`, `PAGADO`, `CANCELADO`.

---

## Pruebas de error

### 404 Not Found — Pedido inexistente
```bash
curl -i http://localhost:8083/api/orders/999
```
```json
{"timestamp":"2026-10-10T22:45:50.360","status":404,"error":"Not Found","message":"Pedido con id 999 no encontrado","path":"/api/orders/999"}
```

### 404 Not Found — Producto inexistente en inventory
```bash
curl -i -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"usuarioId":1,"items":[{"productoId":999,"cantidad":1}]}'
```
```json
{"timestamp":"2026-10-10T22:45:49.797","status":404,"error":"Not Found","message":"Producto con id 999 no existe en inventory-service","path":"/api/orders"}
```

### 400 Bad Request — Estado inválido
```bash
curl -i -X PATCH http://localhost:8083/api/orders/1/status \
  -H "Content-Type: application/json" -d '{ "estado": "XYZ" }'
```
```json
{"timestamp":"2026-10-10T22:45:49.594","status":400,"error":"Bad Request","message":"El cuerpo de la peticion es invalido o esta mal formado","path":"/api/orders/1/status"}
```

### 400 Bad Request — Pedido sin items o cantidad inválida
```bash
curl -i -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" -d '{"usuarioId":1,"items":[]}'

curl -i -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"usuarioId":1,"items":[{"productoId":1,"cantidad":0}]}'
```
```json
{"timestamp":"2026-10-10T22:45:50.179","status":400,"error":"Bad Request","message":"Error de validacion en los datos enviados","path":"/api/orders","details":["items[0].cantidad: La cantidad debe ser al menos 1"]}
```

### 503 Service Unavailable — inventory-service detenido
```bash
docker stop inventory-service
curl -i -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"usuarioId":1,"items":[{"productoId":1,"cantidad":1}]}'
docker start inventory-service
```
```json
{"timestamp":"2026-10-10T22:46:47.581","status":503,"error":"Service Unavailable","message":"inventory-service no esta disponible. Intente nuevamente mas tarde","path":"/api/orders"}
```
