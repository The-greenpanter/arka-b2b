# ARKA B2B — Documentación Técnica Completa

> Sistema B2B de catálogo, inventario, pedidos y pagos.  
> 9 microservicios Spring Boot reactivos · Kafka · MongoDB · PostgreSQL · Docker Compose

---

## Tabla de Contenidos

1. [Visión General del Sistema](#1-visión-general-del-sistema)
2. [Arquitectura Limpia Hexagonal](#2-arquitectura-limpia-hexagonal)
3. [Flujos de Saga (HU1 y HU4)](#3-flujos-de-saga)
4. [Máquinas de Estado](#4-máquinas-de-estado)
5. [Principios SOLID — En el código](#5-principios-solid)
6. [Propiedades ACID — En el código](#6-propiedades-acid)
7. [Referencia de Endpoints](#7-referencia-de-endpoints)
8. [Guía de Pruebas End-to-End](#8-guía-de-pruebas-end-to-end)

---

## 1. Visión General del Sistema

### Mapa de microservicios y comunicación

```mermaid
graph TB
    subgraph Cliente
        C[Browser / Postman]
    end

    subgraph Gateway
        GW[api-gateway :8080\nSpring Cloud Gateway]
    end

    subgraph "Microservicios Core"
        CAT[ms-catalog :8081\nMongoDB]
        INV[ms-inventory :8082\nPostgreSQL]
        ORD[ms-order :8083\nPostgreSQL]
        PAY[ms-payment :8084\nPostgreSQL]
        PRV[ms-provider :8085\n— sin BD —]
        CRT[ms-cart :8086\nMongoDB]
    end

    subgraph "Microservicios Soporte"
        NOT[ms-notifications :8087\n— sin BD —]
        REP[ms-reporter :8088\nPostgreSQL]
    end

    subgraph "Infraestructura"
        KF[(Kafka KRaft\n:9092)]
        MG[(MongoDB\n:27017)]
        PG[(PostgreSQL\n:5432)]
        KUI[Kafka UI\n:8090]
        TRK[Traefik\n:80 / :8070]
    end

    C -->|HTTP| GW
    GW -->|proxy| CAT
    GW -->|proxy| INV
    GW -->|proxy| ORD
    GW -->|proxy| CRT
    GW -->|proxy| PAY
    GW -->|proxy| PRV

    CAT <-->|catalog.product-validated| KF
    CAT <-->|inventory.stock-created| KF
    CAT <-->|provider.provider-validated| KF
    PRV <-->|catalog.product-validated| KF
    PRV <-->|provider.provider-validated| KF
    INV <-->|provider.provider-validated| KF
    INV <-->|inventory.stock-created| KF
    CRT <-->|cart.checkout-requested| KF
    ORD <-->|cart.checkout-requested| KF
    ORD <-->|order.order-created| KF
    ORD <-->|order.order-completed| KF
    PAY <-->|order.order-created| KF
    PAY <-->|payment.payment-processed| KF
    NOT -.->|consume all| KF
    REP -.->|consume all| KF

    CAT --- MG
    CRT --- MG
    INV --- PG
    ORD --- PG
    PAY --- PG
    REP --- PG
```

### Tabla de servicios

| Servicio | Puerto | BD | Produce | Consume |
|---|---|---|---|---|
| api-gateway | 8080 | — | — | — |
| ms-catalog | 8081 | MongoDB | `catalog.product-validated` | `provider.provider-validated`, `inventory.stock-created` |
| ms-inventory | 8082 | PostgreSQL | `inventory.stock-created` | `provider.provider-validated` |
| ms-provider | 8085 | — | `provider.provider-validated` | `catalog.product-validated` |
| ms-cart | 8086 | MongoDB | `cart.checkout-requested` | — |
| ms-order | 8083 | PostgreSQL | `order.order-created`, `order.order-completed` | `cart.checkout-requested` |
| ms-payment | 8084 | PostgreSQL | `payment.payment-processed` | `order.order-created` |
| ms-notifications | 8087 | — | — | todos los eventos |
| ms-reporter | 8088 | PostgreSQL | — | todos los eventos |

---

## 2. Arquitectura Limpia Hexagonal

Cada microservicio sigue el scaffold **Bancolombia Clean Architecture v4.0.5**.

### Estructura de capas (regla de dependencia hacia adentro)

```mermaid
graph LR
    subgraph "domain/model — Núcleo puro"
        E[Entidades\nProduct, Stock, Order...]
        P[Puertos\nProductRepository\nEventPublisher...]
    end

    subgraph "domain/usecase — Casos de uso"
        UC[RegisterProductUseCase\nCreateStockUseCase\n...]
    end

    subgraph "infrastructure — Adapters"
        subgraph "entry-points"
            REST[reactive-web\nRouter + Handler\nWebFlux]
        end
        subgraph "driven-adapters"
            MDB[mongo-repository\nMongoProductRepositoryAdapter]
            R2D[r2dbc-postgresql\nStockR2dbcRepositoryAdapter]
            KPR[kafka-producer\nKafkaEventPublisher]
            KCS[kafka-consumer\nProviderValidatedConsumer\n...]
        end
    end

    subgraph "applications/app-service"
        MAIN[MainApplication\nUseCasesConfig\nInMemoryXxx...]
    end

    REST --> UC
    UC --> E
    UC --> P
    MDB --> P
    R2D --> P
    KPR --> P
    KCS --> UC
    MAIN --> REST
    MAIN --> MDB
    MAIN --> R2D
    MAIN --> KPR
    MAIN --> KCS
```

### La regla de oro

```
❌ domain/model   →  NO puede importar Spring, JPA, Kafka, MongoDB
❌ domain/usecase →  NO puede importar Spring (excepto reactor-core)
✅ infrastructure →  SÍ puede importar todo
✅ app-service    →  Cablea todo (wiring)
```

### ¿Por qué no `@Service` en los use cases?

```java
// UseCasesConfig.java — registra use cases automáticamente
@ComponentScan(
    basePackages = "co.com.bancolombia.usecase",
    includeFilters = {
        @Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
    },
    useDefaultFilters = false
)
// → cualquier clase que termine en "UseCase" es un bean Spring
// → sin contaminar el dominio con anotaciones de framework
```

---

## 3. Flujos de Saga

### HU1 — Crear Producto (Choreography Saga)

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant GW as api-gateway<br/>:8080
    participant CAT as ms-catalog<br/>:8081
    participant KF as Kafka
    participant PRV as ms-provider<br/>:8085
    participant INV as ms-inventory<br/>:8082

    Dev->>GW: POST /api/products<br/>{name, category, price}
    GW->>CAT: proxy →
    CAT->>CAT: save(EN_CREACION)<br/>→ validateProvider()<br/>= VALIDANDO_PROVEEDOR
    CAT->>KF: catalog.product-validated<br/>key=productId
    CAT-->>Dev: 201 {productId, status: VALIDANDO_PROVEEDOR}

    KF->>PRV: consume catalog.product-validated
    PRV->>PRV: stub validation (always ok)
    PRV->>KF: provider.provider-validated<br/>key=productId

    KF->>CAT: consume provider.provider-validated
    CAT->>CAT: moveToCreatingStock()<br/>= EN_CREACION_STOCK

    KF->>INV: consume provider.provider-validated
    INV->>INV: createStock(productId, qty=0)
    INV->>KF: inventory.stock-created<br/>key=productId

    KF->>CAT: consume inventory.stock-created
    CAT->>CAT: confirm()<br/>= CONFIRMADO

    Note over Dev,INV: Estado final: Product=CONFIRMADO, Stock=ACTIVE qty=0
```

### HU4 — Crear Pedido (Saga HU4)

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant GW as api-gateway
    participant CRT as ms-cart<br/>:8086
    participant KF as Kafka
    participant ORD as ms-order<br/>:8083
    participant PAY as ms-payment<br/>:8084

    Dev->>GW: POST /api/carts/{customerId}/items
    GW->>CRT: addItem(productId, qty, price)
    CRT-->>Dev: 200 {cart}

    Dev->>GW: POST /api/carts/{cartId}/checkout
    GW->>CRT: checkout()
    CRT->>KF: cart.checkout-requested<br/>key=cartId
    CRT-->>Dev: 201 {cart, status: CHECKED_OUT}

    KF->>ORD: consume cart.checkout-requested
    ORD->>ORD: createOrder(PENDING)
    ORD->>KF: order.order-created<br/>key=orderId

    KF->>PAY: consume order.order-created
    PAY->>PAY: processPayment (stub)
    PAY->>KF: payment.payment-processed<br/>key=orderId
```

---

## 4. Máquinas de Estado

### Product (ms-catalog)

```mermaid
stateDiagram-v2
    [*] --> EN_CREACION : POST /api/products

    EN_CREACION --> VALIDANDO_PROVEEDOR : RegisterProductUseCase\n.validateProvider()
    VALIDANDO_PROVEEDOR --> EN_CREACION_STOCK : HandleProviderValidatedUseCase\n.moveToCreatingStock()
    EN_CREACION_STOCK --> CONFIRMADO : ConfirmProductUseCase\n.confirm()
    CONFIRMADO --> INACTIVO : deactivate()

    EN_CREACION --> RECHAZADO : provider-rejected event
    VALIDANDO_PROVEEDOR --> RECHAZADO : provider-rejected event
    EN_CREACION_STOCK --> RECHAZADO : stock-failed event
```

### Order (ms-order)

```mermaid
stateDiagram-v2
    [*] --> PENDING : CreateOrderUseCase\n(from cart.checkout-requested)
    PENDING --> PROCESSING : process()
    PROCESSING --> COMPLETED : CompleteOrderUseCase
    PENDING --> CANCELLED : cancel()
    PROCESSING --> CANCELLED : cancel()
```

### Stock (ms-inventory)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : CreateStockUseCase\n(qty=0)
    ACTIVE --> DEPLETED : availableQty == 0
    DEPLETED --> ACTIVE : restock()
    ACTIVE --> ACTIVE : reserve() / restock()
```

---

## 5. Principios SOLID

### S — Single Responsibility Principle

> *Una clase tiene una sola razón para cambiar.*

```mermaid
graph LR
    subgraph "❌ Violación"
        BAD["ProductService\n- registrar()\n- buscar()\n- confirmar()\n- listar()\n- enviarEmail()\n- guardarEnMongo()"]
    end

    subgraph "✅ ARKA — una clase, un motivo"
        A[RegisterProductUseCase\nSolo: registrar + emitir evento]
        B[ConfirmProductUseCase\nSolo: confirmar en saga]
        C[MongoProductRepositoryAdapter\nSolo: persistir en MongoDB]
        D[KafkaEventPublisher\nSolo: publicar en Kafka]
    end
```

**Ejemplo real** — `RegisterProductUseCase` solo hace una cosa:

```java
// Solo registra. No persiste directamente, no envía email, no valida formato.
public Mono<Product> execute(Product input) {
    return Mono.fromSupplier(() -> input.toBuilder()
                .productId(UUID.randomUUID().toString())
                .status(ProductStatus.EN_CREACION)
                .build())
            .map(Product::validateProvider)
            .flatMap(repository::save)              // delega persistencia al puerto
            .flatMap(saved -> eventPublisher         // delega mensajería al puerto
                    .publish("catalog.product-validated", saved.getProductId(), saved)
                    .thenReturn(saved));
}
```

---

### O — Open/Closed Principle

> *Abierto para extensión, cerrado para modificación.*

Los **puertos (interfaces)** están cerrados para modificación. Los **adapters** extienden sin tocar el dominio:

```mermaid
graph TD
    PORT["«interface»\nProductRepository\n+ save()\n+ findById()\n..."]

    A["MongoProductRepositoryAdapter\n@ConditionalOnProperty(use-in-memory=false)\nimpl → MongoDB"]
    B["InMemoryProductRepository\n@ConditionalOnProperty(use-in-memory=true)\nimpl → ConcurrentHashMap"]
    C["FuturePostgresAdapter\nAgrega sin modificar el puerto"]

    PORT <|-- A
    PORT <|-- B
    PORT <|-- C
```

Cambiar de MongoDB a PostgreSQL = escribir un nuevo adapter. **El dominio no cambia.**

---

### L — Liskov Substitution Principle

> *Los subtipos deben ser sustituibles por sus tipos base.*

```java
// El use case solo conoce ProductRepository (interfaz)
@RequiredArgsConstructor
public class RegisterProductUseCase {
    private final ProductRepository repository; // ← interfaz, no implementación
    // funciona igual con Mongo, InMemory, o cualquier futuro adapter
}
```

En tests: `Mockito.mock(ProductRepository.class)` es un sustituto válido → los tests pasan sin Spring ni MongoDB.

---

### I — Interface Segregation Principle

> *Los clientes no deben depender de interfaces que no usan.*

```mermaid
graph LR
    subgraph "✅ Interfaces pequeñas en ARKA"
        PR["ProductRepository\n+ save()\n+ findById()\n+ findAll()\n+ findByStatus()\n+ findByCategory()\n+ existsById()"]
        EP["EventPublisher\n+ publish(topic, key, payload)"]
        NS["NotificationSender\n+ send(notification)"]
    end

    subgraph "Clientes"
        RUC["RegisterProductUseCase\nusa: save(), publish()"]
        CUC["ConfirmProductUseCase\nusa: findById(), save()"]
        SN["SendNotificationUseCase\nusa: send()"]
    end

    RUC -->|solo necesita| PR
    RUC -->|solo necesita| EP
    CUC -->|solo necesita| PR
    SN -->|solo necesita| NS
```

`EventPublisher` tiene **un solo método** (`publish`). Nunca fuerza a los use cases a implementar métodos que no usan.

---

### D — Dependency Inversion Principle

> *Depende de abstracciones, no de implementaciones concretas.*

```mermaid
graph TB
    subgraph "❌ Sin DIP — alto acoplamiento"
        UC1["RegisterProductUseCase"] -->|import directo| MDB1["MongoProductRepository"]
        UC1 -->|import directo| KT["KafkaTemplate"]
    end

    subgraph "✅ ARKA — con DIP"
        UC2["RegisterProductUseCase\n(dominio puro)"]
        IRP["«interface»\nProductRepository"]
        IEP["«interface»\nEventPublisher"]
        MDB2["MongoProductRepositoryAdapter\n(infraestructura)"]
        KP["KafkaEventPublisher\n(infraestructura)"]

        UC2 -->|depende de| IRP
        UC2 -->|depende de| IEP
        MDB2 -->|implementa| IRP
        KP -->|implementa| IEP
    end
```

La dirección de dependencia apunta **hacia adentro**. El dominio nunca importa infraestructura.

---

## 6. Propiedades ACID

### ¿Qué es ACID?

```mermaid
graph LR
    A["⚛️  Atomicity\nTodo o nada\nUna transacción no\nqueda a medias"]
    C["✅  Consistency\nLos datos siempre\nrespetan las reglas\nde negocio"]
    I["🔒  Isolation\nTransacciones\nconcurrentes no\nse interfieren"]
    D["💾  Durability\nUna vez commited,\nlos datos sobreviven\na fallas"]

    A --- C --- I --- D
```

### ACID en ms-inventory (PostgreSQL + R2DBC)

```mermaid
sequenceDiagram
    participant UC as ReserveStockUseCase
    participant R2DBC as R2DBC / PostgreSQL
    participant DB as arka_inventory DB

    UC->>R2DBC: findByProductId(productId)
    R2DBC->>DB: SELECT ... FOR UPDATE (Pessimistic Lock)
    DB-->>R2DBC: Stock {available=100, reserved=0}

    Note over UC: stock.reserve(30)\navailable=70, reserved=30

    UC->>R2DBC: save(stock)
    R2DBC->>DB: UPDATE stocks SET available=70, reserved=30
    DB-->>R2DBC: OK — COMMIT

    Note over DB: ⚛️ Atomicity: si falla el UPDATE, rollback automático
    Note over DB: ✅ Consistency: available nunca < 0 (regla en dominio)
    Note over DB: 🔒 Isolation: SELECT FOR UPDATE bloquea escrituras concurrentes
    Note over DB: 💾 Durability: dato persiste en PostgreSQL
```

**Regla de negocio que garantiza Consistency:**

```java
// Stock.java — el dominio lanza excepción antes de persistir un estado inválido
public Stock reserve(int quantity) {
    if (quantity > this.availableQty) {
        throw new IllegalStateException("Insufficient available quantity");
    }
    this.availableQty -= quantity;
    this.reservedQty  += quantity;
    return this;
}
```

### ACID en ms-catalog (MongoDB)

MongoDB 4+ soporta transacciones multi-documento. El **Outbox Pattern** garantiza atomicidad entre la escritura del producto y la publicación del evento:

```mermaid
graph LR
    subgraph "Transacción MongoDB"
        direction TB
        W1["INSERT products\n{productId, status: VALIDANDO_PROVEEDOR}"]
        W2["INSERT outbox_events\n{topic: catalog.product-validated\npayload: productId}"]
        W1 -.->|misma sesión| W2
    end

    SCHED["@Scheduled Outbox Relay\n(publicar a Kafka)\npoll cada 500ms"]
    SCHED -->|lee y elimina| W2

    Note["Si Kafka falla:\noutbox_events persiste\nRetry automático\n= No dual-write problem"]
```

> **Implementación actual**: el `InMemoryEventPublisher` simula este comportamiento para desarrollo. El `KafkaEventPublisher` real se activa con `arka.catalog.use-in-memory-broker=false`.

---

## 7. Referencia de Endpoints

### Base URL
- **Directo**: `http://localhost:{puerto}`
- **Via Gateway**: `http://localhost:8080`

---

### ms-catalog — Productos (`/api/products`)

| Método | Path | Descripción | Body |
|---|---|---|---|
| `POST` | `/api/products` | Registrar producto (HU1 paso 1) | Ver abajo |
| `GET` | `/api/products` | Listar todos los productos | — |
| `GET` | `/api/products?status=CONFIRMADO` | Filtrar por estado | — |
| `GET` | `/api/products?category=MONITORES` | Filtrar por categoría | — |
| `GET` | `/api/products/{id}` | Detalle de un producto | — |
| `POST` | `/api/products/{id}/confirm` | Confirmar manualmente (sin saga) | — |

**Body POST `/api/products`:**
```json
{
  "name": "Monitor 4K UltraWide",
  "description": "Monitor para trabajo profesional",
  "category": "MONITORES",
  "basePriceUsd": 599.99,
  "taxRate": 0.19,
  "minOrderQty": 1,
  "maxOrderQty": 100,
  "supplier": {
    "supplierId": "sup-001",
    "name": "DisplayCorp S.A.",
    "leadTimeDays": 14
  }
}
```

**Respuesta 201:**
```json
{
  "productId": "a3f1c2d4-...",
  "name": "Monitor 4K UltraWide",
  "status": "VALIDANDO_PROVEEDOR",
  "createdAt": "2026-04-21T18:00:00Z"
}
```

---

### ms-inventory — Stock (`/api/stocks`)

| Método | Path | Descripción | Body |
|---|---|---|---|
| `POST` | `/api/stocks` | Crear stock para un producto | `{"productId": "..."}` |
| `GET` | `/api/stocks/{productId}` | Consultar stock de un producto | — |
| `POST` | `/api/stocks/{productId}/reserve` | Reservar cantidad (HU2) | `{"quantity": 30}` |
| `POST` | `/api/stocks/{productId}/restock` | Añadir unidades | `{"quantity": 50}` |

**Respuesta GET `/api/stocks/{productId}`:**
```json
{
  "stockId": "b2e9...",
  "productId": "a3f1...",
  "availableQty": 70,
  "reservedQty": 30,
  "totalQty": 100,
  "status": "ACTIVE",
  "updatedAt": "2026-04-21T18:05:00Z"
}
```

---

### ms-cart — Carrito (`/api/carts`)

| Método | Path | Descripción | Body |
|---|---|---|---|
| `POST` | `/api/carts/{customerId}/items` | Agregar ítem al carrito | Ver abajo |
| `GET` | `/api/carts/{cartId}` | Ver carrito | — |
| `POST` | `/api/carts/{cartId}/checkout` | Hacer checkout (HU4) | — |

**Body POST `/api/carts/{customerId}/items`:**
```json
{
  "productId": "a3f1c2d4-...",
  "productName": "Monitor 4K UltraWide",
  "quantity": 2,
  "unitPrice": 599.99
}
```

---

### ms-order — Pedidos (`/api/orders`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/api/orders/{orderId}` | Consultar pedido |
| `POST` | `/api/orders/{orderId}/complete` | Completar pedido manualmente |

---

### ms-payment — Pagos (`/api/payments`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/api/payments/{paymentId}` | Consultar pago |

---

### ms-reporter — Eventos (`/api/events`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/api/events/type/{eventType}` | Eventos por tipo |
| `GET` | `/api/events/aggregate/{id}` | Eventos de un agregado |

**Ejemplo:** `GET /api/events/type/catalog.product-validated`

---

### api-gateway — Health

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/actuator/health` | Salud del gateway |
| `GET` | `/actuator/gateway/routes` | Rutas configuradas |

---

## 8. Guía de Pruebas End-to-End

### Paso 0 — Levantar la infraestructura

```bash
cd arka/
docker compose up -d

# Esperar que todos los servicios estén healthy (~30-60s)
docker compose ps

# Verificar Kafka UI
open http://localhost:8090
```

### Paso 1 — Probar HU1 (Saga crear producto)

```bash
# 1. Registrar producto → debe quedar en VALIDANDO_PROVEEDOR
curl -s -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Teclado Mecánico",
    "category": "PERIFÉRICOS",
    "basePriceUsd": 129.99,
    "taxRate": 0.19,
    "minOrderQty": 1,
    "maxOrderQty": 50,
    "supplier": {
      "supplierId": "sup-001",
      "name": "TechSupplier",
      "leadTimeDays": 7
    }
  }' | python3 -m json.tool

# Guardar el productId del response
PRODUCT_ID="<id del response>"

# 2. Esperar ~2s que fluyan los eventos Kafka
sleep 3

# 3. Consultar estado final — debe ser CONFIRMADO
curl -s http://localhost:8081/api/products/$PRODUCT_ID | python3 -m json.tool
```

**Resultado esperado:**
```json
{
  "productId": "...",
  "name": "Teclado Mecánico",
  "status": "CONFIRMADO"
}
```

### Paso 2 — Verificar Stock creado en ms-inventory

```bash
# El stock se crea automáticamente via saga
curl -s http://localhost:8082/api/stocks/$PRODUCT_ID | python3 -m json.tool
```

**Resultado esperado:**
```json
{
  "productId": "...",
  "availableQty": 0,
  "reservedQty": 0,
  "status": "ACTIVE"
}
```

### Paso 3 — Hacer restock (HU2)

```bash
curl -s -X POST http://localhost:8082/api/stocks/$PRODUCT_ID/restock \
  -H "Content-Type: application/json" \
  -d '{"quantity": 100}' | python3 -m json.tool
```

### Paso 4 — Reservar stock (HU2)

```bash
curl -s -X POST http://localhost:8082/api/stocks/$PRODUCT_ID/reserve \
  -H "Content-Type: application/json" \
  -d '{"quantity": 30}' | python3 -m json.tool

# Espera: available=70, reserved=30
```

### Paso 5 — Probar HU4 (Saga carrito → pedido)

```bash
# 1. Agregar ítem al carrito
curl -s -X POST http://localhost:8086/api/carts/customer-001/items \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": \"$PRODUCT_ID\",
    \"productName\": \"Teclado Mecánico\",
    \"quantity\": 2,
    \"unitPrice\": 129.99
  }" | python3 -m json.tool

CART_ID="<cartId del response>"

# 2. Checkout
curl -s -X POST http://localhost:8086/api/carts/$CART_ID/checkout | python3 -m json.tool

# 3. Esperar saga
sleep 3

# 4. Verificar en reporter (todos los eventos)
curl -s http://localhost:8088/api/events/type/cart.checkout-requested
curl -s http://localhost:8088/api/events/type/order.order-created
curl -s http://localhost:8088/api/events/type/payment.payment-processed
```

### Paso 6 — Verificar eventos en Kafka UI

```
http://localhost:8090
→ Topics → catalog.product-validated → Messages
→ Topics → provider.provider-validated → Messages
→ Topics → inventory.stock-created → Messages
```

---

### Códigos de respuesta HTTP

| Código | Significado | Cuándo ocurre |
|---|---|---|
| `201` | Created | Recurso creado exitosamente |
| `200` | OK | Consulta o actualización exitosa |
| `404` | Not Found | Producto/Stock/Pedido no existe (`IllegalArgumentException`) |
| `409` | Conflict | Transición de estado inválida (`IllegalStateException`) |
| `400` | Bad Request | Datos inválidos (cantidad negativa, etc.) |
| `500` | Internal Error | Error inesperado |

---

### Variables de entorno para producción

```bash
# ms-catalog
MONGODB_URI=mongodb://user:pass@host:27017/arka_catalog
KAFKA_BOOTSTRAP_SERVERS=kafka-broker:9092
ARKA_CATALOG_USE_IN_MEMORY_REPOSITORY=false
ARKA_CATALOG_USE_IN_MEMORY_BROKER=false

# ms-inventory
R2DBC_URL=r2dbc:postgresql://host:5432/arka_inventory
DB_USER=arka
DB_PASSWORD=<secret>
KAFKA_BOOTSTRAP_SERVERS=kafka-broker:9092
ARKA_INVENTORY_USE_IN_MEMORY_REPOSITORY=false
ARKA_INVENTORY_USE_IN_MEMORY_BROKER=false

# api-gateway
CATALOG_URL=http://ms-catalog:8081
INVENTORY_URL=http://ms-inventory:8082
ORDER_URL=http://ms-order:8083
CART_URL=http://ms-cart:8086
PAYMENT_URL=http://ms-payment:8084
```

---

*Generado: 2026-04-21 | Versión del sistema: ARKA B2B v1.0*


  MongoDB

  ┌───────────────┬───────────────────────────────────────────────────────────────────┐
  │ Microservicio │                            Colecciones                            │
  ├───────────────┼───────────────────────────────────────────────────────────────────┤
  │ ms-catalog    │ products (estado del producto, saga HU1), outbox (Outbox Pattern) │
  ├───────────────┼───────────────────────────────────────────────────────────────────┤
  │ ms-cart       │ Documentos de carrito con items embebidos (HU8)                   │
  └───────────────┴───────────────────────────────────────────────────────────────────┘

  PostgreSQL

  ┌───────────────┬─────────────────────────────────────────────────────┐
  │ Microservicio │                    Tablas clave                     │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ ms-inventory  │ stock_records (qty, threshold, alertSent — HU2/HU3) │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ ms-order      │ Órdenes con state machine (HU4/HU5)                 │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ ms-payment    │ Pagos procesados (HU4)                              │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ ms-provider   │ Proveedores B2B (HU1 validación)                    │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ ms-reporter   │ domain_events JSONB — Event Store append-only (HU7) │
  └───────────────┴─────────────────────────────────────────────────────┘

  Sin DB

  ┌──────────────────┬──────────────────────────────────────┐
  │  Microservicio   │               Por qué                │
  ├──────────────────┼──────────────────────────────────────┤
  │ api-gateway      │ Solo enruta                          │
  ├──────────────────┼──────────────────────────────────────┤
  │ ms-notifications │ Consumer pasivo, imprime por consola │
  └──────────────────┴──────────────────────────────────────┘