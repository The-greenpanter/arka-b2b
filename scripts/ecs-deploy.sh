#!/bin/bash
# Deploy ARKA a AWS ECS Fargate con Cloud Map service discovery
# Uso: bash scripts/ecs-deploy.sh
set -e

REGION="us-east-1"
ACCOUNT_ID="863771938027"
ECR="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
CLUSTER="arka-cluster"
EXEC_ROLE="arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole"
SG="sg-0eb87b2e7a57c3261"
# Usar 2 subnets públicas
SUBNETS="subnet-029ccc07decf4782b,subnet-0cf6f8648690bec38"
LOG_GROUP="/arka"

# Cloud Map service IDs (creados previamente)
declare -A SD_SVC
SD_SVC[kafka]="srv-topxtpqlaw6i36kk"
SD_SVC[mongodb]="srv-rlmv6hmvnm77shi5"
SD_SVC[postgresql]="srv-ltwschv2xmodcm2v"
SD_SVC[ms-catalog]="srv-3kchnpowk6j5qgf3"
SD_SVC[ms-inventory]="srv-ldagjblfb2hp34y3"
SD_SVC[ms-provider]="srv-psgsulqpjyexb3vi"
SD_SVC[ms-cart]="srv-ysqpz5qxbmvgtn7r"
SD_SVC[ms-order]="srv-we6wambe5iduidpw"
SD_SVC[ms-payment]="srv-s22q5qbouzowwdp5"
SD_SVC[ms-notifications]="srv-tnvjzpiox3eepbml"
SD_SVC[ms-reporter]="srv-2ea6lzre2r4wqn7s"
SD_SVC[api-gateway]="srv-aignfrf53wsc64wj"
SD_SVC[frontend]="srv-g4tgutwywznf6c2n"

log_def() {
  local NAME=$1
  echo "{\"logDriver\":\"awslogs\",\"options\":{\"awslogs-group\":\"$LOG_GROUP\",\"awslogs-region\":\"$REGION\",\"awslogs-stream-prefix\":\"$NAME\"}}"
}

register_task() {
  local NAME=$1
  local DEF=$2
  aws ecs register-task-definition \
    --family "arka-$NAME" \
    --network-mode awsvpc \
    --requires-compatibilities FARGATE \
    --execution-role-arn "$EXEC_ROLE" \
    --region "$REGION" \
    --cli-input-json "$DEF" \
    --query 'taskDefinition.taskDefinitionArn' --output text
}

create_service() {
  local NAME=$1
  local PORT=$2
  local SD_ID=${SD_SVC[$NAME]}

  aws ecs create-service \
    --cluster "$CLUSTER" \
    --service-name "arka-$NAME" \
    --task-definition "arka-$NAME" \
    --desired-count 1 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=ENABLED}" \
    --service-registries "[{\"registryArn\":\"arn:aws:servicediscovery:$REGION:$ACCOUNT_ID:service/$SD_ID\",\"containerName\":\"$NAME\",\"containerPort\":$PORT}]" \
    --region "$REGION" \
    --query 'service.{Name:serviceName,Status:status}' --output table 2>/dev/null || \
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "arka-$NAME" \
    --task-definition "arka-$NAME" \
    --region "$REGION" \
    --query 'service.{Name:serviceName,Status:status}' --output table
}

echo "====== ARKA ECS DEPLOY ======"
echo ""

# ==== KAFKA ====
echo "--- Kafka ---"
KAFKA_DEF=$(cat << EOF
{
  "cpu": "512", "memory": "1024",
  "containerDefinitions": [{
    "name": "kafka",
    "image": "apache/kafka:3.8.0",
    "portMappings": [{"containerPort":9092},{"containerPort":9093}],
    "environment": [
      {"name":"KAFKA_NODE_ID","value":"1"},
      {"name":"KAFKA_PROCESS_ROLES","value":"broker,controller"},
      {"name":"KAFKA_LISTENERS","value":"PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093"},
      {"name":"KAFKA_ADVERTISED_LISTENERS","value":"PLAINTEXT://kafka.arka.local:9092"},
      {"name":"KAFKA_CONTROLLER_LISTENER_NAMES","value":"CONTROLLER"},
      {"name":"KAFKA_CONTROLLER_QUORUM_VOTERS","value":"1@kafka.arka.local:9093"},
      {"name":"KAFKA_LISTENER_SECURITY_PROTOCOL_MAP","value":"CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"},
      {"name":"KAFKA_INTER_BROKER_LISTENER_NAME","value":"PLAINTEXT"},
      {"name":"KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR","value":"1"},
      {"name":"KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR","value":"1"},
      {"name":"KAFKA_TRANSACTION_STATE_LOG_MIN_ISR","value":"1"},
      {"name":"KAFKA_AUTO_CREATE_TOPICS_ENABLE","value":"true"},
      {"name":"CLUSTER_ID","value":"MkU3OEVBNTcwNTJENDM2Qk"},
      {"name":"KAFKA_HEAP_OPTS","value":"-Xmx512m -Xms256m"}
    ],
    "logConfiguration": $(log_def kafka),
    "essential": true
  }]
}
EOF
)
ARN=$(register_task "kafka" "$KAFKA_DEF")
echo "✓ Task: $ARN"
create_service "kafka" 9092

# ==== MONGODB ====
echo ""
echo "--- MongoDB ---"
MONGO_DEF=$(cat << EOF
{
  "cpu": "512", "memory": "1024",
  "containerDefinitions": [{
    "name": "mongodb",
    "image": "mongo:7",
    "portMappings": [{"containerPort":27017}],
    "environment": [
      {"name":"MONGO_INITDB_ROOT_USERNAME","value":"arka"},
      {"name":"MONGO_INITDB_ROOT_PASSWORD","value":"arka123"},
      {"name":"MONGO_INITDB_DATABASE","value":"arka_catalog"}
    ],
    "logConfiguration": $(log_def mongodb),
    "essential": true
  }]
}
EOF
)
ARN=$(register_task "mongodb" "$MONGO_DEF")
echo "✓ Task: $ARN"
create_service "mongodb" 27017

# ==== POSTGRESQL ====
echo ""
echo "--- PostgreSQL ---"
PG_DEF=$(cat << EOF
{
  "cpu": "512", "memory": "1024",
  "containerDefinitions": [{
    "name": "postgresql",
    "image": "$ECR/arka/postgresql:latest",
    "portMappings": [{"containerPort":5432}],
    "environment": [
      {"name":"POSTGRES_USER","value":"arka"},
      {"name":"POSTGRES_PASSWORD","value":"arka123"},
      {"name":"POSTGRES_MULTIPLE_DATABASES","value":"arka_inventory,arka_order,arka_payment,arka_provider,arka_reporter"}
    ],
    "logConfiguration": $(log_def postgresql),
    "essential": true
  }]
}
EOF
)
ARN=$(register_task "postgresql" "$PG_DEF")
echo "✓ Task: $ARN"
create_service "postgresql" 5432

echo ""
echo "==> Infraestructura creada. Esperando 30s para que arranquen..."
sleep 30

# ==== MICROSERVICIOS ====
declare -A MS_CONFIG
# name:port:extra_env
MS_CONFIG[ms-catalog]="8081:MONGODB_URI=mongodb://arka:arka123@mongodb.arka.local:27017/arka_catalog,ARKA_CATALOG_USE_IN_MEMORY_REPOSITORY=false,ARKA_CATALOG_USE_IN_MEMORY_BROKER=false"
MS_CONFIG[ms-inventory]="8082:R2DBC_URL=r2dbc:postgresql://postgresql.arka.local:5432/arka_inventory,DB_USER=arka,DB_PASSWORD=arka123,ARKA_INVENTORY_USE_IN_MEMORY_REPOSITORY=false,ARKA_INVENTORY_USE_IN_MEMORY_BROKER=false"
MS_CONFIG[ms-provider]="8085:R2DBC_URL=r2dbc:postgresql://postgresql.arka.local:5432/arka_provider,DB_USER=arka,DB_PASSWORD=arka123"
MS_CONFIG[ms-cart]="8086:MONGODB_URI=mongodb://arka:arka123@mongodb.arka.local:27017/arka_cart"
MS_CONFIG[ms-order]="8083:R2DBC_URL=r2dbc:postgresql://postgresql.arka.local:5432/arka_order,DB_USER=arka,DB_PASSWORD=arka123"
MS_CONFIG[ms-payment]="8084:R2DBC_URL=r2dbc:postgresql://postgresql.arka.local:5432/arka_payment,DB_USER=arka,DB_PASSWORD=arka123"
MS_CONFIG[ms-notifications]="8087:"
MS_CONFIG[ms-reporter]="8088:R2DBC_URL=r2dbc:postgresql://postgresql.arka.local:5432/arka_reporter,DB_USER=arka,DB_PASSWORD=arka123"

for MS in ms-catalog ms-inventory ms-provider ms-cart ms-order ms-payment ms-notifications ms-reporter; do
  echo ""
  echo "--- $MS ---"
  IFS=':' read -r PORT EXTRA_ENV <<< "${MS_CONFIG[$MS]}"

  ENV_JSON='[{"name":"SPRING_PROFILES_ACTIVE","value":"docker"},{"name":"KAFKA_BOOTSTRAP_SERVERS","value":"kafka.arka.local:9092"},{"name":"JAVA_OPTS","value":"-Xmx192m -Xms64m"}'
  if [ -n "$EXTRA_ENV" ]; then
    IFS=',' read -ra ENVS <<< "$EXTRA_ENV"
    for ENV in "${ENVS[@]}"; do
      KEY="${ENV%%=*}"; VAL="${ENV#*=}"
      ENV_JSON="$ENV_JSON,{\"name\":\"$KEY\",\"value\":\"$VAL\"}"
    done
  fi
  ENV_JSON="$ENV_JSON]"

  MS_DEF=$(cat << EOF
{
  "cpu": "256", "memory": "512",
  "containerDefinitions": [{
    "name": "$MS",
    "image": "$ECR/arka/$MS:latest",
    "portMappings": [{"containerPort":$PORT}],
    "environment": $ENV_JSON,
    "logConfiguration": $(log_def $MS),
    "essential": true
  }]
}
EOF
)
  ARN=$(register_task "$MS" "$MS_DEF")
  echo "✓ Task: $ARN"
  create_service "$MS" $PORT
done

# ==== API GATEWAY ====
echo ""
echo "--- api-gateway ---"
GW_DEF=$(cat << EOF
{
  "cpu": "256", "memory": "512",
  "containerDefinitions": [{
    "name": "api-gateway",
    "image": "$ECR/arka/api-gateway:latest",
    "portMappings": [{"containerPort":8080}],
    "environment": [
      {"name":"SPRING_PROFILES_ACTIVE","value":"docker"},
      {"name":"CATALOG_URL","value":"http://ms-catalog.arka.local:8081"},
      {"name":"INVENTORY_URL","value":"http://ms-inventory.arka.local:8082"},
      {"name":"ORDER_URL","value":"http://ms-order.arka.local:8083"},
      {"name":"PAYMENT_URL","value":"http://ms-payment.arka.local:8084"},
      {"name":"PROVIDER_URL","value":"http://ms-provider.arka.local:8085"},
      {"name":"CART_URL","value":"http://ms-cart.arka.local:8086"},
      {"name":"REPORTER_URL","value":"http://ms-reporter.arka.local:8088"},
      {"name":"NOTIFICATIONS_URL","value":"http://ms-notifications.arka.local:8087"},
      {"name":"JAVA_OPTS","value":"-Xmx192m -Xms64m"}
    ],
    "logConfiguration": $(log_def api-gateway),
    "essential": true
  }]
}
EOF
)
ARN=$(register_task "api-gateway" "$GW_DEF")
echo "✓ Task: $ARN"
create_service "api-gateway" 8080

# ==== FRONTEND ====
echo ""
echo "--- frontend ---"
FE_DEF=$(cat << EOF
{
  "cpu": "256", "memory": "512",
  "containerDefinitions": [{
    "name": "frontend",
    "image": "$ECR/arka/arka-frontend:latest",
    "portMappings": [{"containerPort":80}],
    "logConfiguration": $(log_def frontend),
    "essential": true
  }]
}
EOF
)
ARN=$(register_task "frontend" "$FE_DEF")
echo "✓ Task: $ARN"
create_service "frontend" 80

echo ""
echo "====== DEPLOY COMPLETO ======"
echo "Verificando servicios en cluster arka-cluster..."
aws ecs list-services --cluster arka-cluster --region us-east-1 --output table
