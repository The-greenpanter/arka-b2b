# ARKA B2B — Plan de Implementación desde Cero

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir el sistema B2B ARKA completo: 9 microservicios Spring Boot reactivos (WebFlux + Kafka + Hexagonal) desplegables en Docker Compose.

**Architecture:** Event-Driven Architecture con Sagas Coreografiadas. Cada microservicio sigue Arquitectura Hexagonal usando el scaffold Bancolombia Clean Architecture v4.0.5. Comunicación asíncrona exclusivamente via Kafka (no HTTP entre servicios core). Outbox Pattern para garantizar consistencia entre escritura en BD y publicación de eventos.

**Tech Stack:** Java 17+, Spring Boot 3.x, Spring WebFlux (Reactor), Gradle 9.x, MongoDB (R2DBC Mongo), PostgreSQL (R2DBC), Apache Kafka (KRaft), Traefik, Docker Compose, JUnit 5, Mockito, StepVerifier, WebTestClient, JaCoCo (≥90%), PIT Mutation Testing.

---

## Orden de construcción (por dependencias de saga)

```
Fase 0: Infra (Docker Compose, Kafka topics, DB init)
Fase 1: ms-catalog        ← saga HU1 originator
Fase 2: ms-inventory      ← saga HU1 step final + HU2/HU3
Fase 3: ms-provider       ← saga HU1 validador
Fase 4: ms-cart           ← saga HU4 entry point
Fase 5: ms-order          ← saga HU4 coordinator
Fase 6: ms-payment        ← saga HU4 exit
Fase 7: ms-notifications  ← consumidor pasivo
Fase 8: ms-reporter       ← CQRS read model
Fase 9: api-gateway       ← seguridad + routing
Fase 10: Cross-cutting    ← Dockerfiles, CI/CD, observabilidad
```

---

## FASE 0 — Infraestructura Base

### Task 0.1: Docker Compose con todos los servicios

**Files:**
- Create: `arka/docker-compose.yml`
- Create: `arka/scripts/init-multiple-postgres-dbs.sh`
- Create: `arka/scripts/kafka-topics.sh`

- [ ] **Step 1: Escribir docker-compose.yml**

```yaml
version: '3.9'
services:
  # --- Kafka KRaft (sin ZooKeeper) ---
  kafka:
    image: apache/kafka:3.7.0
    container_name: arka-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
    networks: [arka-net]

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "9000:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: arka
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
    depends_on: [kafka]
    networks: [arka-net]

  # --- MongoDB ---
  mongo:
    image: mongo:7.0
    container_name: arka-mongo
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: arka
      MONGO_INITDB_ROOT_PASSWORD: arka123
    volumes:
      - mongo-data:/data/db
    networks: [arka-net]

  # --- PostgreSQL (una instancia, múltiples DBs) ---
  postgres:
    image: postgres:16
    container_name: arka-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: arka
      POSTGRES_PASSWORD: arka123
      POSTGRES_MULTIPLE_DATABASES: inventory,orders,payment,provider,reporter
    volumes:
      - ./scripts/init-multiple-postgres-dbs.sh:/docker-entrypoint-initdb.d/init.sh
      - postgres-data:/var/lib/postgresql/data
    networks: [arka-net]

  # --- Traefik (API Gateway / Reverse Proxy) ---
  traefik:
    image: traefik:v3.0
    ports:
      - "8080:8080"   # API Gateway entry
      - "8090:8090"   # Dashboard
    command:
      - --api.insecure=true
      - --api.dashboard=true
      - --providers.docker=true
      - --entrypoints.web.address=:8080
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    networks: [arka-net]

volumes:
  mongo-data:
  postgres-data:

networks:
  arka-net:
    driver: bridge
```

- [ ] **Step 2: Script init-multiple-postgres-dbs.sh**

```bash
#!/bin/bash
set -e

for db in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' '); do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $db;
    GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
done
```

- [ ] **Step 3: Script para crear topics Kafka**

```bash
#!/bin/bash
# Ejecutar después de: docker compose up -d kafka
KAFKA_CONTAINER="arka-kafka"

topics=(
  "catalog.product-validated"
  "provider.provider-validated"
  "provider.provider-rejected"
  "inventory.stock-created"
  "inventory.stock-reserved"
  "inventory.stock-insufficient"
  "inventory.stock-released"
  "inventory.stock-low-detected"
  "order.order-created"
  "order.order-confirmed"
  "order.order-cancelled"
  "payment.payment-approved"
  "payment.payment-rejected"
  "cart.checkout-requested"
)

for topic in "${topics[@]}"; do
  docker exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
    --create --if-not-exists \
    --bootstrap-server localhost:9092 \
    --topic "$topic" \
    --partitions 1 \
    --replication-factor 1
  echo "Created: $topic"
done
```

- [ ] **Step 4: Levantar infra y verificar**

```bash
cd arka
docker compose up -d kafka mongo postgres traefik
docker compose ps   # todos en estado Up
docker exec arka-kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

- [ ] **Step 5: Commit**

```bash
git add arka/docker-compose.yml arka/scripts/
git commit -m "infra: docker compose with kafka kraft, mongodb, postgres, traefik"
```

---

## FASE 1 — ms-catalog

> Microservicio dueño del catálogo de productos. MongoDB. Originator de la Saga HU1.

### Task 1.1: Scaffolding con plugin Bancolombia

**Files:**
- Create: `arka/ms-catalog/` (generado por plugin)

- [ ] **Step 1: Generar el scaffold**

```bash
cd arka
gradle generate \
  --type=reactive-usecase-microservice \
  --name ms-catalog \
  --package co.com.bancolombia
```

Estructura generada:
```
ms-catalog/
├── domain/
│   ├── model/src/main/java/co/com/bancolombia/model/
│   └── usecase/src/main/java/co/com/bancolombia/usecase/
├── infrastructure/
│   ├── driven-adapters/
│   └── entry-points/reactive-web/
└── applications/app-service/
```

- [ ] **Step 2: Verificar que compila**

```bash
cd ms-catalog && ./gradlew build -x test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add ms-catalog/
git commit -m "feat(catalog): scaffold bancolombia clean architecture"
```

---

### Task 1.2: Domain Model — Product

**Files:**
- Create: `ms-catalog/domain/model/src/main/java/co/com/bancolombia/model/product/Product.java`
- Create: `ms-catalog/domain/model/src/main/java/co/com/bancolombia/model/product/ProductStatus.java`
- Create: `ms-catalog/domain/model/src/main/java/co/com/bancolombia/model/product/gateways/ProductRepository.java`
- Create: `ms-catalog/domain/model/src/main/java/co/com/bancolombia/model/product/gateways/EventPublisher.java`
- Create: `ms-catalog/domain/model/src/test/java/co/com/bancolombia/model/product/ProductTest.java`

- [ ] **Step 1: Escribir test del modelo**

```java
// ProductTest.java
class ProductTest {
    @Test
    void shouldCreateProductInCreacionStatus() {
        Product p = Product.create("SKU-001", "Monitor 4K", "Electrónica", 
                                    new BigDecimal("1200.00"), "prov-1");
        assertThat(p.getStatus()).isEqualTo(ProductStatus.EN_CREACION);
        assertThat(p.getProductId()).isNotNull();
    }

    @Test
    void shouldTransitionToValidatingProvider() {
        Product p = Product.create("SKU-001", "Monitor 4K", "Electrónica",
                                    new BigDecimal("1200.00"), "prov-1");
        Product updated = p.startProviderValidation();
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.VALIDANDO_PROVEEDOR);
    }

    @Test
    void shouldTransitionToCreatingStock() {
        Product p = Product.create("SKU-001", "Monitor 4K", "Electrónica",
                                    new BigDecimal("1200.00"), "prov-1")
                           .startProviderValidation()
                           .providerValidated();
        assertThat(p.getStatus()).isEqualTo(ProductStatus.EN_CREACION_STOCK);
    }

    @Test
    void shouldTransitionToConfirmed() {
        Product p = Product.create("SKU-001", "Monitor 4K", "Electrónica",
                                    new BigDecimal("1200.00"), "prov-1")
                           .startProviderValidation()
                           .providerValidated()
                           .stockCreated();
        assertThat(p.getStatus()).isEqualTo(ProductStatus.CONFIRMADO);
    }

    @Test
    void shouldTransitionToRejected() {
        Product p = Product.create("SKU-001", "Monitor 4K", "Electrónica",
                                    new BigDecimal("1200.00"), "prov-1")
                           .startProviderValidation()
                           .reject("Proveedor inválido");
        assertThat(p.getStatus()).isEqualTo(ProductStatus.RECHAZADO);
    }

    @Test
    void shouldNotAllowDirectTransitionFromCreacionToStock() {
        Product p = Product.create("SKU-001", "Monitor 4K", "Electrónica",
                                    new BigDecimal("1200.00"), "prov-1");
        assertThatThrownBy(p::stockCreated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition");
    }
}
```

- [ ] **Step 2: Correr test — debe FALLAR**

```bash
./gradlew :domain:model:test --tests "*.ProductTest"
```

Expected: FAIL - Product class does not exist

- [ ] **Step 3: Implementar ProductStatus**

```java
public enum ProductStatus {
    EN_CREACION,
    VALIDANDO_PROVEEDOR,
    EN_CREACION_STOCK,
    CONFIRMADO,
    RECHAZADO,
    INACTIVO
}
```

- [ ] **Step 4: Implementar Product**

```java
@Value
@Builder(toBuilder = true)
public class Product {
    String productId;
    String sku;
    String name;
    String category;
    BigDecimal price;
    String providerId;
    ProductStatus status;
    String rejectionReason;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static Product create(String sku, String name, String category,
                                  BigDecimal price, String providerId) {
        return Product.builder()
            .productId(UUID.randomUUID().toString())
            .sku(sku).name(name).category(category)
            .price(price).providerId(providerId)
            .status(ProductStatus.EN_CREACION)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public Product startProviderValidation() {
        requireStatus(ProductStatus.EN_CREACION, "startProviderValidation");
        return toBuilder().status(ProductStatus.VALIDANDO_PROVEEDOR)
                          .updatedAt(LocalDateTime.now()).build();
    }

    public Product providerValidated() {
        requireStatus(ProductStatus.VALIDANDO_PROVEEDOR, "providerValidated");
        return toBuilder().status(ProductStatus.EN_CREACION_STOCK)
                          .updatedAt(LocalDateTime.now()).build();
    }

    public Product stockCreated() {
        requireStatus(ProductStatus.EN_CREACION_STOCK, "stockCreated");
        return toBuilder().status(ProductStatus.CONFIRMADO)
                          .updatedAt(LocalDateTime.now()).build();
    }

    public Product reject(String reason) {
        if (status == ProductStatus.CONFIRMADO || status == ProductStatus.INACTIVO) {
            throw new IllegalStateException("Cannot transition from " + status + " to RECHAZADO");
        }
        return toBuilder().status(ProductStatus.RECHAZADO)
                          .rejectionReason(reason)
                          .updatedAt(LocalDateTime.now()).build();
    }

    public Product deactivate() {
        requireStatus(ProductStatus.CONFIRMADO, "deactivate");
        return toBuilder().status(ProductStatus.INACTIVO)
                          .updatedAt(LocalDateTime.now()).build();
    }

    private void requireStatus(ProductStatus required, String operation) {
        if (this.status != required) {
            throw new IllegalStateException(
                "Cannot transition via " + operation + " from status " + this.status);
        }
    }
}
```

- [ ] **Step 5: Implementar puertos (interfaces)**

```java
// ProductRepository.java
public interface ProductRepository {
    Mono<Product> save(Product product);
    Mono<Product> findById(String productId);
    Mono<Product> findBySku(String sku);
    Flux<Product> findAll();
    Flux<Product> findByStatus(ProductStatus status);
}

// EventPublisher.java
public interface EventPublisher {
    Mono<Void> publish(String topic, String aggregateId, Object payload);
}
```

- [ ] **Step 6: Correr tests — deben PASAR**

```bash
./gradlew :domain:model:test
```

Expected: BUILD SUCCESSFUL, 6 tests passing

- [ ] **Step 7: Commit**

```bash
git add ms-catalog/domain/model/
git commit -m "feat(catalog): domain model Product with state machine"
```

---

### Task 1.3: Use Cases — ms-catalog

**Files:**
- Create: `ms-catalog/domain/usecase/src/main/java/co/com/bancolombia/usecase/product/CreateProductUseCase.java`
- Create: `ms-catalog/domain/usecase/src/main/java/co/com/bancolombia/usecase/product/GetProductUseCase.java`
- Create: `ms-catalog/domain/usecase/src/main/java/co/com/bancolombia/usecase/product/ConfirmProductUseCase.java`
- Create: `ms-catalog/domain/usecase/src/main/java/co/com/bancolombia/usecase/product/RejectProductUseCase.java`
- Create: `ms-catalog/domain/usecase/src/test/java/co/com/bancolombia/usecase/product/CreateProductUseCaseTest.java`
- Create: `ms-catalog/domain/usecase/src/test/java/co/com/bancolombia/usecase/product/ConfirmProductUseCaseTest.java`

- [ ] **Step 1: Tests de CreateProductUseCase**

```java
@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseTest {
    @Mock ProductRepository productRepository;
    @Mock EventPublisher eventPublisher;
    CreateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateProductUseCase(productRepository, eventPublisher);
    }

    @Test
    void shouldCreateProductAndPublishEvent() {
        Product saved = Product.create("SKU-001", "Monitor 4K", "Electrónica",
                                       new BigDecimal("1200.00"), "prov-1")
                               .startProviderValidation();
        when(productRepository.save(any())).thenReturn(Mono.just(saved));
        when(eventPublisher.publish(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("SKU-001", "Monitor 4K", "Electrónica",
                                             new BigDecimal("1200.00"), "prov-1"))
            .assertNext(p -> {
                assertThat(p.getStatus()).isEqualTo(ProductStatus.VALIDANDO_PROVEEDOR);
                assertThat(p.getSku()).isEqualTo("SKU-001");
            })
            .verifyComplete();

        verify(eventPublisher).publish(eq("catalog.product-validated"), any(), any());
    }

    @Test
    void shouldFailIfRepositorySaveFails() {
        when(productRepository.save(any())).thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(useCase.execute("SKU-001", "Monitor 4K", "Electrónica",
                                             new BigDecimal("1200.00"), "prov-1"))
            .expectError(RuntimeException.class)
            .verify();

        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
```

- [ ] **Step 2: Correr tests — FALLAR**

```bash
./gradlew :domain:usecase:test --tests "*.CreateProductUseCaseTest"
```

Expected: FAIL - CreateProductUseCase not found

- [ ] **Step 3: Implementar CreateProductUseCase**

```java
@RequiredArgsConstructor
public class CreateProductUseCase {
    private final ProductRepository productRepository;
    private final EventPublisher eventPublisher;

    public Mono<Product> execute(String sku, String name, String category,
                                  BigDecimal price, String providerId) {
        Product product = Product.create(sku, name, category, price, providerId)
                                 .startProviderValidation();
        return productRepository.save(product)
            .flatMap(saved -> eventPublisher
                .publish("catalog.product-validated", saved.getProductId(), buildPayload(saved))
                .thenReturn(saved));
    }

    private Map<String, Object> buildPayload(Product p) {
        return Map.of(
            "productId", p.getProductId(),
            "providerId", p.getProviderId(),
            "sku", p.getSku()
        );
    }
}
```

- [ ] **Step 4: Implementar ConfirmProductUseCase (para cuando llega stock-created)**

```java
@RequiredArgsConstructor
public class ConfirmProductUseCase {
    private final ProductRepository productRepository;

    public Mono<Product> execute(String productId) {
        return productRepository.findById(productId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + productId)))
            .map(Product::stockCreated)
            .flatMap(productRepository::save);
    }
}
```

- [ ] **Step 5: Implementar RejectProductUseCase**

```java
@RequiredArgsConstructor
public class RejectProductUseCase {
    private final ProductRepository productRepository;

    public Mono<Product> execute(String productId, String reason) {
        return productRepository.findById(productId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + productId)))
            .map(p -> p.reject(reason))
            .flatMap(productRepository::save);
    }
}
```

- [ ] **Step 6: Correr todos los tests del usecase**

```bash
./gradlew :domain:usecase:test
```

Expected: BUILD SUCCESSFUL, cobertura ≥ 90%

- [ ] **Step 7: Commit**

```bash
git add ms-catalog/domain/usecase/
git commit -m "feat(catalog): use cases create/confirm/reject product"
```

---

### Task 1.4: MongoDB Adapter

**Files:**
- Create: `ms-catalog/infrastructure/driven-adapters/mongo-repository/src/main/java/.../MongoProductAdapter.java`
- Create: `ms-catalog/infrastructure/driven-adapters/mongo-repository/src/main/java/.../ProductDocument.java`
- Create: `ms-catalog/infrastructure/driven-adapters/mongo-repository/src/main/java/.../MongoProductRepository.java` (interface Spring Data)
- Create: `ms-catalog/infrastructure/driven-adapters/mongo-repository/src/test/.../MongoProductAdapterTest.java`

- [ ] **Step 1: Test del adaptador**

```java
@ExtendWith(MockitoExtension.class)
class MongoProductAdapterTest {
    @Mock MongoProductRepository mongoRepo;
    MongoProductAdapter adapter;

    @BeforeEach
    void setUp() { adapter = new MongoProductAdapter(mongoRepo); }

    @Test
    void shouldSaveProductAndMapToDomain() {
        Product product = Product.create("SKU-001", "Monitor", "Tech",
                                          new BigDecimal("500"), "prov-1");
        ProductDocument doc = ProductDocument.fromDomain(product);
        when(mongoRepo.save(any(ProductDocument.class))).thenReturn(Mono.just(doc));

        StepVerifier.create(adapter.save(product))
            .assertNext(p -> {
                assertThat(p.getSku()).isEqualTo("SKU-001");
                assertThat(p.getStatus()).isEqualTo(ProductStatus.EN_CREACION);
            })
            .verifyComplete();
    }
}
```

- [ ] **Step 2: Implementar ProductDocument**

```java
@Data
@Document(collection = "products")
public class ProductDocument {
    @Id
    private String id;
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private String providerId;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductDocument fromDomain(Product p) {
        ProductDocument doc = new ProductDocument();
        doc.setId(p.getProductId());
        doc.setSku(p.getSku());
        doc.setName(p.getName());
        doc.setCategory(p.getCategory());
        doc.setPrice(p.getPrice());
        doc.setProviderId(p.getProviderId());
        doc.setStatus(p.getStatus().name());
        doc.setRejectionReason(p.getRejectionReason());
        doc.setCreatedAt(p.getCreatedAt());
        doc.setUpdatedAt(p.getUpdatedAt());
        return doc;
    }

    public Product toDomain() {
        return Product.builder()
            .productId(id).sku(sku).name(name).category(category)
            .price(price).providerId(providerId)
            .status(ProductStatus.valueOf(status))
            .rejectionReason(rejectionReason)
            .createdAt(createdAt).updatedAt(updatedAt)
            .build();
    }
}
```

- [ ] **Step 3: Implementar MongoProductAdapter**

```java
@Repository
@RequiredArgsConstructor
public class MongoProductAdapter implements ProductRepository {
    private final MongoProductRepository mongoRepository;

    @Override
    public Mono<Product> save(Product product) {
        return mongoRepository.save(ProductDocument.fromDomain(product))
            .map(ProductDocument::toDomain);
    }

    @Override
    public Mono<Product> findById(String id) {
        return mongoRepository.findById(id).map(ProductDocument::toDomain);
    }

    @Override
    public Mono<Product> findBySku(String sku) {
        return mongoRepository.findBySku(sku).map(ProductDocument::toDomain);
    }

    @Override
    public Flux<Product> findAll() {
        return mongoRepository.findAll().map(ProductDocument::toDomain);
    }

    @Override
    public Flux<Product> findByStatus(ProductStatus status) {
        return mongoRepository.findByStatus(status.name()).map(ProductDocument::toDomain);
    }
}

// Interface Spring Data Reactive
public interface MongoProductRepository extends ReactiveMongoRepository<ProductDocument, String> {
    Mono<ProductDocument> findBySku(String sku);
    Flux<ProductDocument> findByStatus(String status);
}
```

- [ ] **Step 4: application.yaml**

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://arka:arka123@localhost:27017/catalog_db?authSource=admin
      database: catalog_db

arka:
  catalog:
    use-in-memory-repository: ${USE_IN_MEMORY:true}
```

- [ ] **Step 5: Correr tests del adaptador**

```bash
./gradlew :infrastructure:driven-adapters:mongo-repository:test
```

- [ ] **Step 6: Commit**

```bash
git add ms-catalog/infrastructure/driven-adapters/
git commit -m "feat(catalog): mongodb adapter with ProductDocument mapping"
```

---

### Task 1.5: REST Entry Point — ms-catalog

**Files:**
- Create: `ms-catalog/infrastructure/entry-points/reactive-web/src/main/java/.../ProductRouter.java`
- Create: `ms-catalog/infrastructure/entry-points/reactive-web/src/main/java/.../ProductHandler.java`
- Create: `ms-catalog/infrastructure/entry-points/reactive-web/src/main/java/.../CreateProductRequest.java`
- Create: `ms-catalog/infrastructure/entry-points/reactive-web/src/main/java/.../ProductResponse.java`
- Create: `ms-catalog/infrastructure/entry-points/reactive-web/src/test/.../ProductRouterTest.java`

- [ ] **Step 1: Tests de la API REST**

```java
@WebFluxTest
@Import({ProductRouter.class, ProductHandler.class})
class ProductRouterTest {
    @Autowired WebTestClient webTestClient;
    @MockBean CreateProductUseCase createProductUseCase;
    @MockBean GetProductUseCase getProductUseCase;

    @Test
    void shouldCreateProductAndReturn202() {
        Product product = Product.create("SKU-001", "Monitor", "Tech",
                                          new BigDecimal("500"), "prov-1")
                                 .startProviderValidation();
        when(createProductUseCase.execute(any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(product));

        webTestClient.post().uri("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"sku":"SKU-001","name":"Monitor","category":"Tech",
                 "price":500.00,"providerId":"prov-1"}
                """)
            .exchange()
            .expectStatus().isAccepted()
            .expectBody()
            .jsonPath("$.productId").isNotEmpty()
            .jsonPath("$.status").isEqualTo("VALIDANDO_PROVEEDOR");
    }

    @Test
    void shouldReturn404WhenProductNotFound() {
        when(getProductUseCase.execute(any()))
            .thenReturn(Mono.error(new IllegalArgumentException("Not found")));

        webTestClient.get().uri("/api/v1/products/nonexistent")
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

- [ ] **Step 2: Implementar DTOs**

```java
// CreateProductRequest.java
public record CreateProductRequest(String sku, String name, String category,
                                    BigDecimal price, String providerId) {
    public String[] toDomainArgs() {
        return new String[]{sku, name, category};
    }
}

// ProductResponse.java
public record ProductResponse(String productId, String sku, String name,
                               String category, BigDecimal price,
                               String status, LocalDateTime createdAt) {
    public static ProductResponse fromDomain(Product p) {
        return new ProductResponse(p.getProductId(), p.getSku(), p.getName(),
            p.getCategory(), p.getPrice(), p.getStatus().name(), p.getCreatedAt());
    }
}
```

- [ ] **Step 3: Implementar ProductRouter (Functional)**

```java
@Configuration
public class ProductRouter {
    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return RouterFunctions.route()
            .path("/api/v1/products", builder -> builder
                .POST("", handler::create)
                .GET("/{id}", handler::findById)
                .GET("", handler::findAll))
            .build();
    }
}
```

- [ ] **Step 4: Implementar ProductHandler**

```java
@Component
@RequiredArgsConstructor
public class ProductHandler {
    private final CreateProductUseCase createProductUseCase;
    private final GetProductUseCase getProductUseCase;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateProductRequest.class)
            .flatMap(req -> createProductUseCase.execute(
                req.sku(), req.name(), req.category(), req.price(), req.providerId()))
            .map(ProductResponse::fromDomain)
            .flatMap(p -> ServerResponse.accepted().bodyValue(p))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())))
            .onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Map.of("error", "Internal error")));
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        return getProductUseCase.execute(request.pathVariable("id"))
            .map(ProductResponse::fromDomain)
            .flatMap(p -> ServerResponse.ok().bodyValue(p))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> findAll(ServerRequest request) {
        return getProductUseCase.findAll()
            .map(ProductResponse::fromDomain)
            .collectList()
            .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }
}
```

- [ ] **Step 5: Correr tests**

```bash
./gradlew :infrastructure:entry-points:reactive-web:test
```

Expected: BUILD SUCCESSFUL, 3+ tests passing

- [ ] **Step 6: Commit**

```bash
git add ms-catalog/infrastructure/entry-points/
git commit -m "feat(catalog): REST API with functional routing (create/get products)"
```

---

### Task 1.6: Kafka Consumer — ms-catalog (escucha respuestas de saga)

**Files:**
- Create: `ms-catalog/infrastructure/driven-adapters/kafka-consumer/src/main/java/.../CatalogEventConsumer.java`
- Create: `ms-catalog/infrastructure/driven-adapters/kafka-producer/src/main/java/.../KafkaEventPublisher.java`
- Create: `.../CatalogEventConsumerTest.java`

- [ ] **Step 1: Tests del consumer**

```java
@ExtendWith(MockitoExtension.class)
class CatalogEventConsumerTest {
    @Mock ConfirmProductUseCase confirmProductUseCase;
    @Mock RejectProductUseCase rejectProductUseCase;
    CatalogEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CatalogEventConsumer(confirmProductUseCase, rejectProductUseCase);
    }

    @Test
    void shouldConfirmProductOnStockCreatedEvent() {
        when(confirmProductUseCase.execute("prod-1"))
            .thenReturn(Mono.just(mock(Product.class)));
        
        StepVerifier.create(consumer.onStockCreated(Map.of("productId", "prod-1")))
            .verifyComplete();
        
        verify(confirmProductUseCase).execute("prod-1");
    }

    @Test
    void shouldRejectProductOnProviderRejected() {
        when(rejectProductUseCase.execute("prod-1", "Proveedor no encontrado"))
            .thenReturn(Mono.just(mock(Product.class)));

        StepVerifier.create(consumer.onProviderRejected(
            Map.of("productId", "prod-1", "reason", "Proveedor no encontrado")))
            .verifyComplete();
    }
}
```

- [ ] **Step 2: Implementar KafkaEventPublisher**

```java
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {
    private final ReactiveKafkaProducerTemplate<String, Object> kafkaTemplate;

    @Override
    public Mono<Void> publish(String topic, String aggregateId, Object payload) {
        return kafkaTemplate.send(topic, aggregateId, payload)
            .doOnSuccess(r -> log.info("Published to {}: key={}", topic, aggregateId))
            .then();
    }
}
```

- [ ] **Step 3: Implementar CatalogEventConsumer**

```java
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {
    private final ConfirmProductUseCase confirmProductUseCase;
    private final RejectProductUseCase rejectProductUseCase;

    @KafkaListener(topics = "inventory.stock-created", groupId = "catalog-group")
    public Mono<Void> onStockCreated(Map<String, Object> event) {
        String productId = (String) event.get("productId");
        return confirmProductUseCase.execute(productId).then();
    }

    @KafkaListener(topics = "provider.provider-rejected", groupId = "catalog-group")
    public Mono<Void> onProviderRejected(Map<String, Object> event) {
        String productId = (String) event.get("productId");
        String reason = (String) event.get("reason");
        return rejectProductUseCase.execute(productId, reason).then();
    }
}
```

- [ ] **Step 4: Correr tests**

```bash
./gradlew :domain:usecase:test :infrastructure:driven-adapters:kafka-consumer:test
```

- [ ] **Step 5: Commit**

```bash
git add ms-catalog/infrastructure/driven-adapters/kafka-*/
git commit -m "feat(catalog): kafka producer and consumer for HU1 saga"
```

---

### Task 1.7: Outbox Pattern — ms-catalog

**Files:**
- Create: `.../OutboxEvent.java` (document MongoDB)
- Create: `.../OutboxRepository.java` (Spring Data Reactive)
- Create: `.../OutboxPoller.java` (@Scheduled que publica a Kafka)
- Modify: `MongoProductAdapter.java` — guardar outbox en misma transacción

- [ ] **Step 1: Implementar OutboxEvent document**

```java
@Data
@Document(collection = "outbox")
public class OutboxEvent {
    @Id
    private String id;
    private String topic;
    private String aggregateId;
    private Object payload;
    private boolean published;
    private LocalDateTime createdAt;

    public static OutboxEvent of(String topic, String aggregateId, Object payload) {
        OutboxEvent e = new OutboxEvent();
        e.setId(UUID.randomUUID().toString());
        e.setTopic(topic);
        e.setAggregateId(aggregateId);
        e.setPayload(payload);
        e.setPublished(false);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
```

- [ ] **Step 2: Implementar OutboxPoller**

```java
@Component
@RequiredArgsConstructor
public class OutboxPoller {
    private final OutboxRepository outboxRepository;
    private final ReactiveKafkaProducerTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        outboxRepository.findByPublished(false)
            .flatMap(event -> kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                .then(outboxRepository.save(markPublished(event))))
            .subscribe();
    }

    private OutboxEvent markPublished(OutboxEvent e) {
        e.setPublished(true);
        return e;
    }
}
```

- [ ] **Step 3: Modificar MongoProductAdapter para usar outbox transaccional**

```java
@Override
public Mono<Product> save(Product product) {
    ProductDocument doc = ProductDocument.fromDomain(product);
    OutboxEvent outbox = OutboxEvent.of(
        "catalog.product-validated",
        product.getProductId(),
        Map.of("productId", product.getProductId(), "providerId", product.getProviderId())
    );
    // MongoDB no tiene transacciones multi-documento sin replica set
    // → guardar ambos en secuencia; en producción usar replica set para tx atómica
    return mongoRepository.save(doc)
        .then(outboxRepository.save(outbox))
        .thenReturn(doc.toDomain());
}
```

- [ ] **Step 4: Commit**

```bash
git add ms-catalog/
git commit -m "feat(catalog): outbox pattern with mongo poller for guaranteed event delivery"
```

---

## FASE 2 — ms-inventory

> Dueño transaccional del stock. PostgreSQL R2DBC. Pessimistic Locking. Event Sourcing local.

### Task 2.1: Scaffold + Domain Model

**Files:**
- Create: `arka/ms-inventory/` (scaffold)
- Create: `.../model/stockrecord/StockRecord.java`
- Create: `.../model/stockrecord/StockStatus.java`
- Create: `.../model/stockrecord/gateways/StockRepository.java`

- [ ] **Step 1: Scaffolding**

```bash
cd arka && gradle generate --type=reactive-usecase-microservice --name ms-inventory --package co.com.bancolombia
```

- [ ] **Step 2: Tests del dominio**

```java
class StockRecordTest {
    @Test
    void shouldCreateStockWithZeroQuantity() {
        StockRecord s = StockRecord.create("prod-1", 10);
        assertThat(s.getCurrentStock()).isEqualTo(0);
        assertThat(s.getStatus()).isEqualTo(StockStatus.ACTIVE);
    }

    @Test
    void shouldReserveStockWhenSufficient() {
        StockRecord s = StockRecord.create("prod-1", 10).withStock(50);
        StockRecord reserved = s.reserve(20);
        assertThat(reserved.getCurrentStock()).isEqualTo(30);
    }

    @Test
    void shouldThrowWhenInsufficientStock() {
        StockRecord s = StockRecord.create("prod-1", 10).withStock(5);
        assertThatThrownBy(() -> s.reserve(20))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient stock");
    }

    @Test
    void shouldTransitionToDepletedWhenStockBelowThreshold() {
        StockRecord s = StockRecord.create("prod-1", 10).withStock(15);
        StockRecord afterReserve = s.reserve(10);
        assertThat(afterReserve.getCurrentStock()).isEqualTo(5);
        assertThat(afterReserve.getStatus()).isEqualTo(StockStatus.DEPLETED);
    }
}
```

- [ ] **Step 3: Implementar StockRecord**

```java
@Value
@Builder(toBuilder = true)
public class StockRecord {
    String stockId;
    String productId;
    int currentStock;
    int threshold;
    StockStatus status;
    LocalDateTime updatedAt;

    public static StockRecord create(String productId, int threshold) {
        return StockRecord.builder()
            .stockId(UUID.randomUUID().toString())
            .productId(productId)
            .currentStock(0)
            .threshold(threshold)
            .status(StockStatus.ACTIVE)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public StockRecord withStock(int qty) {
        int newStock = this.currentStock + qty;
        return toBuilder()
            .currentStock(newStock)
            .status(newStock <= threshold ? StockStatus.DEPLETED : StockStatus.ACTIVE)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public StockRecord reserve(int qty) {
        if (currentStock < qty) {
            throw new IllegalStateException(
                "Insufficient stock for " + productId + ". Available: " + currentStock + ", requested: " + qty);
        }
        int newStock = currentStock - qty;
        return toBuilder()
            .currentStock(newStock)
            .status(newStock <= threshold ? StockStatus.DEPLETED : StockStatus.ACTIVE)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public boolean isBelowThreshold() {
        return currentStock <= threshold;
    }
}
```

- [ ] **Step 4: Correr tests**

```bash
./gradlew :domain:model:test
```

- [ ] **Step 5: Commit**

```bash
git add ms-inventory/domain/model/
git commit -m "feat(inventory): domain model StockRecord with state machine"
```

---

### Task 2.2: Use Cases — ms-inventory

**Files:**
- Create: `.../usecase/InitializeStockUseCase.java`
- Create: `.../usecase/ReserveStockUseCase.java`
- Create: `.../usecase/UpdateStockUseCase.java`
- Tests correspondientes

- [ ] **Step 1: Test InitializeStockUseCase (triggered by catalog.product-validated)**

```java
@ExtendWith(MockitoExtension.class)
class InitializeStockUseCaseTest {
    @Mock StockRepository stockRepository;
    @Mock EventPublisher eventPublisher;
    InitializeStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitializeStockUseCase(stockRepository, eventPublisher);
    }

    @Test
    void shouldCreateStockAndPublishStockCreatedEvent() {
        StockRecord stock = StockRecord.create("prod-1", 10);
        when(stockRepository.save(any())).thenReturn(Mono.just(stock));
        when(eventPublisher.publish(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("prod-1", 10))
            .assertNext(s -> {
                assertThat(s.getProductId()).isEqualTo("prod-1");
                assertThat(s.getCurrentStock()).isEqualTo(0);
            })
            .verifyComplete();

        verify(eventPublisher).publish(eq("inventory.stock-created"), eq("prod-1"), any());
    }
}
```

- [ ] **Step 2: Implementar use cases**

```java
@RequiredArgsConstructor
public class InitializeStockUseCase {
    private final StockRepository stockRepository;
    private final EventPublisher eventPublisher;

    public Mono<StockRecord> execute(String productId, int threshold) {
        StockRecord stock = StockRecord.create(productId, threshold);
        return stockRepository.save(stock)
            .flatMap(saved -> eventPublisher
                .publish("inventory.stock-created", productId,
                    Map.of("productId", productId, "stockId", saved.getStockId()))
                .thenReturn(saved));
    }
}

@RequiredArgsConstructor
public class ReserveStockUseCase {
    private final StockRepository stockRepository;
    private final EventPublisher eventPublisher;

    public Mono<StockRecord> execute(String productId, int qty, String orderId) {
        return stockRepository.findByProductIdWithLock(productId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Stock not found: " + productId)))
            .flatMap(stock -> {
                try {
                    StockRecord reserved = stock.reserve(qty);
                    return stockRepository.save(reserved)
                        .flatMap(saved -> {
                            String topic = saved.isBelowThreshold()
                                ? "inventory.stock-low-detected"
                                : "inventory.stock-reserved";
                            return eventPublisher.publish(topic, productId,
                                Map.of("productId", productId, "orderId", orderId,
                                       "qty", qty, "remaining", saved.getCurrentStock()))
                                .thenReturn(saved);
                        });
                } catch (IllegalStateException e) {
                    return eventPublisher.publish("inventory.stock-insufficient", productId,
                        Map.of("productId", productId, "orderId", orderId, "reason", e.getMessage()))
                        .then(Mono.error(e));
                }
            });
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ms-inventory/domain/usecase/
git commit -m "feat(inventory): use cases initialize/reserve/update stock"
```

---

### Task 2.3: R2DBC PostgreSQL Adapter — ms-inventory

**Files:**
- Create: `.../R2dbcStockAdapter.java`
- Create: `.../StockRecordEntity.java` (Spring Data R2DBC)
- Create: `.../R2dbcStockRepository.java` (interface)
- Create: `resources/schema.sql` (DDL)

- [ ] **Step 1: DDL**

```sql
-- schema.sql
CREATE TABLE IF NOT EXISTS stock_records (
    stock_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(255) UNIQUE NOT NULL,
    current_stock INT NOT NULL DEFAULT 0,
    threshold INT NOT NULL DEFAULT 10,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stock_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(255) NOT NULL,
    delta INT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    order_id VARCHAR(255),
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 2: Implementar R2dbcStockRepository con SELECT FOR UPDATE**

```java
public interface R2dbcStockRepository extends ReactiveCrudRepository<StockRecordEntity, String> {
    Mono<StockRecordEntity> findByProductId(String productId);

    @Query("SELECT * FROM stock_records WHERE product_id = :productId FOR UPDATE")
    Mono<StockRecordEntity> findByProductIdWithLock(String productId);
}
```

- [ ] **Step 3: Implementar R2dbcStockAdapter**

```java
@Repository
@RequiredArgsConstructor
public class R2dbcStockAdapter implements StockRepository {
    private final R2dbcStockRepository repository;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<StockRecord> save(StockRecord stock) {
        return repository.save(StockRecordEntity.fromDomain(stock))
            .map(StockRecordEntity::toDomain);
    }

    @Override
    public Mono<StockRecord> findByProductId(String productId) {
        return repository.findByProductId(productId).map(StockRecordEntity::toDomain);
    }

    @Override
    @Transactional
    public Mono<StockRecord> findByProductIdWithLock(String productId) {
        return repository.findByProductIdWithLock(productId).map(StockRecordEntity::toDomain);
    }
}
```

- [ ] **Step 4: Kafka Consumer — escucha catalog.product-validated**

```java
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {
    private final InitializeStockUseCase initializeStockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final ProcessedEventRepository processedEvents;

    @KafkaListener(topics = "catalog.product-validated", groupId = "inventory-group")
    public Mono<Void> onProductValidated(Map<String, Object> event) {
        String eventId = (String) event.get("eventId");
        String productId = (String) event.get("productId");
        
        return processedEvents.existsById(eventId)
            .flatMap(alreadyProcessed -> alreadyProcessed
                ? Mono.empty()  // idempotencia
                : initializeStockUseCase.execute(productId, 10)
                    .then(processedEvents.save(new ProcessedEvent(eventId))));
    }

    @KafkaListener(topics = "order.order-created", groupId = "inventory-group")
    public Mono<Void> onOrderCreated(Map<String, Object> event) {
        String eventId = (String) event.get("eventId");
        String productId = (String) event.get("productId");
        int qty = (int) event.get("qty");
        String orderId = (String) event.get("orderId");

        return processedEvents.existsById(eventId)
            .flatMap(alreadyProcessed -> alreadyProcessed
                ? Mono.empty()
                : reserveStockUseCase.execute(productId, qty, orderId)
                    .then(processedEvents.save(new ProcessedEvent(eventId))));
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add ms-inventory/infrastructure/
git commit -m "feat(inventory): r2dbc adapter with pessimistic locking + idempotent kafka consumer"
```

---

## FASE 3 — ms-provider

> Validación de proveedores. Solo necesita stub mínimo para que HU1 funcione.

### Task 3.1: Domain + Use Case mínimo

**Files:**
- Create: `arka/ms-provider/` (scaffold)
- Create: `.../model/provider/Provider.java`
- Create: `.../usecase/ValidateProviderUseCase.java`

- [ ] **Step 1: Scaffold + dominio básico**

```java
// Provider.java
@Value
@Builder
public class Provider {
    String providerId;
    String name;
    boolean active;

    public boolean isValid() { return active; }
}
```

- [ ] **Step 2: ValidateProviderUseCase**

```java
@RequiredArgsConstructor
public class ValidateProviderUseCase {
    private final ProviderRepository providerRepository;
    private final EventPublisher eventPublisher;

    public Mono<Void> execute(String providerId, String productId) {
        return providerRepository.findById(providerId)
            .flatMap(provider -> {
                if (provider.isValid()) {
                    return eventPublisher.publish("provider.provider-validated", productId,
                        Map.of("productId", productId, "providerId", providerId));
                } else {
                    return eventPublisher.publish("provider.provider-rejected", productId,
                        Map.of("productId", productId, "reason", "Provider inactive"));
                }
            })
            .switchIfEmpty(eventPublisher.publish("provider.provider-rejected", productId,
                Map.of("productId", productId, "reason", "Provider not found")));
    }
}
```

- [ ] **Step 3: Kafka Consumer para catalog.product-validated**

```java
@KafkaListener(topics = "catalog.product-validated", groupId = "provider-group")
public Mono<Void> onProductValidated(Map<String, Object> event) {
    return validateProviderUseCase.execute(
        (String) event.get("providerId"),
        (String) event.get("productId")
    );
}
```

- [ ] **Step 4: Commit**

```bash
git add ms-provider/
git commit -m "feat(provider): minimal stub for HU1 saga - validate provider on product-validated event"
```

---

## FASE 4 — ms-cart

> Carrito temporal. MongoDB. Entrada a la Saga HU4.

### Task 4.1: Domain + Use Cases

**Files:**
- Create: `arka/ms-cart/` (scaffold)
- Create: `.../model/cart/Cart.java`
- Create: `.../model/cart/CartItem.java`
- Create: `.../usecase/AddItemUseCase.java`
- Create: `.../usecase/CheckoutUseCase.java`

- [ ] **Step 1: Tests del dominio Cart**

```java
class CartTest {
    @Test
    void shouldAddItemToCart() {
        Cart cart = Cart.create("user-1");
        Cart updated = cart.addItem("prod-1", 2, new BigDecimal("500.00"), "Monitor 4K");
        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getSnapshotPrice()).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldUpdateQuantityForExistingItem() {
        Cart cart = Cart.create("user-1")
                        .addItem("prod-1", 2, new BigDecimal("500.00"), "Monitor 4K");
        Cart updated = cart.addItem("prod-1", 3, new BigDecimal("500.00"), "Monitor 4K");
        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void shouldNotCheckoutEmptyCart() {
        Cart cart = Cart.create("user-1");
        assertThatThrownBy(cart::checkout)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty");
    }
}
```

- [ ] **Step 2: Implementar Cart**

```java
@Value
@Builder(toBuilder = true)
public class Cart {
    String cartId;
    String userId;
    List<CartItem> items;
    CartStatus status;
    LocalDateTime updatedAt;

    public static Cart create(String userId) {
        return Cart.builder()
            .cartId(UUID.randomUUID().toString())
            .userId(userId)
            .items(new ArrayList<>())
            .status(CartStatus.ACTIVE)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public Cart addItem(String productId, int qty, BigDecimal priceSnapshot, String name) {
        List<CartItem> newItems = new ArrayList<>(items);
        newItems.stream()
            .filter(i -> i.getProductId().equals(productId))
            .findFirst()
            .ifPresentOrElse(
                existing -> {
                    newItems.remove(existing);
                    newItems.add(existing.withQuantity(existing.getQuantity() + qty));
                },
                () -> newItems.add(CartItem.of(productId, qty, priceSnapshot, name))
            );
        return toBuilder().items(newItems).updatedAt(LocalDateTime.now()).build();
    }

    public Cart checkout() {
        if (items.isEmpty()) throw new IllegalStateException("Cannot checkout empty cart");
        return toBuilder().status(CartStatus.CHECKOUT).updatedAt(LocalDateTime.now()).build();
    }
}
```

- [ ] **Step 3: CheckoutUseCase publica CheckoutSolicitado a Kafka**

```java
@RequiredArgsConstructor
public class CheckoutUseCase {
    private final CartRepository cartRepository;
    private final EventPublisher eventPublisher;

    public Mono<Cart> execute(String cartId) {
        return cartRepository.findById(cartId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Cart not found: " + cartId)))
            .map(Cart::checkout)
            .flatMap(cart -> cartRepository.save(cart)
                .flatMap(saved -> eventPublisher.publish("cart.checkout-requested", cartId,
                    Map.of("cartId", cartId, "userId", saved.getUserId(),
                           "items", saved.getItems()))
                .thenReturn(saved)));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add ms-cart/
git commit -m "feat(cart): domain Cart with price snapshot, add/remove items, checkout"
```

---

## FASE 5 — ms-order

> Máquina de estados de pedidos. Saga Coreografiada HU4. PostgreSQL R2DBC.

### Task 5.1: Domain Order con State Machine

**Files:**
- Create: `arka/ms-order/` (scaffold)
- Create: `.../model/order/Order.java`
- Create: `.../model/order/OrderStatus.java`
- Create: `.../model/order/OrderItem.java`

- [ ] **Step 1: Tests de la máquina de estados**

```java
class OrderTest {
    @Test
    void shouldFollowHappyPathSaga() {
        Order order = Order.create("user-1", List.of(OrderItem.of("prod-1", 2, new BigDecimal("500"))));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        Order validating = order.startValidation();
        assertThat(validating.getStatus()).isEqualTo(OrderStatus.VALIDATING);

        Order reserving = validating.stockReserved();
        assertThat(reserving.getStatus()).isEqualTo(OrderStatus.RESERVED);

        Order paying = reserving.startPayment();
        assertThat(paying.getStatus()).isEqualTo(OrderStatus.PAYING);

        Order completed = paying.complete();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void shouldCancelFromAnyIntermediateState() {
        Order order = Order.create("user-1", List.of(OrderItem.of("prod-1", 2, new BigDecimal("500"))))
                           .startValidation();
        Order cancelled = order.cancel("Catalog validation failed");
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Catalog validation failed");
    }
}
```

- [ ] **Step 2: Implementar Order**

```java
@Value
@Builder(toBuilder = true)
public class Order {
    String orderId;
    String userId;
    List<OrderItem> items;
    BigDecimal totalAmount;
    OrderStatus status;
    String cancellationReason;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static Order create(String userId, List<OrderItem> items) {
        BigDecimal total = items.stream()
            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Order.builder()
            .orderId(UUID.randomUUID().toString())
            .userId(userId).items(items).totalAmount(total)
            .status(OrderStatus.PENDING)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    public Order startValidation() {
        requireStatus(OrderStatus.PENDING, "startValidation");
        return toBuilder().status(OrderStatus.VALIDATING).updatedAt(LocalDateTime.now()).build();
    }

    public Order stockReserved() {
        requireStatus(OrderStatus.VALIDATING, "stockReserved");
        return toBuilder().status(OrderStatus.RESERVED).updatedAt(LocalDateTime.now()).build();
    }

    public Order startPayment() {
        requireStatus(OrderStatus.RESERVED, "startPayment");
        return toBuilder().status(OrderStatus.PAYING).updatedAt(LocalDateTime.now()).build();
    }

    public Order complete() {
        requireStatus(OrderStatus.PAYING, "complete");
        return toBuilder().status(OrderStatus.COMPLETED).updatedAt(LocalDateTime.now()).build();
    }

    public Order cancel(String reason) {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }
        return toBuilder().status(OrderStatus.CANCELLED)
            .cancellationReason(reason).updatedAt(LocalDateTime.now()).build();
    }

    private void requireStatus(OrderStatus required, String op) {
        if (status != required)
            throw new IllegalStateException("Cannot " + op + " from " + status);
    }
}
```

- [ ] **Step 3: CreateOrderUseCase**

```java
@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public Mono<Order> execute(String userId, List<OrderItem> items) {
        Order order = Order.create(userId, items).startValidation();
        return orderRepository.save(order)
            .flatMap(saved -> eventPublisher.publish("order.order-created", saved.getOrderId(),
                Map.of("orderId", saved.getOrderId(), "userId", userId, "items",
                       items.stream().map(i -> Map.of(
                           "productId", i.getProductId(), "qty", i.getQuantity())).toList()))
            .thenReturn(saved));
    }
}
```

- [ ] **Step 4: Kafka Consumer que escucha saga responses**

```java
@Component
@RequiredArgsConstructor
public class OrderSagaConsumer {
    private final AdvanceOrderUseCase advanceOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    @KafkaListener(topics = "inventory.stock-reserved", groupId = "order-group")
    public Mono<Void> onStockReserved(Map<String, Object> event) {
        return advanceOrderUseCase.stockReserved((String) event.get("orderId"));
    }

    @KafkaListener(topics = "inventory.stock-insufficient", groupId = "order-group")
    public Mono<Void> onStockInsufficient(Map<String, Object> event) {
        return cancelOrderUseCase.execute((String) event.get("orderId"), "Stock insuficiente");
    }

    @KafkaListener(topics = "payment.payment-approved", groupId = "order-group")
    public Mono<Void> onPaymentApproved(Map<String, Object> event) {
        return advanceOrderUseCase.paymentApproved((String) event.get("orderId"));
    }

    @KafkaListener(topics = "payment.payment-rejected", groupId = "order-group")
    public Mono<Void> onPaymentRejected(Map<String, Object> event) {
        return cancelOrderUseCase.execute((String) event.get("orderId"), "Pago rechazado");
    }
}
```

- [ ] **Step 5: Timeout Detection Cronjob**

```java
@Component
@RequiredArgsConstructor
public class OrderTimeoutDetector {
    private final OrderRepository orderRepository;
    private final CancelOrderUseCase cancelOrderUseCase;

    @Scheduled(fixedDelay = 60_000)
    public void detectTimedOutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        orderRepository.findByStatusInAndUpdatedAtBefore(
            List.of(OrderStatus.VALIDATING, OrderStatus.RESERVED, OrderStatus.PAYING), cutoff)
            .flatMap(order -> cancelOrderUseCase.execute(order.getOrderId(), "Timeout"))
            .subscribe();
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add ms-order/
git commit -m "feat(order): order state machine + saga consumer + timeout detection"
```

---

## FASE 6 — ms-payment

> Procesamiento de pagos. Circuit Breaker. ACL para pasarelas externas.

### Task 6.1: Domain + Use Cases mínimos

- [ ] **Step 1: Dominio Payment**

```java
@Value @Builder(toBuilder = true)
public class Payment {
    String paymentId;
    String orderId;
    BigDecimal amount;
    PaymentStatus status;
    String gatewayReference;
    LocalDateTime processedAt;

    public static Payment create(String orderId, BigDecimal amount) {
        return Payment.builder()
            .paymentId(UUID.randomUUID().toString())
            .orderId(orderId).amount(amount)
            .status(PaymentStatus.PENDING)
            .build();
    }

    public Payment approve(String gatewayRef) {
        return toBuilder().status(PaymentStatus.APPROVED)
            .gatewayReference(gatewayRef).processedAt(LocalDateTime.now()).build();
    }

    public Payment reject(String reason) {
        return toBuilder().status(PaymentStatus.REJECTED)
            .processedAt(LocalDateTime.now()).build();
    }
}
```

- [ ] **Step 2: ProcessPaymentUseCase con Circuit Breaker**

```java
@RequiredArgsConstructor
public class ProcessPaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;  // Port → ACL
    private final EventPublisher eventPublisher;

    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "fallback")
    public Mono<Payment> execute(String orderId, BigDecimal amount) {
        Payment payment = Payment.create(orderId, amount);
        return paymentRepository.save(payment)
            .flatMap(saved -> paymentGateway.process(saved)
                .flatMap(ref -> {
                    Payment approved = saved.approve(ref);
                    return paymentRepository.save(approved)
                        .flatMap(p -> eventPublisher.publish("payment.payment-approved", orderId,
                            Map.of("orderId", orderId, "paymentId", p.getPaymentId()))
                        .thenReturn(p));
                })
                .onErrorResume(e -> {
                    Payment rejected = saved.reject(e.getMessage());
                    return paymentRepository.save(rejected)
                        .flatMap(p -> eventPublisher.publish("payment.payment-rejected", orderId,
                            Map.of("orderId", orderId, "reason", e.getMessage()))
                        .thenReturn(p));
                }));
    }

    public Mono<Payment> fallback(String orderId, BigDecimal amount, Exception e) {
        return Mono.error(new RuntimeException("Payment gateway unavailable: " + e.getMessage()));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ms-payment/
git commit -m "feat(payment): payment processing with circuit breaker and ACL gateway"
```

---

## FASE 7 — ms-notifications

> Consumidor pasivo. Strategy Pattern para Email/SMS/Push.

### Task 7.1: Strategy Pattern + Event Consumer

- [ ] **Step 1: Port + estrategias**

```java
// Port
public interface NotificationChannel {
    String channelName();
    Mono<Void> send(String recipient, String subject, String body);
}

// Strategy: Email
@Component
public class EmailChannel implements NotificationChannel {
    public String channelName() { return "EMAIL"; }
    public Mono<Void> send(String recipient, String subject, String body) {
        // integración con servicio de email (SendGrid, SES, etc.)
        return Mono.fromRunnable(() -> log.info("Sending email to {}: {}", recipient, subject));
    }
}

// Strategy: SMS (stub)
@Component
public class SmsChannel implements NotificationChannel {
    public String channelName() { return "SMS"; }
    public Mono<Void> send(String recipient, String subject, String body) {
        return Mono.fromRunnable(() -> log.info("Sending SMS to {}: {}", recipient, body));
    }
}
```

- [ ] **Step 2: SendNotificationUseCase**

```java
@RequiredArgsConstructor
public class SendNotificationUseCase {
    private final Map<String, NotificationChannel> channels;
    private final NotificationRepository notificationRepository;

    public Mono<Void> send(String channelName, String recipient, String subject, String body) {
        NotificationChannel channel = channels.get(channelName);
        if (channel == null) return Mono.error(new IllegalArgumentException("Unknown channel: " + channelName));
        
        return channel.send(recipient, subject, body)
            .then(notificationRepository.save(
                Notification.of(channelName, recipient, subject, body)));
    }
}
```

- [ ] **Step 3: Consumer de eventos**

```java
@KafkaListener(topics = {"order.order-confirmed", "order.order-cancelled", 
                          "inventory.stock-low-detected"}, groupId = "notifications-group")
public Mono<Void> onEvent(Map<String, Object> event) {
    String eventType = (String) event.get("eventType");
    return switch (eventType) {
        case "ORDER_CONFIRMED" -> sendNotificationUseCase.send("EMAIL",
            (String) event.get("userEmail"), "Orden confirmada",
            "Tu orden " + event.get("orderId") + " fue confirmada.");
        case "STOCK_LOW" -> sendNotificationUseCase.send("SMS",
            ADMIN_PHONE, "Stock bajo",
            "Producto " + event.get("productId") + " tiene stock bajo.");
        default -> Mono.empty();
    };
}
```

- [ ] **Step 4: Commit**

```bash
git add ms-notifications/
git commit -m "feat(notifications): strategy pattern with email/sms channels + event consumers"
```

---

## FASE 8 — ms-reporter

> Event Store append-only. CQRS read model. Reportes a S3.

### Task 8.1: Event Store + Queries

- [ ] **Step 1: Tabla domain_events (DDL)**

```sql
CREATE TABLE IF NOT EXISTS domain_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_domain_events_aggregate ON domain_events (aggregate_type, aggregate_id);
CREATE INDEX idx_domain_events_type ON domain_events (event_type);
CREATE INDEX idx_domain_events_payload ON domain_events USING GIN (payload);
```

- [ ] **Step 2: Consumer que persiste todos los eventos**

```java
@KafkaListener(topics = {"catalog.product-validated", "inventory.stock-created",
    "order.order-created", "order.order-confirmed", "order.order-cancelled",
    "payment.payment-approved"}, groupId = "reporter-group")
public Mono<Void> onAnyEvent(Map<String, Object> event) {
    DomainEvent domainEvent = DomainEvent.builder()
        .eventType((String) event.get("eventType"))
        .aggregateId((String) event.get("aggregateId"))
        .aggregateType((String) event.get("aggregateType"))
        .payload(event)
        .occurredAt(LocalDateTime.now())
        .build();
    return domainEventRepository.save(domainEvent).then();
}
```

- [ ] **Step 3: Commit**

```bash
git add ms-reporter/
git commit -m "feat(reporter): append-only event store with JSONB and GIN indexes"
```

---

## FASE 9 — api-gateway

> Spring Cloud Gateway + validación JWT (Microsoft Entra ID).

### Task 9.1: Spring Cloud Gateway configuración

- [ ] **Step 1: Scaffold como Spring Boot project estándar (no scaffold Bancolombia)**

```bash
# Generar con Spring Initializr:
# Dependencies: Spring Cloud Gateway, Spring Security, OAuth2 Resource Server
```

- [ ] **Step 2: application.yaml con rutas**

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: catalog
          uri: http://ms-catalog:8081
          predicates:
            - Path=/api/v1/products/**
          filters:
            - name: JwtAuthFilter
        - id: inventory
          uri: http://ms-inventory:8082
          predicates:
            - Path=/api/v1/stock/**
          filters:
            - name: JwtAuthFilter
        - id: orders
          uri: http://ms-order:8083
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - name: JwtAuthFilter
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://login.microsoftonline.com/${TENANT_ID}/v2.0
```

- [ ] **Step 3: JwtAuthFilter que propaga X-User-Email**

```java
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<Object> {
    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> exchange.getPrincipal()
            .cast(JwtAuthenticationToken.class)
            .flatMap(token -> {
                String email = token.getToken().getClaimAsString("email");
                ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Email", email)
                    .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            })
            .switchIfEmpty(chain.filter(exchange));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add api-gateway/
git commit -m "feat(gateway): spring cloud gateway with jwt validation and x-user-email propagation"
```

---

## FASE 10 — Cross-cutting

### Task 10.1: Dockerfiles multi-stage

- [ ] **Step 1: Dockerfile por microservicio (ejemplo ms-catalog)**

```dockerfile
# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :applications:app-service:bootJar -x test

# Runtime stage
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /app/applications/app-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Actualizar docker-compose.yml para incluir microservicios**

```yaml
ms-catalog:
  build: ./ms-catalog
  ports:
    - "8081:8081"
  environment:
    SPRING_DATA_MONGODB_URI: mongodb://arka:arka123@mongo:27017/catalog_db?authSource=admin
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    USE_IN_MEMORY: "false"
  depends_on: [kafka, mongo]
  networks: [arka-net]
  labels:
    - "traefik.enable=true"
    - "traefik.http.routers.catalog.rule=PathPrefix(`/api/v1/products`)"
```

### Task 10.2: GitHub Actions CI/CD

- [ ] **Step 1: Crear .github/workflows/ci.yml**

```yaml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Test ms-catalog
        run: cd arka/ms-catalog && ./gradlew test jacocoTestReport
      - name: Test ms-inventory
        run: cd arka/ms-inventory && ./gradlew test jacocoTestReport
      - name: Upload coverage
        uses: codecov/codecov-action@v4

  build-docker:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Build Docker images
        run: cd arka && docker compose build
```

- [ ] **Step 2: Commit**

```bash
git add .github/ arka/ms-*/Dockerfile
git commit -m "ci: github actions for test + docker build on main"
```

---

## Resumen del Plan

| Fase | Componente | Estimado | Prioridad |
|---|---|---|---|
| 0 | Infra Docker Compose | 2h | **Crítico** |
| 1 | ms-catalog (dominio + REST + Kafka + Outbox) | 8h | **Crítico** |
| 2 | ms-inventory (dominio + R2DBC + Pessimistic Lock + Kafka) | 8h | **Crítico** |
| 3 | ms-provider (stub mínimo) | 3h | **Crítico** |
| 4 | ms-cart (MongoDB + checkout) | 4h | Alto |
| 5 | ms-order (saga + state machine + timeout) | 8h | Alto |
| 6 | ms-payment (circuit breaker + ACL) | 5h | Alto |
| 7 | ms-notifications (strategy pattern) | 3h | Medio |
| 8 | ms-reporter (event store + CQRS) | 4h | Medio |
| 9 | api-gateway (JWT + routing) | 3h | Alto |
| 10 | Cross-cutting (Dockerfiles + CI/CD) | 4h | Medio |

**Total estimado: ~52 horas de implementación**

**Punto de validación HU1 completa (saga end-to-end):**
Completar fases 0 + 1 + 2 + 3 → probar con Kafka UI que:
1. `POST /api/v1/products` → aparece `catalog.product-validated` en Kafka
2. ms-provider consume → aparece `provider.provider-validated`
3. ms-inventory consume → aparece `inventory.stock-created`
4. ms-catalog consume → producto en DB con status `CONFIRMADO`
