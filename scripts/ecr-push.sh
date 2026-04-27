#!/bin/bash
# Push todas las imágenes ARKA a AWS ECR
# Uso: bash scripts/ecr-push.sh
# Requiere: aws cli configurado + Docker corriendo

set -e

ACCOUNT_ID="863771938027"
REGION="us-east-1"
ECR_URL="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

SERVICES=(
  "ms-catalog"
  "ms-inventory"
  "ms-provider"
  "ms-cart"
  "ms-order"
  "ms-payment"
  "ms-notifications"
  "ms-reporter"
  "api-gateway"
  "arka-frontend"
)

echo "==> Autenticando Docker con ECR..."
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ECR_URL

echo ""
echo "==> Construyendo y subiendo imágenes..."

for SERVICE in "${SERVICES[@]}"; do
  echo ""
  echo "--- $SERVICE ---"

  if [ "$SERVICE" = "arka-frontend" ]; then
    CONTEXT_DIR="./arka-frontend"
  else
    CONTEXT_DIR="./$SERVICE"
  fi

  docker build -t "arka/$SERVICE" "$CONTEXT_DIR"
  docker tag "arka/$SERVICE:latest" "$ECR_URL/arka/$SERVICE:latest"
  docker push "$ECR_URL/arka/$SERVICE:latest"

  echo "✓ $SERVICE subido"
done

echo ""
echo "=== Listo. Imágenes en ECR ==="
aws ecr describe-repositories \
  --region $REGION \
  --query 'repositories[].{Nombre:repositoryName,URI:repositoryUri}' \
  --output table
