# ms-inventory Quick Start

## Project Structure at a Glance

```
ms-inventory/
├── settings.gradle                    # Module configuration (app-service, model, usecase, reactive-web, r2dbc-postgresql)
├── build.gradle                       # Root build, Sonar, Jacoco, PIT config
├── main.gradle                        # Subprojects common config (Java 21, Lombok, Reactor)
├── applications/
│   └── app-service/                   # Spring Boot entry point (port 8082)
│       ├── build.gradle
│       └── src/main/
│           ├── java/co/com/bancolombia/
│           │   ├── MainApplication.java
│           │   └── config/
│           │       ├── UseCasesConfig.java       # ComponentScan for UseCase beans
│           │       └── InMemoryStockRepository.java
│           └── resources/
│               ├── application.yaml              # Config (use-in-memory-repository toggle)
│               ├── schema.sql                    # PostgreSQL DDL (stock + stock_movement tables)
│               └── log4j2.properties             # Logging
├── domain/
│   ├── model/                         # Domain logic (ZERO Spring dependencies)
│   │   ├── build.gradle
│   │   └── src/main/java/co/com/bancolombia/model/stock/
│   │       ├── Stock.java             # Aggregate root with state machine
│   │       ├── StockStatus.java       # Enum: ACTIVE, DEPLETED
│   │       ├── StockMovement.java     # Value object for audit
│   │       └── gateways/
│   │           └── StockRepository.java    # Output port interface
│   │   └── src/test/java/...
│   │       └── StockTest.java         # 40+ pure unit tests
│   │
│   └── usecase/                       # Business orchestration
│       ├── build.gradle
│       └── src/main/java/co/com/bancolombia/usecase/stock/
│           ├── CreateStockUseCase.java    # HU1: creates stock (availableQty=0)
│           ├── GetStockUseCase.java       # Query by productId
│           ├── ReserveStockUseCase.java   # HU2: reserves qty
│           └── RestockUseCase.java        # Adds qty to available
│       └── src/test/java/...
│           └── ReserveStockUseCaseTest.java
│
└── infrastructure/
    ├── entry-points/
    │   └── reactive-web/              # HTTP layer (WebFlux functional routing)
    │       ├── build.gradle
    │       └── src/main/java/co/com/bancolombia/api/
    │           ├── RouterRest.java    # @Bean RouterFunction (POST, GET, POST)
    │           ├── Handler.java       # Request → UseCase → Response
    │           └── dto/
    │               ├── StockRequest.java
    │               └── StockResponse.java
    │       └── src/test/java/...
    │           └── RouterRestTest.java
    │
    └── driven-adapters/
        └── r2dbc-postgresql/          # PostgreSQL persistence (optional)
            ├── build.gradle
            └── src/main/java/co/com/bancolombia/r2dbc/
                ├── StockEntity.java   # @Table mapping
                ├── StockR2dbcRepository.java   # Spring Data R2DBC
                └── StockR2dbcRepositoryAdapter.java    # Implements StockRepository
```

## Endpoints Quick Reference

```bash
# Create stock (HU1 saga: ProductValidatedEvent → StockCreatedEvent)
POST http://localhost:8082/api/stocks
{
  "productId": "prod-001"
}

# Get stock details
GET http://localhost:8082/api/stocks/{productId}

# Reserve quantity (HU2 saga: order creation)
POST http://localhost:8082/api/stocks/{productId}/reserve
{
  "quantity": 30
}

# Add quantity (supplier delivery, order cancellation)
POST http://localhost:8082/api/stocks/{productId}/restock
{
  "quantity": 100
}
```

## Core Concepts

### Stock Aggregate (Domain)
```
State Machine:
    ACTIVE (availableQty > 0)  ←→ DEPLETED (availableQty = 0)

Operations:
    reserve(qty)              → availableQty -= qty, reservedQty += qty
    releaseReservation(qty)   → reservedQty -= qty, availableQty += qty
    consumeReservation(qty)   → reservedQty -= qty (final step of order fulfillment)
    restock(qty)              → availableQty += qty, always ACTIVE
```

### Guardrails (Invariants)
- availableQty >= 0
- reservedQty >= 0
- reserve() fails if qty > availableQty or status == DEPLETED
- releaseReservation() auto-transitions DEPLETED → ACTIVE if inventory available
- consumeReservation() only for previously-reserved quantities

### Repository Toggle
```yaml
# Default: in-memory (no database required)
arka.inventory.use-in-memory-repository: true

# Production: PostgreSQL via R2DBC
arka.inventory.use-in-memory-repository: false
spring.r2dbc.url: r2dbc:postgresql://localhost:5432/arka_inventory
```

## Running Tests

```bash
cd /sessions/exciting-sleepy-ptolemy/mnt/AceleraTI/arka/ms-inventory

# All tests
./gradlew test

# Specific module
./gradlew :model:test                    # Domain only (fastest)
./gradlew :usecase:test                  # Use cases + Mockito
./gradlew :reactive-web:test             # API endpoints

# Coverage & mutations
./gradlew jacocoMergedReport            # JaCoCo HTML report in build/reports
./gradlew pitest                        # PIT mutation testing
```

## Starting the App

```bash
# Development (in-memory, no DB needed)
./gradlew :app-service:bootRun
# → http://localhost:8082/api/stocks

# For PostgreSQL support:
# 1. Edit applications/app-service/src/main/resources/application.yaml
#    - Set: use-in-memory-repository: false
#    - Update: spring.r2dbc.url, username, password
# 2. Create PostgreSQL database: arka_inventory
# 3. Run: psql -U postgres arka_inventory < applications/app-service/src/main/resources/schema.sql
# 4. Restart app
```

## Integration Points

### HU1: Product Validation → Stock Creation
```
ms-catalog (ProductValidatedEvent)
    ↓
ms-inventory (CreateStockUseCase)
    ↓
Database (Stock with availableQty=0, status=ACTIVE)
    ↓
ms-inventory (publishes StockCreatedEvent)
    ↓
ms-catalog (transitions Product → CONFIRMADO)
```

### HU2: Order Creation → Stock Reservation
```
ms-ordering (create order)
    ↓
POST /api/stocks/{productId}/reserve (qty)
    ↓
ms-inventory (validates & reserves)
    ↓
ms-ordering (proceeds with payment)
    ├─ If success: consume reservation later
    └─ If fail: POST /api/stocks/{productId}/restock to release
```

## Class Relationships

### Domain Classes (Zero Spring)
- **Stock** - Aggregate root (state machine, business rules)
  - Uses: StockStatus, StockMovement
  - Implements: reserve(), releaseReservation(), consumeReservation(), restock()
- **StockRepository** (interface) - Output port
  - Implementations: InMemoryStockRepository, StockR2dbcRepositoryAdapter

### Use Cases
- **CreateStockUseCase** → StockRepository.save()
- **GetStockUseCase** → StockRepository.findByProductId()
- **ReserveStockUseCase** → Stock.reserve() → StockRepository.save()
- **RestockUseCase** → Stock.restock() → StockRepository.save()

### Entry Points (WebFlux)
- **RouterRest** (configuration bean) → exposes routes
- **Handler** (component) → orchestrates use cases
- **StockRequest** (DTO) → request parsing
- **StockResponse** (DTO) → response formatting

### Driven Adapters
- **InMemoryStockRepository** (component, conditional) → ConcurrentHashMap
- **StockR2dbcRepositoryAdapter** (component, conditional) → R2DBC
- **StockEntity** → JPA-like mapping
- **StockR2dbcRepository** (interface) → Spring Data R2DBC

## Testing Matrix

| Layer | Class | Test | Type | Mocks |
|-------|-------|------|------|-------|
| Domain | Stock | StockTest | Pure JUnit | None |
| Use Case | ReserveStock | ReserveStockUseCaseTest | JUnit + StepVerifier | Repository |
| API | RouterRest | RouterRestTest | @WebFluxTest + WebTestClient | Use Cases |

## Decisions & Trade-offs

| Decision | Rationale |
|----------|-----------|
| In-memory by default | Fast dev cycle, no DB setup required |
| R2DBC (not JPA) | Reactive, non-blocking, fewer layers |
| Functional Router | No @RestController boilerplate, functional FP style |
| ComponentScan regex | Auto-inject use cases, decoupled config |
| ConcurrentHashMap (dual index) | Fast lookups by stockId OR productId |
| State machine in domain | Business logic in one place, testable without Spring |
| Value object (StockMovement) | Audit trail design-ready, immutable |

## Next Steps (Future Work)

1. Kafka event publishing (ProductValidatedEvent → StockCreatedEvent)
2. Saga pattern with compensating transactions
3. Flyway/Liquibase for database versioning
4. Circuit breaker (Resilience4j) for inter-service calls
5. Custom Micrometer metrics (reserves/releases/consumes rates)
6. Distributed tracing (Spring Cloud Sleuth)
7. API documentation (Springdoc OpenAPI)
8. Performance testing (JMH benchmarks)
9. Stress testing (k6 scripts)

---

**Ready to integrate with ARKA microservices system!**
