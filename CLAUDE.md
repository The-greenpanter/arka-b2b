# CLAUDE.md — ARKA B2B Microservices

> Este archivo se carga cuando ejecutas `claude` desde `arka/`.
> Hereda el contexto de `../CLAUDE.md` (perfil de Juan, notas del curso, etc.).

## What is ARKA

Sistema B2B de catálogo, inventario, pedidos y pagos. 9 microservicios
Spring Boot reactivos comunicados por Kafka. Proyecto evaluativo del curso
AceleraTI Cohorte 5 (enyoi). **Objetivo final: despliegue en la nube.**

## Architecture (Hexagonal / Clean Architecture)

Cada microservicio sigue el scaffold Bancolombia Clean Architecture v4.0.5.
Esto NO es opcional — el curso lo exige y el plugin genera la estructura.

### Layer map (de adentro hacia afuera)

```
┌─────────────────────────────────────────────┐
│  domain/model        ← Entidades, VOs,      │
│                        enums, ports (gateways)│
│                        SIN Spring, SIN libs   │
│                        Solo Java puro + Lombok│
├─────────────────────────────────────────────┤
│  domain/usecase      ← Casos de uso.         │
│                        Orquestan domain model │
│                        Retornan Mono/Flux     │
│                        SIN @Component (se     │
│                        registran por regex    │
│                        scan en app-service)   │
├─────────────────────────────────────────────┤
│  infrastructure/     ← Adapters:             │
│    entry-points/       - reactive-web (REST)  │
│    driven-adapters/    - mongo-repository     │
│                        - r2dbc-postgresql     │
│                        - kafka-producer       │
│                        - kafka-consumer       │
├─────────────────────────────────────────────┤
│  applications/       ← Spring Boot app.      │
│    app-service/        Wiring, config, main.  │
│                        ComponentScan con      │
│                        regex "^.+UseCase$"    │
└─────────────────────────────────────────────┘
```

### Critical rules (enforced by scaffold)

1. **domain/model** NEVER imports Spring, Reactor, or infrastructure classes.
   It's pure Java + Lombok. If you need reactive types, they go in the port
   interface (gateway), not in the entity.
2. **domain/usecase** depends ONLY on domain/model. Uses ports (gateways)
   injected via constructor. Returns `Mono<T>` / `Flux<T>`.
3. **Adapters** implement domain ports. They CAN import Spring, DB drivers,
   Kafka clients, etc. They map between domain entities and infra DTOs.
4. **app-service** wires everything. `@ComponentScan` with regex filter
   `^.+UseCase$` auto-registers use cases without `@Component` on them.
5. **No `@Component` / `@Service` on use cases.** The scan does it.
6. **Jacoco coverage ≥ 90%** on domain/usecase or build fails.

### Dependency direction (inward only)

```
entry-points → usecase → model ← driven-adapters
                  ↑                    ↑
              app-service (wires both sides)
```

Adapters depend on model (to implement ports). Use cases depend on model
(to use ports). Entry-points depend on use cases. Nothing depends outward.

## Microservices map

| MS | Port | DB | Status | Role in sagas |
|---|---|---|---|---|
| ms-catalog | 8081 | MongoDB | ✅ Core done | HU1 originator |
| ms-inventory | 8082 | PostgreSQL R2DBC | ✅ Core done | HU1 step 3, HU4 |
| ms-provider | 8085 | PostgreSQL R2DBC | ❌ Pending | HU1 step 1 |
| ms-order | 8083 | PostgreSQL R2DBC | ❌ Pending | HU4 orchestrator |
| ms-cart | 8086 | MongoDB | ❌ Pending | HU4 entry |
| ms-payment | 8084 | PostgreSQL R2DBC | ❌ Pending | HU4 exit |
| ms-notifications | 8087 | — | ❌ Pending | Event consumer |
| ms-reporter | 8088 | PostgreSQL (read) | ❌ Pending | CQRS read model |
| api-gateway | 8080 | — | ❌ Pending | Spring Cloud Gateway |

## Kafka topics (convention)

Format: `<origin-ms>.<event-past-participle>`

```
catalog.product-validated
provider.provider-validated
provider.provider-rejected
inventory.stock-created
inventory.stock-reserved
inventory.stock-released
order.order-created
order.order-completed
payment.payment-processed
payment.payment-failed
```

## Saga patterns

### HU1: Crear producto end-to-end (choreography)
```
ms-catalog          ms-provider         ms-inventory        ms-catalog
    │                    │                   │                   │
    ├─ save(EN_CREACION) │                   │                   │
    ├─ outbox: product   │                   │                   │
    │  -validated ──────►├─ validate ──────► │                   │
    │                    │  provider         │                   │
    │                    ├─ provider         │                   │
    │                    │  -validated ─────►├─ create stock     │
    │                    │                   │  (qty=0)          │
    │                    │                   ├─ stock            │
    │                    │                   │  -created ───────►├─ confirm()
    │                    │                   │                   │  CONFIRMADO
```

Compensation: if any step fails, emit `*.rejected` → ms-catalog sets RECHAZADO.

### HU4: Crear pedido (orchestration via ms-order)
```
ms-cart → ms-order → ms-catalog (verify) → ms-inventory (reserve)
       → ms-payment (charge) → ms-order (complete) → ms-inventory (consume)
```

## State machines

### Product (ms-catalog)
```
EN_CREACION → VALIDANDO_PROVEEDOR → EN_CREACION_STOCK → CONFIRMADO → INACTIVO
     │                │                     │
     └────────────────┴─────────────────────┴──→ RECHAZADO
```

### Stock (ms-inventory)
```
ACTIVE ←→ DEPLETED  (auto-transition based on availableQty)
```

### Order (ms-order, planned)
```
PENDING → VALIDATING → RESERVED → PAID → COMPLETED
   │          │            │         │
   └──────────┴────────────┴─────────┴──→ CANCELLED
```

## Outbox Pattern (pending implementation)

Each ms that produces events stores them in an `outbox` collection/table
atomically with the business write (same transaction). A scheduler polls
the outbox and publishes to Kafka. This avoids dual-write problems.

```java
// In the driven-adapter (e.g., MongoProductRepositoryAdapter):
public Mono<Product> save(Product product) {
    return template.inTransaction(session ->
        session.save(ProductDocument.fromDomain(product))
            .then(session.save(OutboxEvent.of(
                "catalog.product-validated",
                product.getProductId(),
                payload)))
    ).map(ProductDocument::toDomain);
}
```

## Cloud deployment target

**Goal:** deploy ARKA to cloud. Strategy TBD but aligned with course topics:

### Options evaluated (decide with Juan)

| Option | Pros | Cons | Course alignment |
|---|---|---|---|
| **AWS (real)** | Production-grade | Costs $ | High (ECS/EKS, MSK, RDS, DocumentDB) |
| **LocalStack** | Free, local AWS emulation | Not real cloud | High (course covers it in tema 13) |
| **Railway / Render** | Easy, free tier | Limited Kafka support | Low |
| **Docker Compose on VPS** | Simple, cheap ($5/mo) | Manual ops | Medium (Docker tema 13) |

### Infra already ready for deployment

- `docker-compose.yml` — Kafka KRaft + MongoDB + PostgreSQL + Traefik
- `scripts/init-multiple-postgres-dbs.sh` — multi-DB init
- Each ms has `Dockerfile`-ready bootJar (gradle `bootJar` task)
- Traefik labels ready for dynamic routing

### Cloud checklist (pending)

- [ ] Dockerfile per microservice (multi-stage: build + runtime)
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Environment-specific application.yaml (dev/staging/prod profiles)
- [ ] Secrets management (Spring Cloud Config or AWS Secrets Manager)
- [ ] Health checks + readiness probes (`/actuator/health`)
- [ ] Observability: distributed tracing (Micrometer + Zipkin/Jaeger)
- [ ] API Gateway external TLS termination

## Build & test commands

```bash
# Test single ms
cd ms-catalog && ./gradlew test

# Coverage report
./gradlew jacocoTestReport
# → build/reports/jacoco/test/html/index.html

# Mutation testing
./gradlew pitest

# Run standalone (in-memory, no infra needed)
./gradlew :app-service:bootRun

# Run with real DB (needs docker compose up first)
# Edit application.yaml: arka.<ms>.use-in-memory-repository: false
./gradlew :app-service:bootRun

# Infra
docker compose up -d          # start all
docker compose down           # stop (keep data)
docker compose down -v        # stop + delete volumes
```

## Coding conventions

- Package base: `co.com.bancolombia` (scaffold default, do NOT rename)
- Functional routing (`RouterFunctions`), NOT `@RestController`
- DTOs: `*Request` (with `toDomain()`) and `*Response` (with `fromDomain()`)
- Error handling in Handler: `IllegalArgumentException` → 404,
  `IllegalStateException` → 409, generic → 500
- Tests: domain = plain JUnit, usecase = Mockito + StepVerifier,
  web = `@WebFluxTest` + `WebTestClient`
- Commits in English, conversation in Spanish
