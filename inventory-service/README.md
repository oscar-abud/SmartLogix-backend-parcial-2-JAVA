# inventory-service

Microservicio de catálogo y stock. Puerto **3002**, PostgreSQL `inventory_db`.

Solo **consume** tokens JWT; no los emite. Comparte el `JWT_SECRET` con
`users-service` y con la Lambda authorizer del API Gateway.

> Documentación general: [`../README.md`](../README.md) ·
> Arquitectura: [`../ARQUITECTURA.md`](../ARQUITECTURA.md)

---

## Permisos

| Operación | Quién puede |
|---|---|
| Lectura (`GET`) | Cualquier usuario autenticado |
| Escritura (`POST`, `PATCH`, `PUT`, `DELETE`) | Solo **ADMIN** |
| `GET /actuator/health` | Público |

Las reglas están en la cadena de filtros (`SecurityConfig`), no en anotaciones por
método: son uniformes para todo el servicio.

> `GET /api/products/{id}` es el endpoint que consume `orders-service` para obtener
> el precio real de cada producto. Desde la EP3 exige token, así que `orders-service`
> **propaga la cabecera `Authorization` del cliente**: es el mismo usuario quien
> consulta el inventario, y no hacen falta credenciales de servicio.

---

## Endpoints

| Método | Ruta | Rol | Respuestas |
|---|---|---|---|
| GET | `/api/products` | autenticado | 200, 401 |
| GET | `/api/products/{id}` | autenticado | 200, 401, 404 |
| POST | `/api/products` | **ADMIN** | 201 + `Location`, 400, 401, 403, 404, 409 |
| PATCH | `/api/products/{id}/stock` | **ADMIN** | 200, 400, 401, 403, 404 |
| PATCH | `/api/products/bulk-stock` | **ADMIN** | 200, 400, 401, 403, 404 |
| DELETE | `/api/products/{id}` | **ADMIN** | 204, 401, 403, 404 |
| GET | `/api/categories` | autenticado | 200, 401 |
| POST | `/api/categories` | **ADMIN** | 201, 400, 401, 403, 409 |
| GET | `/actuator/health` | público | 200 |

**`bulk-stock` es todo-o-nada**: recibe un array de `{id, newStock}` y, si algún id
no existe, no aplica ningún cambio y responde 404.

### Datos sembrados

`data.sql` crea tres categorías al arrancar (SQL específico de PostgreSQL, con
`ON CONFLICT DO NOTHING`):

| id | Nombre |
|---|---|
| 1 | Electronica |
| 2 | Hogar |
| 3 | Oficina |

---

## Ejemplos

```bash
TOKEN=$(curl -s -X POST http://localhost:3001/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"admin@smartlogix.cl","password":"admin123"}' | jq -r .token)
A="Authorization: Bearer $TOKEN"

# Catálogo
curl -s http://localhost:3002/api/categories -H "$A" | jq
curl -s http://localhost:3002/api/products   -H "$A" | jq

# Crear producto (ADMIN)
curl -s -X POST http://localhost:3002/api/products -H "$A" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-EP3-001","nombre":"Teclado mecanico","descripcion":"RGB",
       "precio":29990,"stock":50,"categoriaId":1}' | jq

# Stock individual y masivo
curl -s -X PATCH http://localhost:3002/api/products/1/stock -H "$A" \
  -H 'Content-Type: application/json' -d '{"stock":45}' | jq

curl -s -X PATCH http://localhost:3002/api/products/bulk-stock -H "$A" \
  -H 'Content-Type: application/json' \
  -d '[{"id":1,"newStock":40},{"id":2,"newStock":75}]' | jq
```

### Errores

```bash
curl -i http://localhost:3002/api/products                      # 401 sin token
curl -i http://localhost:3002/api/products/999999 -H "$A"       # 404
curl -i -X POST http://localhost:3002/api/products -H "$OPER" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"X","nombre":"X","descripcion":"","precio":1,"stock":1,"categoriaId":1}'
# 403: un OPERADOR no puede escribir en el inventario

curl -i -X POST http://localhost:3002/api/products -H "$A" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"Y","nombre":"Y","descripcion":"","precio":-100,"stock":5,"categoriaId":1}'
# 400: precio negativo
```

---

## Configuración

| Variable | Por defecto | Descripción |
|---|---|---|
| `SERVER_PORT` | `3002` | |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5433` / `inventory_db` | |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | |
| `JWT_SECRET` | secreto de desarrollo | **Debe coincidir** con el de `users-service` |

---

## Ejecución

```bash
docker compose up -d --build     # desde la raíz, todo el sistema
./mvnw spring-boot:run           # solo este servicio
./mvnw -B verify                 # tests (7)
```

Los tests son *slices* `@WebMvcTest` con la cadena de seguridad cargada: verifican
que un OPERADOR lee pero no escribe, y que sin token la respuesta es 401. Requieren
**JDK 17**.
