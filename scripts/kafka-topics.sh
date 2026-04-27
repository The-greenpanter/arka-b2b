#!/bin/bash
# Initialize Kafka topics for ARKA B2B
# Uso: bash scripts/kafka-topics.sh
# Requiere que el contenedor arka-kafka esté corriendo

CONTAINER="arka-kafka"
KAFKA_BIN="/opt/kafka/bin"
BOOTSTRAP="localhost:9092"

TOPICS=(
  "catalog.product-validated"
  "provider.provider-validated"
  "provider.provider-rejected"
  "inventory.stock-created"
  "inventory.stock-reserved"
  "inventory.stock-released"
  "inventory.stock-low-alert"
  "cart.checkout-requested"
  "order.order-created"
  "order.order-completed"
  "payment.payment-processed"
  "payment.payment-failed"
)

for TOPIC in "${TOPICS[@]}"; do
  echo "Creating topic: $TOPIC"
  docker exec "$CONTAINER" "$KAFKA_BIN/kafka-topics.sh" \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$TOPIC" \
    --partitions 3 \
    --replication-factor 1
done

echo ""
echo "All topics created:"
docker exec "$CONTAINER" "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --list
