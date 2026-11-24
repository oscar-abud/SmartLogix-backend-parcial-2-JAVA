# Justificación técnica de la arquitectura

Documento de apoyo para la EP3. Explica **por qué** se tomó cada decisión, no solo
qué se hizo, con foco en escalabilidad, disponibilidad y mantenibilidad.

---

## 1. Por qué Docker Swarm (y no ECS, EKS o contenedores sueltos)

| Opción | Por qué se descartó / eligió |
|---|---|
| Contenedores sueltos con `docker run` | No hay réplicas, ni reinicio automático, ni balanceo. Un fallo deja el servicio caído. |
| **Docker Swarm** ✅ | Viene incluido en Docker, el `docker-stack.yml` es casi el mismo `docker-compose.yml` que ya se usa en local, y se monta sobre las EC2 que el laboratorio permite. Da réplicas, *healthchecks*, *rolling updates* y balanceo sin ninguna pieza extra. |
| Amazon ECS | Gestionado y potente, pero la definición de tareas no se parece al compose local: habría dos descripciones distintas del mismo sistema que mantener sincronizadas. |
| Amazon EKS | Kubernetes resuelve problemas de una escala que este sistema no tiene. El coste de operación y de aprendizaje no se justifica para tres servicios. |

El criterio decisivo fue la **paridad entre desarrollo y producción**: lo que se
prueba en local con `docker compose up` es, salvo el bloque `deploy`, exactamente
lo que corre en el clúster.

---

## 2. Escalabilidad

**Escalado horizontal declarativo.** Cada microservicio declara `replicas: 2` y se
escala con un solo comando, sin reiniciar nada:

```bash
docker service scale smartlogix_orders-service=4
```

**Routing mesh.** Los puertos se publican en modo `ingress`: cualquier nodo del
clúster acepta tráfico en el 3003 y Swarm lo reparte entre todas las réplicas, estén
donde estén. El API Gateway solo necesita conocer **una** dirección (la Elastic IP
del manager) y el balanceo ocurre dentro del clúster. Añadir un tercer nodo no
obliga a tocar el API Gateway.

**Escalado independiente por servicio.** `orders-service` recibe mucho más tráfico
que `users-service`, y puede escalarse por separado. Es la ventaja concreta de haber
separado en microservicios en vez de tener un monolito.

**La cola absorbe los picos.** Si llegan 500 pedidos de golpe, `orders-service` solo
tiene que escribir en Postgres y encolar; la Lambda de envíos los procesa a su ritmo
y AWS escala sus instancias automáticamente. Sin la cola, un pico de pedidos sería
un pico de escrituras en MongoDB Atlas, que es el eslabón más lento.

**Límite asumido:** las bases de datos no escalan horizontalmente en este diseño
(ver §5).

---

## 3. Disponibilidad

**Réplicas repartidas.** Con dos réplicas por servicio y dos nodos, Swarm distribuye
las tareas entre ambos. Si una EC2 se cae, el servicio sigue atendiendo desde la otra.

**Reinicio automático.** `restart_policy: on-failure` con 3 intentos: si un
contenedor muere, Swarm lo reemplaza sin intervención.

**Despliegue sin corte.** La política de actualización es:

```yaml
update_config:
  parallelism: 1          # una réplica a la vez
  order: start-first      # arranca la nueva ANTES de bajar la vieja
  failure_action: rollback
```

Durante un despliegue siempre queda al menos una réplica atendiendo. Si la versión
nueva no pasa su *healthcheck*, Swarm **revierte sola** a la anterior.

**Healthchecks reales.** Cada imagen consulta `/actuator/health`. Swarm no envía
tráfico a una réplica hasta que su aplicación responde, no solo cuando el proceso
arranca: con Spring Boot la diferencia son 40-60 segundos de peticiones fallidas.

**Degradación elegante.** `orders-service` trata la caída de `inventory-service`
como un 503 explícito, no como un error 500 genérico. Y si SQS falla, el pedido se
crea igualmente: la publicación del evento registra el error pero nunca lo propaga,
porque perder una venta por un problema de mensajería sería peor que retrasar un envío.

**Reintentos y DLQ.** Si la Lambda de envíos falla, SQS reintrega el mensaje hasta
3 veces y después lo deja en `smartlogix-shipping-dlq`. Nada se pierde en silencio,
y la DLQ sirve de bandeja de diagnóstico.

---

## 4. Mantenibilidad

**Una sola descripción del sistema.** `docker-stack.yml` describe los seis servicios,
sus redes, volúmenes, secretos y políticas. Desplegar es aplicar ese archivo; no hay
pasos manuales que recordar ni documentar.

**Infraestructura como código.** SQS, las Lambdas y el API Gateway completo viven en
`infra/cloudformation/smartlogix-infra.yml`. El stack es idempotente: aplicarlo dos
veces no duplica nada. La infraestructura se revisa en un *pull request* igual que
el código, y se puede recrear desde cero en otra cuenta cambiando parámetros.

**Imágenes inmutables versionadas por commit.** Cada imagen se etiqueta con el SHA
(`smartlogix-orders-service:a1b2c3d`). Se sabe exactamente qué código corre en
producción y revertir es cambiar una variable:

```bash
TAG=<sha-anterior> ./infra/scripts/deploy-stack.sh
# o, más rápido:
docker service rollback smartlogix_orders-service
```

**Configuración fuera de la imagen.** Puertos, credenciales, URLs y secretos entran
por variables de entorno. La **misma imagen** corre en local y en la nube; lo único
que cambia es el entorno. Es lo que hace creíble la prueba local.

**Separación de responsabilidades en la seguridad.** El JWT lo emite un solo servicio
(`users-service`); los demás solo lo validan. Para cambiar el algoritmo o la duración
del token hay un único sitio que tocar.

**Contrato explícito entre productor y consumidor.** `OrderCreatedEvent` existe a
ambos lados de la cola y ambos ignoran los campos desconocidos: se pueden añadir
campos al evento sin desplegar los dos lados a la vez.

---

## 5. Limitaciones asumidas y cómo se resolverían

Vale la pena nombrarlas: una arquitectura sin compromisos explícitos suele ser una
arquitectura mal entendida.

| Limitación | Por qué está así | Cómo se resolvería en producción |
|---|---|---|
| **Las bases de datos están ancladas al manager** (`node.role == manager`) | Los volúmenes de Docker son locales a cada nodo; si Postgres migrara de nodo, encontraría un disco vacío. | **Amazon RDS**: almacenamiento gestionado, *backups* automáticos y réplicas Multi-AZ. Los microservicios ya leen el host por variable de entorno, así que el cambio es de configuración, no de código. |
| **Un solo nodo manager** | Dos EC2 es lo razonable con el crédito del laboratorio. | Tres managers, que es el mínimo para que el quórum de Raft tolere la caída de uno. |
| **Los puertos 3001-3003 son accesibles directamente** | El `HTTP_PROXY` del API Gateway sale por Internet y no tiene un rango de IPs fijo que permita cerrar el Security Group. | **VPC Link + Network Load Balancer interno**: las EC2 pasan a subred privada, sin IP pública. Mitigación actual: los microservicios validan el JWT por su cuenta, así que el acceso directo tampoco está abierto. |
| **El secreto del JWT viaja como variable de entorno** | Learner Lab restringe la creación de roles y políticas IAM. | **AWS Secrets Manager** o SSM Parameter Store con rotación automática. |
| **HS256 (secreto compartido)** | Simple: un solo valor en los tres servicios y en la Lambda. | **RS256**: `users-service` firma con la clave privada y los validadores solo necesitan la pública, expuesta en un endpoint JWKS. Un servicio comprometido ya no podría emitir tokens. |
| **El pedido no descuenta stock** | Viene heredado del alcance de la EP2. | Un segundo consumidor del mismo evento (SNS *fan-out* o una segunda cola) que ajuste el inventario de forma asíncrona. |

---

## 6. Por qué una cola entre pedidos y envíos

La alternativa era que `orders-service` llamara al servicio de envíos por HTTP, como
ya hace con `inventory-service`. Se eligió la cola por cuatro razones concretas:

1. **Desacople temporal.** Si MongoDB Atlas o la Lambda están caídos, el pedido se
   crea igual; el mensaje espera en la cola y se procesa cuando el servicio vuelva.
   Con una llamada HTTP síncrona, una caída de Atlas tumbaría la creación de pedidos.
2. **Latencia.** El cliente recibe su confirmación en cuanto el pedido está en
   Postgres, sin esperar a que se genere el envío. La parte lenta ocurre después.
3. **Reintentos gratis.** SQS reintenta solo y tiene DLQ. Implementar eso en el
   cliente HTTP sería código propio que mantener y probar.
4. **Extensible sin tocar `orders-service`.** Mañana se pueden sumar un consumidor
   de facturación y otro de notificaciones al mismo evento. El productor no se entera.

La llamada a `inventory-service`, en cambio, **sí** es síncrona a propósito: el
precio se necesita *antes* de poder calcular el total del pedido. Esa es la regla
para elegir entre ambos estilos: síncrono cuando el resultado es necesario para
responder, asíncrono cuando es una consecuencia del hecho ya confirmado.

---

## 7. Por qué el JWT se valida dos veces

Podría parecer redundante que lo validen el API Gateway y además cada microservicio.
Es deliberado, y cada capa cubre un hueco distinto de la otra:

- **En el Gateway** (Lambda authorizer): detiene el tráfico no autenticado en el
  borde, antes de consumir CPU de las EC2. Además el resultado se cachea 300 s por
  token, así que el coste por petición es casi nulo.
- **En cada microservicio**: los puertos 3001-3003 son alcanzables desde Internet
  (ver §5). Sin esta segunda validación, cualquiera que supiera la IP del manager
  entraría saltándose el Gateway.

Quitar cualquiera de las dos capas dejaría un agujero real. Es el principio de
*defensa en profundidad*: ninguna capa es el único punto de fallo de la seguridad.

---

## 8. Decisiones del Dockerfile

Las imágenes son multi-etapa y buscan tres cosas:

- **Tamaño.** Solo el JRE en la imagen final, sobre Alpine: unos 190 MB en vez de
  los ~500 MB de una imagen con JDK y Maven. Menos peso es *pull* más rápido en cada
  despliegue y menos superficie de ataque.
- **Velocidad de build.** Las dependencias se descargan en una capa propia, antes de
  copiar el código. Mientras no cambie el `pom.xml`, Docker reutiliza esa capa. Y el
  jar se descomprime en las capas de Spring Boot, de modo que un cambio de código
  solo invalida la capa de aplicación, que pesa unos pocos KB.
- **Seguridad.** El proceso corre como el usuario `smartlogix`, no como root. Si
  alguien lograra ejecutar código dentro del contenedor, no sería administrador.

Además, `-XX:MaxRAMPercentage=70` hace que la JVM respete el límite de memoria del
contenedor. Sin esa opción, la JVM mira la RAM del host y reserva un *heap* mayor
que el límite del servicio, y el contenedor acaba muriendo por OOM bajo carga.
