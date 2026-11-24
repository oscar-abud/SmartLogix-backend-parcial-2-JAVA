# SmartLogix — Backend de microservicios nativos en nube

**Duoc UC — Java: Diseño y Construcción de Soluciones Nativas en Nube (JVY0101)**
Evaluación Parcial N°3 (EP3)

Sistema de logística compuesto por tres microservicios Spring Boot sobre un clúster
Docker Swarm en EC2, un microservicio de envíos *serverless*, y un API Gateway que
protege todo el backend con un validador de JWT externo en AWS Lambda.

---

## Arquitectura

```
                        ┌──────────────────────────────────────┐
   Cliente  ──HTTPS──>  │   AWS API Gateway (REST, stage prod) │
   (Postman /           │   · Lambda Authorizer (valida JWT)   │
    navegador)          │   · CORS + Resource Policy (IAM)     │
                        │   · Throttling 50 rps                │
                        └──────────────┬───────────────────────┘
                                       │ HTTP_PROXY
                  ┌────────────────────┼────────────────────┐
                  │                    │                    │
            :3001 │              :3002 │              :3003 │
       ┌──────────▼───────┐  ┌─────────▼────────┐  ┌────────▼─────────┐
       │  users-service   │  │ inventory-service│  │  orders-service  │
       │  emite el JWT    │  │  valida el JWT   │  │  valida el JWT   │
       └──────────┬───────┘  └─────────┬────────┘  └────────┬─────────┘
                  │                    │ ▲                  │
          ┌───────▼──────┐   ┌─────────▼─┴──────┐   ┌───────▼──────┐
          │ Postgres     │   │ Postgres         │   │ Postgres     │
          │ users_db     │   │ inventory_db     │   │ orders_db    │
          └──────────────┘   └──────────────────┘   └───────┬──────┘
                                        ▲                   │ publica
             orders consulta el precio ─┘                   ▼
             real antes de crear el pedido          ┌───────────────┐
                                                    │   AWS SQS     │
              Todo lo anterior corre como           │ shipping-queue│
              servicios de DOCKER SWARM             └───────┬───────┘
              sobre 2 instancias EC2                        │ dispara
                                                    ┌───────▼────────┐
                                                    │ Lambda shipping│
                                                    │  (FaaS, Java)  │
                                                    └───────┬────────┘
                                                            ▼
                                                   ┌──────────────────┐
                                                   │ MongoDB Atlas    │
                                                   │ colección envios │
                                                   └──────────────────┘
```

| Microservicio | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `users-service` | 3001 | PostgreSQL `users_db` | Usuarios, roles y **emisión de JWT** |
| `inventory-service` | 3002 | PostgreSQL `inventory_db` | Productos, categorías y stock |
| `orders-service` | 3003 | PostgreSQL `orders_db` | Pedidos; **productor** del evento de envío |
| `lambdas/shipping-service` | — (FaaS) | MongoDB Atlas | Envíos; **consumidor** de la cola SQS |
| `lambdas/jwt-authorizer` | — (FaaS) | — | Valida el JWT en el API Gateway |

La justificación técnica de estas decisiones está en [`ARQUITECTURA.md`](ARQUITECTURA.md).

---

## Autenticación (JWT)

En la EP2 el rol viajaba en la cabecera `X-User-Role`, que cualquier cliente podía
falsificar. Ahora se usa un **JWT firmado con HS256**:

1. `POST /api/auth/login` contra `users-service` devuelve el token.
2. Toda petición posterior lleva `Authorization: Bearer <token>`.
3. El token se valida **dos veces**, de forma deliberada:
   - en el **API Gateway**, por la Lambda `smartlogix-jwt-authorizer`, antes de
     que la petición llegue a la nube privada;
   - en **cada microservicio**, por su propio `JwtAuthenticationFilter`.

   Esta defensa en profundidad es lo que permite que las EC2 publiquen sus puertos
   sin quedar expuestas: aunque alguien salte el Gateway y llame directo a la
   instancia, sigue necesitando un token válido.

**Usuarios sembrados al primer arranque** (`DataInitializer`, solo si la tabla está vacía):

| Usuario | Password | Rol |
|---|---|---|
| `admin@smartlogix.cl` | `admin123` | ADMIN |
| `operador@smartlogix.cl` | `operador123` | OPERADOR |

Las contraseñas se guardan con **BCrypt**. El seed se desactiva con `AUTH_SEED_ENABLED=false`.

### Permisos por rol

| Operación | ADMIN | OPERADOR | Sin token |
|---|:---:|:---:|:---:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ (es público) |
| Leer usuarios, productos, categorías, pedidos | ✅ | ✅ | ❌ 401 |
| Crear / modificar / eliminar **usuarios** | ✅ | ❌ 403 | ❌ 401 |
| Crear / modificar / eliminar **productos y categorías** | ✅ | ❌ 403 | ❌ 401 |
| Crear y actualizar **pedidos** | ✅ | ✅ | ❌ 401 |

---

## Endpoints

Todas las rutas van precedidas por la URL del API Gateway en la nube, o por
`http://localhost:<puerto>` en local.

### users-service (3001)
| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/auth/login` | público |
| GET | `/api/users` | autenticado |
| GET | `/api/users/{id}` | autenticado |
| POST | `/api/users` | ADMIN |
| PATCH | `/api/users/{id}` | autenticado |
| PATCH | `/api/users/{id}/status` | ADMIN |
| DELETE | `/api/users/{id}` | ADMIN |

### inventory-service (3002)
| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/products` · `/api/products/{id}` | autenticado |
| POST | `/api/products` | ADMIN |
| PATCH | `/api/products/{id}/stock` · `/api/products/bulk-stock` | ADMIN |
| DELETE | `/api/products/{id}` | ADMIN |
| GET | `/api/categories` | autenticado |
| POST | `/api/categories` | ADMIN |

### orders-service (3003)
| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/orders/{id}` | autenticado |
| POST | `/api/orders` | autenticado |
| PATCH | `/api/orders/{id}/status` | autenticado |

Los tres exponen además `GET /actuator/health`, público, que usan los *healthchecks*
de Docker y de Swarm.

---

## 1. Entorno local con Docker Compose

```bash
cp .env.example .env          # ajusta puertos si ya tienes un Postgres local
docker compose up -d --build

# Verificar que los tres están arriba
curl http://localhost:3001/actuator/health
curl http://localhost:3002/actuator/health
curl http://localhost:3003/actuator/health
```

Flujo completo en local:

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:3001/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"admin@smartlogix.cl","password":"admin123"}' | jq -r .token)

# 2. Sin token -> 401
curl -i http://localhost:3002/api/products

# 3. Con token -> 200
curl -s http://localhost:3002/api/products -H "Authorization: Bearer $TOKEN" | jq

# 4. Crear un producto y un pedido
curl -s -X POST http://localhost:3002/api/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-001","nombre":"Teclado","descripcion":"RGB","precio":29990,"stock":50,"categoriaId":1}' | jq

curl -s -X POST http://localhost:3003/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"items":[{"productoId":1,"cantidad":2}]}' | jq
```

En local `SQS_QUEUE_URL` va vacío, así que `orders-service` registra un *warning*
y omite la publicación. No hacen falta credenciales de AWS para desarrollar.

Apagar: `docker compose down` (añade `-v` para borrar también los datos).

> **Si cambias de la EP2 a la EP3 reutilizando volúmenes antiguos**, borra el de
> usuarios: las contraseñas estaban en texto plano y ahora se verifican con BCrypt.
> `docker compose down -v` resuelve el problema regenerando el seed.

---

## 2. Clúster Docker Swarm sobre EC2

### 2.1 Requisitos en AWS

Dos instancias EC2 (por ejemplo `t3.small`, Amazon Linux 2023), ambas con Docker
instalado y con el perfil IAM **`LabInstanceProfile`** (así `orders-service` publica
en SQS sin *access keys*).

**Security Group — esto es lo que más falla.** Además de SSH (22) y de los puertos
de la aplicación (3001-3003), el manager y el worker deben poder hablar entre sí:

| Puerto | Protocolo | Para qué |
|---|---|---|
| 2377 | TCP | Comunicación de gestión del clúster |
| 7946 | TCP y UDP | Descubrimiento entre nodos |
| 4789 | UDP | Red *overlay* (VXLAN) |

Si estos puertos están cerrados, los servicios arrancan pero los contenedores de
nodos distintos no se ven entre ellos.

Asigna además una **Elastic IP al manager**: su DNS público cambia en cada
*stop/start*, y ese valor es un parámetro de CloudFormation y un secret de GitHub.

### 2.2 Inicializar el clúster

En el **manager**:

```bash
git clone https://github.com/oscar-abud/SmartLogix-backend-parcial-2-JAVA.git
cd SmartLogix-backend-parcial-2-JAVA
chmod +x infra/scripts/*.sh

DB_PASSWORD='una-password-segura' ./infra/scripts/swarm-init-manager.sh
```

El script detecta la IP privada de la instancia, ejecuta `docker swarm init`, crea
el *secret* `db_password` y **imprime el comando de unión**. Copia ese comando.

En el **worker**:

```bash
# Opción A: pegar el comando que imprimió el manager
docker swarm join --token SWMTKN-1-xxxxx 10.0.1.25:2377

# Opción B: con el script
./infra/scripts/swarm-join-worker.sh 10.0.1.25 SWMTKN-1-xxxxx
```

De vuelta en el **manager**, comprueba que hay dos nodos:

```bash
docker node ls
# ID        HOSTNAME        STATUS   AVAILABILITY   MANAGER STATUS
# abc123 *  ip-10-0-1-25    Ready    Active         Leader
# def456    ip-10-0-1-26    Ready    Active
```

### 2.3 Desplegar el stack

```bash
export JWT_SECRET='el-mismo-secreto-que-usa-la-lambda-authorizer'
export SQS_QUEUE_URL='https://sqs.us-east-1.amazonaws.com/123456789012/smartlogix-shipping-queue'
export DB_PASSWORD='la-misma-del-secret'
export TAG=latest            # o el SHA del commit

./infra/scripts/deploy-stack.sh
```

Comandos útiles:

```bash
docker stack services smartlogix                       # réplicas por servicio
docker stack ps smartlogix                             # en qué nodo corre cada una
docker service logs smartlogix_orders-service --tail 50
docker service rollback smartlogix_orders-service      # volver a la versión anterior
docker stack rm smartlogix                             # bajar todo
```

### 2.4 Escalar

```bash
# Subir, ver la distribución entre nodos y volver a bajar, en un solo comando
./infra/scripts/scale-demo.sh orders-service 4

# O a mano
docker service scale smartlogix_orders-service=4
docker service ps smartlogix_orders-service --filter desired-state=running
docker service scale smartlogix_orders-service=2
```

El *routing mesh* de Swarm balancea las peticiones entre todas las réplicas,
estén en el nodo que estén, y el servicio sigue respondiendo durante el escalado.

---

## 3. CI/CD con GitHub Actions

El pipeline está en [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) y se
dispara con cada `push` a `main`:

```
build-test ──┬─> docker-images ──┐
             └─> lambda-package ─┴─> provision-cloud ──> deploy-swarm ──> smoke-test
```

| Job | Qué hace |
|---|---|
| `build-test` | `./mvnw verify` sobre los 3 microservicios y las 2 Lambdas (33 tests) |
| `docker-images` | Construye y publica las imágenes en GHCR, etiquetadas con el SHA |
| `lambda-package` | Genera los *uber-jar* y los sube a S3 (crea el bucket si no existe) |
| `provision-cloud` | **`aws cloudformation deploy`**: SQS + DLQ, las 2 Lambdas, el API Gateway con su authorizer, CORS y Resource Policy |
| `deploy-swarm` | Por SSH al manager: `git pull` + `docker stack deploy` con el nuevo tag |
| `smoke-test` | Comprueba 401 sin token, login, 200 con token y 401 con token falso |

### Secrets que hay que configurar

`Settings → Secrets and variables → Actions`:

| Secret | De dónde sale |
|---|---|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Learner Lab → *AWS Details* → *AWS CLI* |
| `AWS_REGION` | `us-east-1` |
| `LAB_ROLE_ARN` | `aws iam get-role --role-name LabRole --query Role.Arn --output text` |
| `LAMBDA_S3_BUCKET` | Un nombre único, p. ej. `smartlogix-artifacts-<tu-id>` |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `MONGODB_URI` | Cadena de conexión de Atlas |
| `DB_PASSWORD` | La misma del *secret* `db_password` de Swarm |
| `EC2_MANAGER_HOST` | DNS público o Elastic IP del manager |
| `EC2_SSH_USER` | `ec2-user` en Amazon Linux |
| `EC2_SSH_KEY` | Contenido completo del `.pem` (incluidas las líneas `BEGIN`/`END`) |

> ⚠️ **Las credenciales de AWS Academy caducan al cerrar el laboratorio.** Si el
> pipeline falla con `ExpiredToken`, refréscalas con
> `./infra/scripts/refresh-gh-secrets.sh` (requiere `gh`, se instala con
> `winget install GitHub.cli`).

La primera vez, marca los *packages* de GHCR como **públicos** para que las EC2
puedan hacer `pull` sin autenticarse.

---

## 4. Funciones serverless

### `lambdas/jwt-authorizer`

Validador externo del JWT. El API Gateway lo invoca antes de enrutar; si el token
falta, está expirado, tiene otra firma u otro emisor, devuelve `Unauthorized` y
API Gateway responde 401 sin tocar el backend.

| Variable | Valor |
|---|---|
| `JWT_SECRET` | El mismo secreto HS256 de `users-service` |
| `JWT_ISSUER` | `smartlogix-users` |

### `lambdas/shipping-service`

El microservicio de envíos. SQS lo invoca con los eventos de pedido creado y por
cada uno inserta un documento en la colección `envios` de MongoDB Atlas:

```json
{
  "pedidoId": 7,
  "usuarioId": 1,
  "estado": "EN_PREPARACION",
  "trackingCode": "SL-7-A3F91C20",
  "montoTotal": 25990.0,
  "items": [ { "productoId": 1, "cantidad": 2, "precioUnitario": 12995.0 } ],
  "fechaCreacion": "2026-11-22T13:40:11Z"
}
```

| Variable | Valor |
|---|---|
| `MONGODB_URI` | Cadena de conexión de Atlas |
| `MONGODB_DB` | `smartlogix` |

> La Lambda corre fuera de VPC, por lo que su IP de salida no es fija: en Atlas,
> **Network Access** debe permitir `0.0.0.0/0`.

Es idempotente: hay un índice único en `pedidoId`, de modo que una reentrega de SQS
no duplica el envío. Tras 3 intentos fallidos el mensaje pasa a la DLQ
`smartlogix-shipping-dlq`.

Despliegue y prueba manual:

```bash
cd lambdas/shipping-service && ./mvnw -B clean package
aws lambda update-function-code --function-name smartlogix-shipping \
  --zip-file fileb://target/shipping-service.jar

# Publicar un mensaje de prueba directamente en la cola
aws sqs send-message --queue-url "$SQS_QUEUE_URL" \
  --message-body '{"pedidoId":999,"usuarioId":1,"montoTotal":1000.0,"items":[]}'

aws logs tail /aws/lambda/smartlogix-shipping --follow
```

---

## 5. Prueba de punta a punta en la nube

```bash
API=$(aws cloudformation describe-stacks --stack-name smartlogix-infra \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)
Q=$(aws cloudformation describe-stacks --stack-name smartlogix-infra \
  --query "Stacks[0].Outputs[?OutputKey=='ShippingQueueUrl'].OutputValue" --output text)

# 1. Sin token: el Lambda Authorizer corta en el Gateway
curl -i "$API/api/products"                                  # 401

# 2. Login
TOKEN=$(curl -s -X POST "$API/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"admin@smartlogix.cl","password":"admin123"}' | jq -r .token)

# 3. Gateway -> Swarm -> Postgres
curl -s "$API/api/products" -H "Authorization: Bearer $TOKEN" | jq

# 4. Pedido: llamada síncrona a inventory + publicación asíncrona en SQS
curl -s -X POST "$API/api/orders" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"items":[{"productoId":1,"cantidad":2}]}' | jq

# 5. La cola se consume y la Lambda escribe en Atlas
aws sqs get-queue-attributes --queue-url "$Q" \
  --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible
aws logs tail /aws/lambda/smartlogix-shipping --follow
#   -> en Atlas: base "smartlogix", colección "envios", documento con ese pedidoId

# 6. Escalado en vivo
docker service scale smartlogix_orders-service=4
```

La colección de Postman `postman/EP3-smartlogix.postman_collection.json` automatiza
todo esto: el request de login guarda el token en la variable `{{token}}` y el resto
de peticiones lo reutilizan.

---

## 6. Estructura del repositorio

```
.
├── .github/workflows/ci-cd.yml          Pipeline CI/CD
├── docker-compose.yml                   Entorno local unificado
├── docker-stack.yml                     Stack de Docker Swarm
├── .env.example                         Plantilla de variables de entorno
├── ARQUITECTURA.md                      Justificación técnica
├── infra/
│   ├── cloudformation/smartlogix-infra.yml   IaC: SQS, Lambdas, API Gateway
│   └── scripts/                              swarm-init, join, deploy, scale, secrets
├── lambdas/
│   ├── jwt-authorizer/                  Validador de JWT del API Gateway
│   └── shipping-service/                Microservicio de envíos (FaaS)
├── users-service/                       Emite el JWT · puerto 3001
├── inventory-service/                   Productos y stock · puerto 3002
├── orders-service/                      Pedidos y productor SQS · puerto 3003
└── postman/                             Colección de pruebas
```

---

## 7. Desarrollo sin Docker

Cada módulo es un proyecto Maven independiente:

```bash
cd users-service
./mvnw spring-boot:run        # necesita un Postgres accesible en DB_HOST:DB_PORT
./mvnw verify                 # compila y ejecuta los tests
```

Requiere **JDK 17**. Si tienes un JDK más reciente instalado, apunta `JAVA_HOME` a un
17: la versión de Lombok que fija Spring Boot 3.3.5 no compila con JDK 22 o superior.
