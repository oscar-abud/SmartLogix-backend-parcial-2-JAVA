#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Inicializa el nodo MANAGER del clúster Docker Swarm (IE7).
# Ejecutar UNA sola vez, por SSH, en la EC2 que hara de manager.
#
#   chmod +x infra/scripts/*.sh
#   DB_PASSWORD='...' ./infra/scripts/swarm-init-manager.sh
#
# Al terminar imprime el comando "docker swarm join" que hay que pegar en el
# nodo worker.
# ---------------------------------------------------------------------------
set -euo pipefail

DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo "==> Comprobando Docker"
if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker no esta instalado. En Amazon Linux 2023:" >&2
  echo "  sudo dnf install -y docker && sudo systemctl enable --now docker" >&2
  echo "  sudo usermod -aG docker \$USER   # y reconectar la sesion SSH" >&2
  exit 1
fi

# IP privada de la instancia: es la que usan los demas nodos para hablar con el
# manager. Se obtiene por IMDSv2, que es el modo por defecto en Amazon Linux 2023.
echo "==> Detectando la IP privada de esta instancia"
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
          -H "X-aws-ec2-metadata-token-ttl-seconds: 60" || true)
if [ -n "${TOKEN}" ]; then
  ADVERTISE_ADDR=$(curl -s -H "X-aws-ec2-metadata-token: ${TOKEN}" \
                     http://169.254.169.254/latest/meta-data/local-ipv4)
else
  ADVERTISE_ADDR=$(hostname -I | awk '{print $1}')
fi
echo "    IP privada: ${ADVERTISE_ADDR}"

echo "==> Inicializando Swarm"
if docker info 2>/dev/null | grep -q "Swarm: active"; then
  echo "    Este nodo ya forma parte de un Swarm, se omite el init."
else
  docker swarm init --advertise-addr "${ADVERTISE_ADDR}"
fi

echo "==> Creando el secret db_password"
if docker secret inspect db_password >/dev/null 2>&1; then
  echo "    Ya existe. Para rotarlo: docker secret rm db_password (hay que bajar el stack antes)."
else
  printf '%s' "${DB_PASSWORD}" | docker secret create db_password -
  echo "    Creado."
fi

cat <<BANNER

===========================================================================
 MANAGER LISTO

 1) En la EC2 WORKER, ejecuta:

      $(docker swarm join-token worker | grep 'docker swarm join')

    o bien, si prefieres el script:

      ./infra/scripts/swarm-join-worker.sh ${ADVERTISE_ADDR} $(docker swarm join-token -q worker)

 2) Vuelve a este nodo y comprueba que el worker aparece:

      docker node ls

 3) Despliega el stack:

      export JWT_SECRET='...'  SQS_QUEUE_URL='...'  TAG=latest
      ./infra/scripts/deploy-stack.sh

 RECORDATORIO sobre el Security Group: entre manager y worker deben estar
 abiertos  2377/tcp (gestion), 7946/tcp+udp (descubrimiento) y 4789/udp
 (red overlay VXLAN). Sin ellos los servicios arrancan pero no se ven.
===========================================================================
BANNER
