# jwt-authorizer — Validador externo del JWT (AWS Lambda)

Lambda Authorizer de tipo **TOKEN** para el API Gateway REST de SmartLogix. Es la
primera línea de defensa: el Gateway la invoca **antes** de enrutar la petición, de
modo que un token ausente, expirado o mal firmado nunca llega a las instancias EC2.

```
Cliente ──Authorization: Bearer ...──> API Gateway ──invoca──> ESTA FUNCIÓN
                                            │                       │
                                            │   <──── Allow / Unauthorized
                                            ▼
                                   microservicios en EC2
```

## Configuración

| Variable de entorno | Obligatoria | Descripción |
|---|:---:|---|
| `JWT_SECRET` | sí | Secreto HS256. **Debe ser idéntico** al de `users-service` |
| `JWT_ISSUER` | no | Emisor esperado. Por defecto `smartlogix-users` |

| Ajuste | Valor |
|---|---|
| Runtime | `java17` |
| Handler | `cl.duoc.authorizer.JwtAuthorizerHandler::handleRequest` |
| Memoria | 512 MB |
| Timeout | 10 s |
| Rol | `LabRole` |
| Caché del authorizer | 300 s (`AuthorizerResultTtlInSeconds`) |

## Qué valida

1. Que la cabecera venga con el prefijo `Bearer `.
2. La **firma** HS256 contra el secreto compartido.
3. La **expiración** (`exp`).
4. El **emisor** (`iss` = `smartlogix-users`).

Si algo falla lanza una excepción con el mensaje exacto `Unauthorized`, que es la
convención por la que API Gateway responde **401**. Cualquier otro mensaje produciría
un 500 y ocultaría el motivo real.

## Qué devuelve

```json
{
  "principalId": "admin@smartlogix.cl",
  "policyDocument": {
    "Version": "2012-10-17",
    "Statement": [{
      "Action": "execute-api:Invoke",
      "Effect": "Allow",
      "Resource": "arn:aws:execute-api:us-east-1:123456789012:abc123/prod/*/*"
    }]
  },
  "context": { "email": "admin@smartlogix.cl", "rol": "ADMIN", "uid": "1" }
}
```

El `context` se reenvía al backend como cabeceras (`X-User-Role`, `X-User-Id`)
mediante el mapeo de integración definido en CloudFormation.

## El detalle que más se equivoca: el comodín del `Resource`

La política autoriza `.../prod/*/*`, **no** el `methodArn` concreto que llegó.

Es intencionado. Con la caché activada (300 s), API Gateway guarda la política
**indexada por token**. Si la política apuntara solo al método que disparó la
primera llamada, cualquier petición posterior a otra ruta con ese mismo token
recibiría un **403** durante toda la vida de la caché. Es un fallo difícil de
diagnosticar porque la primera petición siempre funciona.

El test `politicaUsaComodin` fija este comportamiento para que no se rompa sin querer.

## Build y tests

```bash
./mvnw -B clean package     # genera target/jwt-authorizer.jar (uber-jar)
./mvnw -B test              # 7 tests
```

Los tests cubren: token válido, firma de otro secreto, token expirado, emisor
distinto, cabecera sin `Bearer`, forma del comodín y ARN malformado.

## Despliegue

Normalmente lo hace el pipeline. A mano:

```bash
./mvnw -B clean package
aws lambda update-function-code \
  --function-name smartlogix-jwt-authorizer \
  --zip-file fileb://target/jwt-authorizer.jar
```

## Pruebas

```bash
# 1. Obtener un token real
API=$(aws cloudformation describe-stacks --stack-name smartlogix-infra \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)
TOKEN=$(curl -s -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"usuario":"admin@smartlogix.cl","password":"admin123"}' | jq -r .token)

# 2. Invocar la Lambda directamente, simulando el evento del Gateway
cat > /tmp/evento.json <<EOF
{"type":"TOKEN","authorizationToken":"Bearer $TOKEN",
 "methodArn":"arn:aws:execute-api:us-east-1:123456789012:abc123/prod/GET/api/products"}
EOF
aws lambda invoke --function-name smartlogix-jwt-authorizer \
  --payload fileb:///tmp/evento.json /tmp/salida.json
cat /tmp/salida.json        # debe traer Effect: Allow

# 3. A través del Gateway, que es la prueba que vale
curl -i "$API/api/products"                                    # 401
curl -i "$API/api/products" -H "Authorization: Bearer $TOKEN"   # 200
curl -i "$API/api/products" -H "Authorization: Bearer falso"    # 401
```

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| Todo devuelve 401, incluso con token recién emitido | `JWT_SECRET` no coincide con el de `users-service` |
| La primera petición va bien y las siguientes dan 403 | La política no usa comodín (ver arriba) |
| 500 en vez de 401 | El mensaje de la excepción no es exactamente `Unauthorized` |
| El `OPTIONS` del navegador devuelve 401 | Ese método tiene el authorizer asignado; el preflight debe ir con `AuthorizationType: NONE` |
| Cambias el secreto y sigue autorizando tokens viejos | Es la caché de 300 s; espera o baja `AuthorizerResultTtlInSeconds` a 0 |
