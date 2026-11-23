# users-service

Microservicio de usuarios y **emisor de los JWT** del sistema. Puerto **3001**,
PostgreSQL `users_db`.

Es el único servicio que firma tokens; `inventory-service`, `orders-service` y la
Lambda authorizer del API Gateway solo los validan.

> Documentación general del sistema: [`../README.md`](../README.md) ·
> Decisiones de arquitectura: [`../ARQUITECTURA.md`](../ARQUITECTURA.md)

---

## Autenticación

`POST /api/auth/login` busca el usuario por email en la tabla `usuarios`, verifica
la password con **BCrypt** y devuelve un JWT firmado con **HS256**.

```json
{
  "mensaje": "Login exitoso",
  "usuario": "admin@smartlogix.cl",
  "rol": "ADMIN",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "expiraEnSegundos": 3600
}
```

Claims del token: `sub` (email), `uid`, `nombre`, `rol`, `iss` (`smartlogix-users`),
`iat`, `exp`.

**Usuarios sembrados** la primera vez que arranca, solo si la tabla está vacía
(`DataInitializer`):

| Email | Password | Rol |
|---|---|---|
| `admin@smartlogix.cl` | `admin123` | ADMIN |
| `operador@smartlogix.cl` | `operador123` | OPERADOR |

> **Cambio respecto a la EP2.** Antes el login comparaba dos credenciales fijas del
> `application.yml`, no consultaba la base de datos y no emitía token; el rol viajaba
> en la cabecera `X-User-Role`, que cualquier cliente podía falsificar. Ahora esa
> cabecera se ignora por completo y el rol sale del claim firmado.
>
> Si reutilizas un volumen de la EP2, las contraseñas estaban en texto plano y no
> validarán contra BCrypt: borra el volumen (`docker compose down -v`) para que se
> regenere el seed.

---

## Endpoints

Todos exigen `Authorization: Bearer <token>` salvo el login y el *health*.

| Método | Ruta | Rol | Respuestas |
|---|---|---|---|
| POST | `/api/auth/login` | público | 200, 400, 401 |
| GET | `/api/users` | autenticado | 200, 401 |
| GET | `/api/users/{id}` | autenticado | 200, 401, 404 |
| POST | `/api/users` | **ADMIN** | 201 + `Location`, 400, 401, 403, 409 |
| PATCH | `/api/users/{id}` | autenticado | 200, 400, 401, 404 |
| PATCH | `/api/users/{id}/status` | **ADMIN** | 200, 401, 403, 404 |
| DELETE | `/api/users/{id}` | **ADMIN** | 204, 401, 403, 404 |
| GET | `/actuator/health` | público | 200 |

`GET /api/users` lista solo los usuarios con `active = true`. `PATCH .../status` es
la baja lógica; `DELETE` es la baja física.

Las contraseñas nunca se devuelven en las respuestas, y se cifran con BCrypt tanto
al crear como al actualizar.

---

## Ejemplos

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:3001/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"admin@smartlogix.cl","password":"admin123"}' | jq -r .token)

# Listar
curl -s http://localhost:3001/api/users -H "Authorization: Bearer $TOKEN" | jq

# Crear (solo ADMIN)
curl -s -X POST http://localhost:3001/api/users \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nombre":"Camila Rojas","email":"camila.rojas@smartlogix.cl",
       "rut":"19876543-2","telefono":"+56912345678",
       "password":"camila123","rol":"OPERADOR"}' | jq

# Baja lógica y baja física
curl -s -X PATCH http://localhost:3001/api/users/2/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"active":false}' | jq
curl -i -X DELETE http://localhost:3001/api/users/2 -H "Authorization: Bearer $TOKEN"
```

### Errores

```bash
# Sin token -> 401
curl -i http://localhost:3001/api/users

# La cabecera de la EP2 ya no sirve -> 401
curl -i http://localhost:3001/api/users -H 'X-User-Role: ADMIN'

# Con token de OPERADOR en una operación de ADMIN -> 403
curl -i -X DELETE http://localhost:3001/api/users/1 -H "Authorization: Bearer $TOKEN_OPERADOR"
```

```json
{
  "timestamp": "2026-11-22T22:06:56.909",
  "status": 403,
  "error": "Forbidden",
  "message": "Acceso denegado: su rol no tiene permiso para esta operacion",
  "path": "/api/users"
}
```

---

## Configuración

| Variable | Por defecto | Descripción |
|---|---|---|
| `SERVER_PORT` | `3001` | |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `users_db` | |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | |
| `JWT_SECRET` | secreto de desarrollo | **Mínimo 32 bytes.** El mismo en los 3 servicios y en la Lambda authorizer |
| `JWT_EXPIRATION_MS` | `3600000` | Vida del token |
| `AUTH_SEED_ENABLED` | `true` | Pon `false` para no sembrar usuarios |
| `AUTH_ADMIN_USER` / `AUTH_ADMIN_PASSWORD` | `admin@smartlogix.cl` / `admin123` | Credenciales del seed |
| `AUTH_OPERADOR_USER` / `AUTH_OPERADOR_PASSWORD` | `operador@smartlogix.cl` / `operador123` | |

---

## Ejecución

```bash
# Todo el sistema (recomendado), desde la raíz del repositorio
docker compose up -d --build

# Solo este servicio, con un Postgres accesible
./mvnw spring-boot:run

# Tests (18)
./mvnw -B verify
```

Requiere **JDK 17**: la versión de Lombok que fija Spring Boot 3.3.5 no compila con
JDK 22 o superior.

### Clases relevantes

| Clase | Rol |
|---|---|
| `security/JwtService` | Emite los tokens (HS256, algoritmo fijado explícitamente) |
| `security/JwtTokenParser` | Valida firma, expiración y emisor |
| `security/JwtAuthenticationFilter` | Puebla el `SecurityContext` desde el header |
| `security/SecurityConfig` | Cadena stateless, rutas públicas y `BCryptPasswordEncoder` |
| `security/JwtAuthEntryPoint` | Hace que 401 y 403 devuelvan el JSON de error estándar |
| `config/DataInitializer` | Seed inicial de usuarios |
