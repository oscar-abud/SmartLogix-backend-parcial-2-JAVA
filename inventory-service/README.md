# inventory-service — Gestión de Inventario y Productos

Microservicio Spring Boot 3.3 / Java 17 que administra el catálogo de productos y sus categorías.
Usa su propia base de datos PostgreSQL (patrón *Database per Service*).
`orders-service` también lo consulta (`GET /api/products/{id}`) para obtener el precio real de cada producto.

| Item | Valor |
|---|---|
| Puerto de la API | `8082` |
| Base de datos | PostgreSQL 16, BD `inventory_db`, puerto host `5442` |
| Datos iniciales | 3 categorías (`data.sql`): 1 Electronica, 2 Hogar, 3 Oficina |

## Arquitectura en capas

```
cl.duoc.inventory
├── controller   → ProductoController, CategoriaController
├── service      → ProductoService, CategoriaService + impl/
├── repository   → ProductoRepository (@EntityGraph para traer la categoría sin N+1), CategoriaRepository
├── model        → Producto  ──@ManyToOne──▶  Categoria
├── dto          → ProductoRequest/Response, CategoriaRequest/Response, StockUpdateRequest, BulkStockItem
└── exception    → GlobalExceptionHandler (@RestControllerAdvice), ErrorResponse y excepciones propias
```

## Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/api/products` | Lista todos los productos con su categoría | 200 |
| GET | `/api/products/{id}` | Obtiene un producto (lo usa orders-service) | 200, 404 |
| POST | `/api/products` | Crea un producto asociado a una categoría existente | 201, 400, 404, 409 |
| PATCH | `/api/products/{id}/stock` | Actualiza el stock de un producto | 200, 400, 404 |
| PATCH | `/api/products/bulk-stock` | Actualización masiva de stock (todo o nada) | 200, 400, 404 |
| DELETE | `/api/products/{id}` | Elimina un producto | 204, 404 |
| GET | `/api/categories` | Lista las categorías | 200 |
| POST | `/api/categories` | Crea una categoría | 201, 400, 409 |

---

## Despliegue local paso a paso

### Requisitos
- Docker Desktop (en ejecución)
- Opción A además: JDK 17. **No hace falta instalar Maven**: el proyecto incluye Maven Wrapper (`mvnw` / `mvnw.cmd`).

### Opción A — Base de datos en Docker + aplicación con Maven
```bash
# 1. Levantar solo la base de datos PostgreSQL
docker compose up -d db

# 2. Compilar y empaquetar (genera target/inventory-service-1.0.0.jar)
./mvnw clean package          # Windows CMD/PowerShell: mvnw.cmd clean package
#   (con Maven instalado también sirve: mvn clean package)

# 3. Ejecutar
java -jar target/inventory-service-1.0.0.jar
```

### Opción B — Todo en Docker (BD + aplicación)
```bash
docker compose up -d --build
docker compose logs -f app
```

### Detener y limpiar
```bash
docker compose down        # detiene los contenedores
docker compose down -v     # además borra los datos
```

---

## Pruebas con cURL

> En Windows, ejecuta los comandos en **Git Bash** (o impórtalos en Postman con *Import → Raw text*).

### Listar categorías → 200 OK
```bash
curl -i http://localhost:8082/api/categories
```

### Crear categoría → 201 Created
```bash
curl -i -X POST http://localhost:8082/api/categories \
  -H "Content-Type: application/json" \
  -d '{ "nombre": "Gamer", "descripcion": "Accesorios gamer" }'
```

### Crear producto → 201 Created
```bash
curl -i -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "NB-001",
    "nombre": "Notebook Lenovo",
    "descripcion": "14 pulgadas",
    "precio": 549990.0,
    "stock": 10,
    "categoriaId": 1
  }'
```
```json
{"id":1,"sku":"NB-001","nombre":"Notebook Lenovo","descripcion":"14 pulgadas","precio":549990.0,"stock":10,"categoria":{"id":1,"nombre":"Electronica","descripcion":"Computadores, perifericos y dispositivos electronicos"}}
```

Segundo producto, para las pruebas de pedidos:
```bash
curl -i -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"MS-002","nombre":"Mouse","descripcion":"Inalambrico","precio":12990.5,"stock":50,"categoriaId":1}'
```

### Listar productos con su categoría → 200 OK
```bash
curl -i http://localhost:8082/api/products
```

### Actualizar stock de un producto → 200 OK
```bash
curl -i -X PATCH http://localhost:8082/api/products/1/stock \
  -H "Content-Type: application/json" \
  -d '{ "stock": 25 }'
```

### Actualización masiva de stock → 200 OK
Recibe una lista de pares `{id, newStock}`. Si algún producto no existe o un valor es inválido, **no se aplica ningún cambio** (rollback de la transacción).
```bash
curl -i -X PATCH http://localhost:8082/api/products/bulk-stock \
  -H "Content-Type: application/json" \
  -d '[
    { "id": 1, "newStock": 100 },
    { "id": 2, "newStock": 200 }
  ]'
```

### Eliminar producto → 204 No Content
```bash
curl -i -X DELETE http://localhost:8082/api/products/2
```

---

## Pruebas de error

### 404 Not Found — Producto inexistente
```bash
curl -i http://localhost:8082/api/products/999
curl -i -X DELETE http://localhost:8082/api/products/999
```
```json
{"timestamp":"2026-10-10T22:45:50.759","status":404,"error":"Not Found","message":"Producto con id 999 no encontrado","path":"/api/products/999"}
```

### 404 Not Found — Categoría inexistente al crear un producto
```bash
curl -i -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"X-1","nombre":"X","precio":1.0,"stock":1,"categoriaId":99}'
```
```json
{"timestamp":"2026-10-10T22:45:47.107","status":404,"error":"Not Found","message":"Categoria con id 99 no encontrada","path":"/api/products"}
```

### 404 Not Found — Bulk con un ID inexistente (no se modifica nada)
```bash
curl -i -X PATCH http://localhost:8082/api/products/bulk-stock \
  -H "Content-Type: application/json" \
  -d '[{"id":1,"newStock":1},{"id":999,"newStock":5}]'
# Luego GET /api/products/1: el stock del producto 1 quedó igual que antes
```

### 400 Bad Request — Validaciones
```bash
# Precio negativo
curl -i -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"X-2","nombre":"X","precio":-5,"stock":1,"categoriaId":1}'

# Stock negativo
curl -i -X PATCH http://localhost:8082/api/products/1/stock \
  -H "Content-Type: application/json" -d '{ "stock": -1 }'

# Bulk con lista vacía
curl -i -X PATCH http://localhost:8082/api/products/bulk-stock \
  -H "Content-Type: application/json" -d '[]'
```
```json
{"timestamp":"2026-10-10T22:45:47.443","status":400,"error":"Bad Request","message":"Error de validacion en los datos enviados","path":"/api/products","details":["precio: El precio debe ser mayor a 0"]}
```

### 409 Conflict — SKU duplicado
```bash
curl -i -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"NB-001","nombre":"X","precio":1.0,"stock":1,"categoriaId":1}'
```
