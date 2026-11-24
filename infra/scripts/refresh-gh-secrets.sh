#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Refresca en GitHub los secrets de AWS que caducan en AWS Academy Learner Lab.
#
# Las credenciales del laboratorio duran lo que dura la sesion (unas 4 horas) y
# cambian cada vez que se reinicia. Si el pipeline falla con
# "ExpiredToken" o "InvalidClientTokenId", es esto lo que hay que ejecutar.
#
# Requisitos: GitHub CLI autenticado (gh auth login).
#   En Windows:  winget install GitHub.cli
#
# Uso:
#   1. En el laboratorio, abrir "AWS Details" > "AWS CLI" y copiar el bloque.
#   2. Pegarlo en ~/.aws/credentials (perfil "default").
#   3. Ejecutar:  ./infra/scripts/refresh-gh-secrets.sh
#
# Alternativa sin gh: copiar los tres valores a mano en
#   GitHub > Settings > Secrets and variables > Actions
# ---------------------------------------------------------------------------
set -euo pipefail

REPO="${REPO:-oscar-abud/SmartLogix-backend-parcial-2-JAVA}"
PERFIL="${AWS_PROFILE:-default}"

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: falta GitHub CLI. Instalalo con: winget install GitHub.cli" >&2
  exit 1
fi
if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR: falta el AWS CLI." >&2
  exit 1
fi

echo "==> Leyendo las credenciales del perfil '${PERFIL}'"
KEY_ID=$(aws configure get aws_access_key_id       --profile "${PERFIL}")
SECRET=$(aws configure get aws_secret_access_key   --profile "${PERFIL}")
SESSION=$(aws configure get aws_session_token      --profile "${PERFIL}" || true)
REGION=$(aws configure get region --profile "${PERFIL}" || echo "us-east-1")

if [ -z "${KEY_ID}" ] || [ -z "${SECRET}" ]; then
  echo "ERROR: no hay credenciales en el perfil '${PERFIL}'." >&2
  exit 1
fi
if [ -z "${SESSION}" ]; then
  echo "AVISO: no hay aws_session_token. En Learner Lab siempre deberia haberlo." >&2
fi

echo "==> Comprobando que las credenciales son validas"
aws sts get-caller-identity --profile "${PERFIL}" --output table

echo "==> Actualizando los secrets de ${REPO}"
printf '%s' "${KEY_ID}"  | gh secret set AWS_ACCESS_KEY_ID     --repo "${REPO}"
printf '%s' "${SECRET}"  | gh secret set AWS_SECRET_ACCESS_KEY --repo "${REPO}"
printf '%s' "${SESSION}" | gh secret set AWS_SESSION_TOKEN     --repo "${REPO}"
printf '%s' "${REGION}"  | gh secret set AWS_REGION            --repo "${REPO}"

echo
echo "Listo. Tambien conviene revisar que siga siendo correcto:"
echo "  EC2_MANAGER_HOST  (cambia si reinicias la EC2 sin Elastic IP)"
echo "  LAB_ROLE_ARN      (cambia si cambia la cuenta del laboratorio)"
echo
echo "Obtener el ARN del rol:"
echo "  aws iam get-role --role-name LabRole --query Role.Arn --output text"
