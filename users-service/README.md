# users-service — Gestión de Usuarios

Microservicio Spring Boot 3.3 / Java 17 que gestiona los usuarios del sistema con control de acceso por rol (`ADMIN` / `OPERADOR`).
Usa su propia base de datos PostgreSQL (patrón *Database per Service*).

| Item | Valor |
|---|---|
| Puerto de la API | `8081` |
| Base de datos | PostgreSQL 16, BD `users_db`, puerto host `5441` |
| Cabecera de rol | `X-User-Role: ADMIN` o `X-User-Role: OPERADOR` |

## Arquitectura en capas

```
cl.duoc.users
├── controller   → UsuarioController, AuthController (endpoints REST)
├── service      → UsuarioService, AuthService + impl/ (lógica de negocio), RoleValidator
├── repository   → UsuarioRepository (Spring Data JPA)
├── model        → Usuario (entidad JPA)
├── dto          → UsuarioCreateRequest, UsuarioUpdateRequest, StatusRequest, UsuarioResponse
└── exception    → GlobalExceptionHandler (@RestControllerAdvice), ErrorResponse y excepciones propias
```

## Endpoints

| Método | Ruta | Rol requerido | Respuestas |
|---|---|---|---|
| POST | `/api/auth/login` | — | 200, 400, 401 |
| GET | `/api/users` | — | 200 (solo usuarios activos) |
| GET | `/api/users/{id}` | — | 200, 404 |
| POST | `/api/users` | **ADMIN** | 201, 400, 401, 403, 409 |
| PATCH | `/api/users/{id}` | — | 200, 400, 404 (nombre, telefono, password) |
| PATCH | `/api/users/{id}/status` | **ADMIN** | 200, 400, 401, 403, 404 (eliminación/activación lógica) |
| DELETE | `/api/users/{id}` | **ADMIN** | 204, 401, 403, 404 (eliminación física) |

Reglas del rol (cabecera `X-User-Role`):
- No se envía la cabecera → **401 Unauthorized**
- Valor distinto de `ADMIN`/`OPERADOR` → **400 Bad Request**
- `OPERADOR` en una operación de ADMIN → **403 Forbidden**

### Login

`POST /api/auth/login` valida las credenciales con un `if` / `else if` en `AuthServiceImpl`:

```java
if (usuario.equals(usuarioAdmin) && password.equals(passwordAdmin)) {
    // rol ADMIN
} else if (usuario.equals(usuarioOperador) && password.equals(passwordOperador)) {
    // rol OPERADOR
} else {
    // 401 Unauthorized
}
```

| Rol | usuario | password |
|---|---|---|
| ADMIN | `admin` | `admin123` |
| OPERADOR | `operador` | `operador123` |

Las credenciales se definen en `application.yml` (sección `auth`) y se pueden cambiar con variables de entorno (`AUTH_ADMIN_USER`, `AUTH_ADMIN_PASSWORD`, `AUTH_OPERADOR_USER`, `AUTH_OPERADOR_PASSWORD`).
La respuesta devuelve el `rol` que luego se envía en la cabecera `X-User-Role`.

> La contraseña nunca se devuelve en las respuestas. Por alcance de la EP2 se almacena sin cifrar; en producción debería guardarse con hash (BCrypt).

---

## Despliegue local paso a paso

### Requisitos
- Docker Desktop (en ejecución)
- Opción A además: JDK 17. **No hace falta instalar Maven**: el proyecto incluye Maven Wrapper (`mvnw` / `mvnw.cmd`), que lo descarga solo.

### Opción A — Base de datos en Docker + aplicación con Maven

```bash
# 1. Levantar solo la base de datos PostgreSQL
docker compose up -d db

# 2. Compilar y empaquetar (genera target/users-service-1.0.0.jar)
./mvnw clean package          # Windows CMD/PowerShell: mvnw.cmd clean package
#   (con Maven instalado también sirve: mvn clean package)

# 3. Ejecutar
java -jar target/users-service-1.0.0.jar
#   alternativa sin empaquetar: ./mvnw spring-boot:run
```

### Opción B — Todo en Docker (BD + aplicación)

```bash
docker compose up -d --build
docker compose logs -f app     # ver logs (Ctrl+C para salir)
```
El `Dockerfile` es multi-stage: compila con `mvn clean package` dentro de un contenedor Maven y ejecuta el JAR en una imagen JRE liviana.

### Detener y limpiar
```bash
docker compose down        # detiene los contenedores
docker compose down -v     # además borra el volumen con los datos
```

---

## Pruebas con cURL

> En Windows, ejecuta los comandos en **Git Bash** (o impórtalos en Postman). En PowerShell las comillas del JSON se interpretan distinto.
> **Postman:** *Import → Raw text* → pega cualquiera de estos comandos cURL.

### Login → 200 OK
```bash
curl -i -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "usuario": "admin", "password": "admin123" }'
```
```json
{"mensaje":"Login exitoso","usuario":"admin","rol":"ADMIN"}
```
Con `{"usuario":"operador","password":"operador123"}` responde `"rol":"OPERADOR"`.

### Crear usuario (ADMIN) → 201 Created
```bash
curl -i -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMIN" \
  -d '{
    "nombre": "Ana Perez",
    "email": "ana@duoc.cl",
    "rut": "12345678-9",
    "telefono": "+56911112222",
    "password": "secreto123",
    "rol": "OPERADOR"
  }'
```
```json
{"id":1,"nombre":"Ana Perez","email":"ana@duoc.cl","rut":"12345678-9","telefono":"+56911112222","rol":"OPERADOR","active":true}
```

### Listar usuarios activos → 200 OK
```bash
curl -i http://localhost:8081/api/users
```

### Obtener usuario por ID → 200 OK
```bash
curl -i http://localhost:8081/api/users/1
```

### Actualización parcial del perfil → 200 OK
Solo se modifican los campos enviados (`nombre`, `telefono`, `password`).
```bash
curl -i -X PATCH http://localhost:8081/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{ "telefono": "+56999998888" }'
```

### Desactivar usuario (eliminación lógica, ADMIN) → 200 OK
```bash
curl -i -X PATCH http://localhost:8081/api/users/1/status \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMIN" \
  -d '{ "active": false }'
```
Para reactivarlo se envía `{ "active": true }`. Un usuario inactivo deja de aparecer en `GET /api/users`.

### Eliminar usuario físicamente (ADMIN) → 204 No Content
```bash
curl -i -X DELETE http://localhost:8081/api/users/1 -H "X-User-Role: ADMIN"
```

---

## Pruebas de error

Todas las respuestas de error usan el mismo formato JSON:
`timestamp`, `status`, `error`, `message`, `path` y, en errores de validación, `details`.

### 401 Unauthorized — Login con credenciales incorrectas
```bash
curl -i -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "usuario": "admin", "password": "incorrecta" }'
```
```json
{"timestamp":"2026-10-10T23:05:27.510","status":401,"error":"Unauthorized","message":"Usuario o password incorrectos","path":"/api/auth/login"}
```

### 403 Forbidden — DELETE con rol OPERADOR
```bash
curl -i -X DELETE http://localhost:8081/api/users/1 -H "X-User-Role: OPERADOR"
```
```json
{"timestamp":"2026-10-10T22:45:45.822","status":403,"error":"Forbidden","message":"Acceso denegado: esta operacion requiere rol ADMIN","path":"/api/users/1"}
```

### 403 Forbidden — Crear usuario o cambiar estado como OPERADOR
```bash
curl -i -X PATCH http://localhost:8081/api/users/1/status \
  -H "Content-Type: application/json" -H "X-User-Role: OPERADOR" \
  -d '{ "active": false }'
```

### 401 Unauthorized — Sin cabecera de rol
```bash
curl -i -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"nombre":"X","email":"x@duoc.cl","rut":"11111111-1","password":"secreto123","rol":"ADMIN"}'
```
```json
{"timestamp":"2026-10-10T22:45:43.705","status":401,"error":"Unauthorized","message":"Debe enviar la cabecera X-User-Role para identificar su rol","path":"/api/users"}
```

### 404 Not Found — ID inexistente
```bash
curl -i http://localhost:8081/api/users/999
```
```json
{"timestamp":"2026-10-10T22:45:44.794","status":404,"error":"Not Found","message":"Usuario con id 999 no encontrado","path":"/api/users/999"}
```

### 400 Bad Request — Datos inválidos
```bash
curl -i -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" -H "X-User-Role: ADMIN" \
  -d '{"nombre":"X","email":"no-es-email","rut":"11111111-1","password":"123","rol":"ADMIN"}'
```
```json
{"timestamp":"2026-10-10T22:45:44.066","status":400,"error":"Bad Request","message":"Error de validacion en los datos enviados","path":"/api/users","details":["password: La password debe tener al menos 6 caracteres","email: El email no tiene un formato valido"]}
```

### 400 Bad Request — Rol inválido / ID no numérico
```bash
curl -i -X DELETE http://localhost:8081/api/users/1 -H "X-User-Role: JEFE"
curl -i http://localhost:8081/api/users/abc
```

### 409 Conflict — Email o RUT duplicado
```bash
curl -i -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" -H "X-User-Role: ADMIN" \
  -d '{"nombre":"Ana","email":"ana@duoc.cl","rut":"22222222-2","password":"secreto123","rol":"ADMIN"}'
```
