# ms-catalog · ARKA B2B E-commerce

Primer microservicio del sistema **ARKA** (Cohorte 5 enyoi). Gestiona el
catálogo de productos: registro, consulta, listado y confirmación.
Es el origen de la **HU1: Saga de Registro de Producto**.

## Arquitectura

Generado a partir del **Bancolombia Clean Architecture Scaffold**
(plugin `co.com.bancolombia.cleanArchitecture`). Usa Hexagonal + Reactive
Stack (Spring WebFlux + Reactor).

```
ms-catalog/
├── domain/
│   ├── model/                  ← Entidades, Value Objects, Ports (interfaces)
│   │   └── product/
│   │       ├── Product.java
│   │       ├── ProductStatus.java
│   │       ├── Supplier.java
│   │       └── gateways/ProductRepository.java
│   └── usecase/                ← Reglas de aplicación, sin Spring
│       └── product/
│           ├── RegisterProductUseCase.java
│           ├── GetProductUseCase.java
│           ├── ListProductsUseCase.java
│           └── ConfirmProductUseCase.java
├── infrastructure/
│   ├── entry-points/reactive-web/   ← Adapter de entrada (REST WebFlux)
│   │   ├── api/Handler.java
│   │   ├── api/RouterRest.java
│   │   └── api/dto/{ProductRequest, ProductResponse}.java
│   └── driven-adapters/mongo-repository/  ← Adapter de salida (MongoDB)
│       ├── ProductDocument.java
│       ├── ProductMongoRepository.java
│       └── MongoProductRepositoryAdapter.java
└── applications/app-service/   ← Composition root (Spring Boot main)
    └── config/
        ├── UseCasesConfig.java
        └── InMemoryProductRepository.java   ← Repo en memoria para arrancar sin Mongo
```

**Regla de oro:** las dependencias siempre apuntan hacia adentro
(hacia `domain/model`). El dominio no conoce a Spring, ni a Mongo, ni a
HTTP — eso es trabajo de los adapters.

## Endpoints REST

| Método | Ruta                              | Descripción                                  |
|--------|-----------------------------------|----------------------------------------------|
| POST   | `/api/products`                   | Registra un producto (estado `EN_CREACION`)  |
| GET    | `/api/products`                   | Lista todos (`?status=...&category=...`)     |
| GET    | `/api/products/{id}`              | Detalle de un producto                        |
| POST   | `/api/products/{id}/confirm`      | Atajo manual: avanza la state machine a `CONFIRMADO` |
| GET    | `/actuator/health`                | Health check (Spring Actuator)                |

### Ejemplo: registrar un teclado mecánico

```bash
curl -X POST http://localhost:8081/api/products \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Teclado Mecánico Pro",
    "description": "Switches Cherry MX Blue",
    "category": "TECLADOS",
    "basePriceUsd": 129.99,
    "taxRate": 0.19,
    "minOrderQty": 1,
    "maxOrderQty": 100,
    "supplier": {
      "supplierId": "sup-001",
      "name": "TechDistribuidora SAS",
      "leadTimeDays": 7
    },
    "attributes": {
      "keyType": "Mecánico",
      "layout": "QWERTY",
      "backlight": true
    }
  }'
```

Respuesta: `201 Created` + JSON con el `productId` generado.

## Build & Run

### Modo dev (sin MongoDB, repo en memoria)

```bash
./gradlew clean :app-service:bootRun
```

El servicio queda en `http://localhost:8081`. La flag
`arka.catalog.use-in-memory-repository: true` en `application.yaml`
activa el `InMemoryProductRepository` y desactiva el adapter de Mongo.

### Modo prod (con MongoDB)

1. Levanta MongoDB:

   ```bash
   docker run -d --name arka-mongo -p 27017:27017 mongo:7
   ```

2. En `application.yaml` cambia:

   ```yaml
   arka:
     catalog:
       use-in-memory-repository: false
   ```

3. Arranca de nuevo. Los productos quedan persistidos en
   `arka_catalog.products`.

### Tests

```bash
./gradlew test
./gradlew jacocoTestReport
```

## Estado actual y próximos pasos

Implementado en este primer corte:

- Modelo de dominio Product con state machine (EN_CREACION → CONFIRMADO).
- Use cases Register / Get / List / ConfirmProduct.
- Endpoints REST WebFlux con functional routing.
- Adapter MongoDB reactivo + repo in-memory de respaldo.

Pendiente para los siguientes microservicios y la saga HU1 completa:

- Outbox Pattern: escribir `OutboxEvent` en la misma transacción que el
  Product, y un poller que publique a Kafka (`producto-eventos`).
- Listener Kafka que consuma `ProveedorValidado` (de ms-provider) e
  `InventarioInicializado` (de ms-inventory) para avanzar la state
  machine sin intervención manual.
- Idempotencia: tabla `processed_events` para deduplicar eventos.
- Tests de arquitectura ArchUnit (ya configurados en el scaffold) que
  verifiquen que el dominio no importa Spring/Mongo.

## Referencias

- `notas-curso/04-Clean-Architecture-Spring/scaffold-bancolombia.md` —
  Documentación del plugin de Bancolombia.
- `MARCO_CONCEPTUAL_ARKA.md` — Marco completo de los 9 microservicios.
- `notas-curso/17-Proyecto-Arka/diagramas-c4.md`,
  `diagrama-er.md`, `diagrama-eventos.md` — Diagramas C4, ER y de eventos.
