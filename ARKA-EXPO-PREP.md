# ARKA B2B — Guía de Presentación

> Alineada con las diapositivas del profesor (`notas-curso/17-Proyecto-Arka/slides/docs/`).
> AWS: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com

---

## Estado al 2026-04-27: 13/13 servicios en AWS ECS Fargate ✅

| Servicio | Puerto | DB | Estado AWS |
|---|---|---|---|
| api-gateway | 8080 | — | ✅ |
| ms-catalog | 8081 | MongoDB | ✅ |
| ms-inventory | 8082 | PostgreSQL | ✅ |
| ms-order | 8083 | PostgreSQL | ✅ |
| ms-payment | 8084 | PostgreSQL | ✅ |
| ms-provider | 8085 | PostgreSQL | ✅ |
| ms-cart | 8086 | MongoDB | ✅ |
| ms-notifications | 8087 | — | ✅ |
| ms-reporter | 8088 | PostgreSQL | ✅ |
| arka-frontend | 80 | — | ✅ |
| Kafka | 9092 | — | ✅ |
| MongoDB | 27017 | — | ✅ |
| PostgreSQL | 5432 | — | ✅ |

---

## HUs implementadas (numeración del profesor)

### HU1 — Registrar Producto (Saga Coreografía)

**Endpoint:** `POST /api/v1/products`  
**Flujo:**
```
ms-catalog → catalog.product-validated
→ ms-provider → provider.provider-validated
→ ms-inventory (crea stock qty=0) → inventory.stock-created
→ ms-catalog (estado: CONFIRMADO)
```
**Estados de producto:** `EN_CREACION → VALIDANDO_PROVEEDOR → EN_CREACION_STOCK → CONFIRMADO`  
**Patrón clave:** Coreografía sin orquestador central

---

### HU2 — Actualizar Stock (Ajuste Manual Admin)

**Endpoint:** `POST /api/stocks/{productId}/restock`  
**Body:** `{ "quantity": 50 }`  
**Descripción:** Operador agrega inventario físico. Activa restock en dominio Stock → status = ACTIVE.  
**HU3 trigger:** Si el stock estaba alertado (`alertSent=true`) y ahora supera el umbral → resetea flag.

---

### HU3 — Alertas de Stock Bajo (Reactiva, Debounced)

**Sin endpoint** — ocurre automáticamente cuando stock cae bajo el umbral.

**Flujo:**
```
Reserve/Restock disminuye stock
→ Stock.isLowStock(): availableQty <= minimumThreshold && !alertSent
→ emit inventory.stock-low-alert (topic nuevo)
→ ms-notifications: "[URGENT] Stock crítico producto X"
```
**Anti-spam:** campo `alertSent=true` previene alertas duplicadas. Se resetea cuando stock supera threshold en próximo restock.  
**Default threshold:** 10 unidades (configurable por producto via `minimumThreshold`).

---

### HU4 — Registrar Orden de Compra (Saga)

**Endpoint de entrada:** `POST /api/carts/{cartId}/checkout`  
**Flujo:**
```
ms-cart emite cart.checkout-requested
→ ms-order: CreateOrderUseCase → emite order.order-created
→ ms-payment: ProcessPaymentUseCase → emite payment.payment-processed
→ ms-order: CompleteOrderUseCase → ORDER COMPLETED
→ ms-notifications: notificación al cliente
→ ms-reporter: registra evento
```
**Estados de orden:** `PENDING → CREATED → PAID → COMPLETED`  
**Nota:** `ms-order` no expone POST de creación — es 100% event-driven.

---

### HU5 — Historial de Órdenes

**Endpoint:** `GET /api/orders?customerId=X&page=0&size=10`  
**Otro:** `GET /api/orders/{orderId}` para consulta por ID  
**Descripción:** Paginación in-memory con skip/take sobre `repository.findByCustomerId(customerId)`.

---

### HU6 — Notificaciones Centralizadas

**ms-notifications** consume:
- `order.order-created` → ORDER_CREATED
- `order.order-completed` → ORDER_COMPLETED
- `payment.payment-processed` → PAYMENT_PROCESSED
- `payment.payment-failed` → PAYMENT_FAILED
- `inventory.stock-low-alert` → STOCK_LOW_ALERT (urgente)

**Patrón:** Consumer pasivo sin DB. LogNotificationSender imprime por consola (simulación SMS).

---

### HU7 — Reportes / CQRS

**Endpoint:** `GET /api/reports/events/{type}`  
**ms-reporter** consume TODOS los eventos de dominio (AllEventsConsumer).  
**Patrón:** Event Sourcing append-only — cada evento queda registrado en PostgreSQL para analítica.

---

### HU8 — Carrito de Compras

**Endpoints:**
- `POST /api/carts/{customerId}/items` — agregar producto
- `GET /api/carts/{customerId}` — ver carrito
- `POST /api/carts/{cartId}/checkout` — iniciar saga de compra (HU4)

**DB:** MongoDB — documento único por carrito con items embebidos.

---

## Arquitectura (puntos clave para la presentación)

### Clean Architecture Hexagonal (Bancolombia scaffold v4.0.5)

```
domain/model     ← Entidades puras, sin Spring, sin Reactor
domain/usecase   ← Casos de uso, retornan Mono/Flux, sin @Component
infrastructure/  ← Adapters: REST, R2DBC, Mongo, Kafka
app-service/     ← Wiring via @ComponentScan regex ^.+UseCase$
```

### Patrones implementados

| Patrón | Dónde |
|---|---|
| Saga Coreografía | HU1: catalog→provider→inventory |
| Saga Coreografía | HU4: cart→order→payment |
| CQRS | ms-reporter (write path vía Kafka, read path vía REST) |
| Event-Driven Architecture | Kafka 11+ topics entre 9 microservicios |
| Reactive (WebFlux + R2DBC) | Todos los microservicios |
| Clean Architecture Hexagonal | Cada microservicio |
| Domain State Machines | Product (5 estados), Order (4 estados), Stock (2 estados) |
| Debouncing (alertSent flag) | HU3 alertas de stock |

### Decisiones de DB (justificación arquitectónica)

**PostgreSQL:** ms-inventory, ms-order, ms-payment, ms-provider, ms-reporter
- Transacciones ACID para inventario y pagos
- Optimistic/Pessimistic locking para concurrencia
- JSONB en ms-reporter para event sourcing flexible

**MongoDB:** ms-catalog, ms-cart
- Documentos polimórficos (productos con atributos variables)
- Arrays atómicos para items del carrito

---

## Flujo demo para presentación

### Escenario completo (HU1 → HU4 → HU3)

```bash
# 1. Crear proveedor (HU7 admin)
POST /api/providers
{ "name": "TechSupplier", "contactEmail": "ops@tech.com" }

# 2. Registrar producto (inicia HU1 saga)
POST /api/v1/products
{ "name": "Teclado Mecánico", "category": "TECH", "price": 150, "providerId": "..." }

# 3. Agregar stock (HU2) — con threshold bajo para demostrar HU3
POST /api/stocks/{productId}/restock
{ "quantity": 12 }

# 4. Agregar al carrito (HU8)
POST /api/carts/{customerId}/items
{ "productId": "...", "quantity": 5 }

# 5. Checkout (inicia HU4 saga)
POST /api/carts/{cartId}/checkout
→ Kafka: checkout-requested → order-created → payment-processed → order-completed

# 6. Reservar más stock (HU4 trigger) — baja a 7, alert emitida (HU3)
# ms-notifications recibe: "[URGENT] Stock crítico..."

# 7. Ver historial de órdenes (HU5)
GET /api/orders?customerId={customerId}&page=0&size=10

# 8. Ver reportes (HU7)
GET /api/reports/events/order.order-completed
```

---

## AWS URLs directas para demo

- Frontend: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com
- Health: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/actuator/health
- Productos: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/api/v1/products
- Stock: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/api/stocks/{productId}
- Reportes: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/api/reports/events/order.order-created

---

## Temas del curso demostrados en ARKA

| Tema | Donde se ve en ARKA |
|---|---|
| Clean Architecture (tema 4, 10) | Estructura de cada microservicio |
| Testing TDD, Mockito, StepVerifier (tema 7) | domain/usecase tests con JaCoCo ≥90% |
| SOLID + Hexagonal (tema 8, 10) | Ports & Adapters, use cases sin Spring |
| DDD, State Machines (tema 11) | Product, Order, Stock aggregates |
| Programación Reactiva WebFlux (tema 12) | R2DBC, Mono/Flux, KafkaTemplate reactivo |
| Docker + Compose (tema 13) | docker-compose.yml, scripts/ecr-push.sh |
| Microservicios, EDA, Sagas (tema 14) | Kafka, coreografía HU1 y HU4 |
| Spring Boot avanzado (tema 16) | Spring Cloud Gateway, actuator, profiles |
| Despliegue en nube | AWS ECS Fargate, ECR, ALB, Cloud Map |
