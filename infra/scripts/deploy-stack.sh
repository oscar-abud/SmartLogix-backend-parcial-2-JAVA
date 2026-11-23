#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Despliega (o actualiza) el stack SmartLogix en el clúster Swarm.
# Se ejecuta SIEMPRE en el nodo manager. Es idempotente: volver a lanzarlo
# aplica una actualizacion rolling, no recrea el stack desde cero.
#
#   export JWT_SECRET='...'
#   export SQS_QUEUE_URL='https://sqs.us-east-1.amazonaws.com/123/smartlogix-shipping-queue'
#   export TAG=abc1234              # SHA del commit; por defecto "latest"
#   ./deploy-stack.sh
# ---------------------------------------------------------------------------
set -euo pipefail

STACK_NAME="${STACK_NAME:-smartlogix}"
STACK_FILE="${STACK_FILE:-$(cd "$(dirname "$0")/../.." && pwd)/docker-stack.yml}"

if [ -z "${JWT_SECRET:-}" ]; then
  echo "ERROR: falta exportar JWT_SECRET (el mismo que usa la Lambda authorizer)." >&2
  exit 1
fi
if ! docker info 2>/dev/null | grep -q "Swarm: active"; then
  echo "ERROR: este nodo no esta en un Swarm. Ejecuta antes swarm-init-manager.sh" >&2
  exit 1
fi

export TAG="${TAG:-latest}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"
export SQS_QUEUE_URL="${SQS_QUEUE_URL:-}"
export AWS_REGION="${AWS_REGION:-us-east-1}"
export JWT_EXPIRATION_MS="${JWT_EXPIRATION_MS:-3600000}"

echo "==> Desplegando ${STACK_NAME} con TAG=${TAG}"
# --with-registry-auth propaga las credenciales del registro a los workers.
docker stack deploy -c "${STACK_FILE}" "${STACK_NAME}" --with-registry-auth --detach=false

echo
echo "==> Estado de los servicios"
docker stack services "${STACK_NAME}"

echo
echo "Si alguna replica queda en 0/2, revisa los logs con:"
echo "  docker service logs ${STACK_NAME}_orders-service --tail 50"
