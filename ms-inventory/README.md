# ms-inventory

Microservicio de inventario (stock management) para el proyecto ARKA. Participa en la saga HU1 (crear productos) y HU2 (reservar stock para órdenes).

## Arquitectura

Clean Architecture (Bancolombia scaffold) con capas Hexagonales:

```
applications/
  app-service/           # Spring Boot entry point (port 8082)
    config/              # Beans, in-memory repository
    resources/           # application.yaml, schema.sql
domain/
  model/                 # Agregado Stock, enums, puertos (sin Spring)
  usecase/              # Casos de uso (CreateStock, ReserveStock, etc.)
infrastructure/
  entry-points/
    reactive-web/       # WebFlux Router + Handler + DTOs
  driven-adapters/
    r2dbc-postgresql/  # R2DBC adapter (stock entities, repository impl)
```

## Stack Tecnológico

- **Java 21** + **Gradle 9.x**
- **Spring Boot 3.x** + **WebFlux** (reactivo)
- **R2DBC + PostgreSQL** (transaccional)
- **JUnit 5 + Mockito + StepVerifier** (testing reactivo)
- **Lombok** (boilerplate reduction)

## Agregado Stock

**Invariantes:**
- `availableQty >= 0`
- `reservedQty >= 0`
- Si `availableQty == 0` → `status = DEPLETED`
- Si `availableQty > 0` → `status = ACTIVE`

**Operaciones:**
- `reserve(qty)`: Reserva cantidad (la saca de available)
- `releaseReservation(qty)`: Libera una reserva (vuelve a available)
- `consumeReservation(qty)`: Consume una reserva (sale del sistema)
- `restock(qty)`: Añade cantidad (entrada de proveedor)

## Endpoints REST

```bash
# Crear stock (HU1 saga paso 2: ProductValidatedEvent → StockCreatedEvent)
POST /api/stocks
{
  "productId": "prod-001"
}
→ 201 Created { stockId, productId, availableQty: 0, status: ACTIVE }

# Consultar stock
GET /api/stocks/{productId}
→ 200 OK { stockId, productId, availableQty, reservedQty, totalQty, status }

# Reservar cantidad (HU2: crear orden)
POST /api/stocks/{productId}/reserve
{
  "quantity": 30
}
→ 200 OK { ... }
→ 409 Conflict si no hay suficiente o está DEPLETED

# Restock (entrada de proveedor, cancelación de orden, etc.)
POST /api/stocks/{productId}/restock
{
  "quantity": 100
}
→ 200 OK { ... }
```

## Configuración

### application.yaml

```yaml
server.port: 8082
spring.application.name: ms-inventory
arka.inventory.use-in-memory-repository: true   # Por defecto (sin PostgreSQL)
```

### Cambiar a PostgreSQL

1. Levantar PostgreSQL con la BD `arka_inventory`
2. Ejecutar `schema.sql` (migrations futuras: Flyway/Liquibase)
3. Cambiar en `application.yaml`:
   ```yaml
   arka.inventory.use-in-memory-repository: false
   spring.r2dbc.url: r2dbc:postgresql://localhost:5432/arka_inventory
   ```

## Tests

```bash
# Tests del dominio (state machine): rápidos, sin Spring
./gradlew :model:test

# Tests de use cases: con Mockito + StepVerifier (reactivo)
./gradlew :usecase:test

# Tests de API (RouterRest): WebFluxTest + WebTestClient
./gradlew :reactive-web:test

# Todos
./gradlew test
./gradlew jacocoMergedReport
./gradlew pitest
```

## HU1 Saga (ProductValidatedEvent → StockCreatedEvent)

1. **ms-catalog** registra un Product (EN_CREACION)
2. **ms-catalog** publica `ProductValidatedEvent`
3. **ms-inventory** recibe el evento → `CreateStockUseCase.execute(productId)`
4. **ms-inventory** crea Stock con `availableQty=0, status=ACTIVE`
5. **ms-inventory** publica `StockCreatedEvent` (implementar en siguiente sprint)
6. **ms-catalog** recibe `StockCreatedEvent` → transiciona Product a CONFIRMADO

*Nota: Por ahora, sin Kafka/eventos. Los use cases están listos para integración.*

## HU2 Saga (Ordering - Reserva de Stock)

1. **ms-ordering** recibe pedido con `productId, quantity`
2. **ms-ordering** llama `POST /api/stocks/{productId}/reserve` con `quantity`
3. **ms-inventory** reserva → `availableQty -= qty, reservedQty += qty`
4. Si éxito: **ms-ordering** continúa con procesamiento de pago
5. Si falla: **ms-ordering** maneja error (404, 409)
6. Si pago falla: **ms-ordering** llama `POST /api/stocks/{productId}/restock` para liberar

## Diagrama de Estados (Stock)

```
    ┌─────────────────────────┐
    │      ACTIVE             │
    │  (availableQty > 0)     │
    └──────────┬──────────────┘
               │
        reserve(qty_total)
               │
               ▼
    ┌─────────────────────────┐
    │      DEPLETED           │
    │   (availableQty = 0)    │
    └──────────┬──────────────┘
               │
        releaseReservation()
               │
               ▼
    ┌─────────────────────────┐
    │  vuelve a ACTIVE        │
    └─────────────────────────┘

O restock() en cualquier momento → ACTIVE
```

## Próximos Pasos

- [ ] Integración con Kafka (evento ProductValidatedEvent → StockCreatedEvent)
- [ ] Implementar StockMovementRepository para auditoría
- [ ] Agregar métodos de cancelación con compensación
- [ ] R2DBC migrations (Flyway)
- [ ] Health checks y métricas Prometheus
- [ ] Integración con Circuit Breaker (Resilience4j)
- [ ] Saga choreography con Temporal/Axon (si se requiere)

## Versión

- **ms-inventory v1.0.0** (HU1 + HU2 MVP)
- Java 21, Spring Boot 3.x, Gradle 9.2.1
