#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Une esta EC2 al clúster Swarm como nodo WORKER (IE7).
#
#   ./swarm-join-worker.sh <IP_PRIVADA_DEL_MANAGER> <TOKEN_DE_WORKER>
#
# El token se obtiene en el manager con:  docker swarm join-token -q worker
# ---------------------------------------------------------------------------
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Uso: $0 <IP_PRIVADA_DEL_MANAGER> <TOKEN_DE_WORKER>" >&2
  exit 1
fi

MANAGER_IP="$1"
TOKEN="$2"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker no esta instalado en este nodo." >&2
  exit 1
fi

if docker info 2>/dev/null | grep -q "Swarm: active"; then
  echo "Este nodo ya pertenece a un Swarm. Para rehacerlo: docker swarm leave --force"
  exit 0
fi

echo "==> Uniendose al manager ${MANAGER_IP}:2377"
docker swarm join --token "${TOKEN}" "${MANAGER_IP}:2377"

echo
echo "Listo. Verifica desde el MANAGER con:  docker node ls"
