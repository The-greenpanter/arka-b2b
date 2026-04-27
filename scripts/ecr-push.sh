#!/bin/bash
# Push todas las imágenes ARKA a AWS ECR
# Estrategia: build JARs con Gradle en host (Java 17 en WSL, DNS ok),
#             luego empacar con Docker (sin necesidad de internet en contenedores).
# Uso: bash scripts/ecr-push.sh
# Requiere: aws cli configurado + Docker corriendo + Java 17 en PATH

set -e

ACCOUNT_ID="863771938027"
REGION="us-east-1"
ECR_URL="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PREBUILT_DOCKERFILE="$SCRIPT_DIR/Dockerfile.prebuilt"

# Microservicios con estructura Clean Architecture (JAR en applications/app-service/build/libs/)
MS_SERVICES=(
  "ms-catalog"
  "ms-inventory"
  "ms-provider"
  "ms-cart"
  "ms-order"
  "ms-payment"
  "ms-notifications"
  "ms-reporter"
)

echo "==> Autenticando Docker con ECR..."
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ECR_URL

echo ""
echo "==> Fase 1: Construyendo JARs con Gradle (en host)..."

for SERVICE in "${MS_SERVICES[@]}"; do
  echo ""
  echo "--- Gradle build: $SERVICE ---"
  cd "$SCRIPT_DIR/../$SERVICE"
  ./gradlew :app-service:bootJar -x test --no-daemon
  cd "$SCRIPT_DIR/.."
  JAR=$(ls "$SERVICE/applications/app-service/build/libs/$SERVICE.jar" 2>/dev/null || ls "$SERVICE/applications/app-service/build/libs/"*.jar 2>/dev/null | head -1)
  echo "✓ JAR: $JAR"
done

# api-gateway tiene estructura estándar Spring Boot (JAR en build/libs/)
echo ""
echo "--- Gradle build: api-gateway ---"
cd "$SCRIPT_DIR/../api-gateway"
./gradlew bootJar -x test --no-daemon
cd "$SCRIPT_DIR/.."

echo ""
echo "==> Fase 2: Construyendo y subiendo imágenes Docker a ECR..."

for SERVICE in "${MS_SERVICES[@]}"; do
  echo ""
  echo "--- Docker build+push: $SERVICE ---"
  JAR=$(ls "$SERVICE/applications/app-service/build/libs/$SERVICE.jar" 2>/dev/null || ls "$SERVICE/applications/app-service/build/libs/"*.jar 2>/dev/null | head -1)
  JAR_RELATIVE="${JAR#$SERVICE/}"

  docker build \
    -f "$PREBUILT_DOCKERFILE" \
    --build-arg JAR_FILE="$JAR_RELATIVE" \
    -t "arka/$SERVICE" \
    "./$SERVICE"

  docker tag "arka/$SERVICE:latest" "$ECR_URL/arka/$SERVICE:latest"
  docker push "$ECR_URL/arka/$SERVICE:latest"
  echo "✓ $SERVICE subido"
done

# api-gateway
echo ""
echo "--- Docker build+push: api-gateway ---"
GW_JAR=$(ls api-gateway/build/libs/api-gateway.jar 2>/dev/null || ls api-gateway/build/libs/*.jar | head -1)
GW_RELATIVE="${GW_JAR#api-gateway/}"

docker build \
  -f "$PREBUILT_DOCKERFILE" \
  --build-arg JAR_FILE="$GW_RELATIVE" \
  -t "arka/api-gateway" \
  "./api-gateway"

docker tag "arka/api-gateway:latest" "$ECR_URL/arka/api-gateway:latest"
docker push "$ECR_URL/arka/api-gateway:latest"
echo "✓ api-gateway subido"

# arka-frontend usa su propio Dockerfile (nginx, no Java)
echo ""
echo "--- Docker build+push: arka-frontend ---"
docker build -t "arka/arka-frontend" "./arka-frontend"
docker tag "arka/arka-frontend:latest" "$ECR_URL/arka/arka-frontend:latest"
docker push "$ECR_URL/arka/arka-frontend:latest"
echo "✓ arka-frontend subido"

echo ""
echo "=== Listo. Imágenes en ECR ==="
aws ecr describe-repositories \
  --region $REGION \
  --query 'repositories[].{Nombre:repositoryName,URI:repositoryUri}' \
  --output table
