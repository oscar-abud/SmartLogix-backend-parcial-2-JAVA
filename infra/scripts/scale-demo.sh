#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Demostracion de escalabilidad en Docker Swarm (IE8).
# Sube las replicas de un servicio, muestra en que nodos quedaron, comprueba
# que el servicio sigue respondiendo, y vuelve al numero original.
#
#   ./scale-demo.sh                      # por defecto orders-service
#   ./scale-demo.sh inventory-service 5
# ---------------------------------------------------------------------------
set -euo pipefail

STACK_NAME="${STACK_NAME:-smartlogix}"
SERVICIO="${1:-orders-service}"
REPLICAS_ARRIBA="${2:-4}"
REPLICAS_BASE="${REPLICAS_BASE:-2}"
PUERTO="${PUERTO:-3003}"

FULL="${STACK_NAME}_${SERVICIO}"

echo "==> Estado inicial"
docker service ls --filter "name=${FULL}"

echo
echo "==> Escalando ${FULL} a ${REPLICAS_ARRIBA} replicas"
docker service scale "${FULL}=${REPLICAS_ARRIBA}"

echo
echo "==> Distribucion de las replicas entre los nodos del cluster"
docker service ps "${FULL}" --filter "desired-state=running" \
  --format "table {{.Name}}\t{{.Node}}\t{{.CurrentState}}"

echo
echo "==> El servicio sigue respondiendo durante el escalado"
curl -s -o /dev/null -w "   GET /actuator/health -> HTTP %{http_code}\n" \
  "http://localhost:${PUERTO}/actuator/health" || echo "   (ajusta PUERTO si el servicio no es orders-service)"

echo
echo "==> Volviendo a ${REPLICAS_BASE} replicas"
docker service scale "${FULL}=${REPLICAS_BASE}"
docker service ls --filter "name=${FULL}"
