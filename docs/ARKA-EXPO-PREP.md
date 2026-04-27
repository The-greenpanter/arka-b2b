# ARKA B2B — Preparación para la Exposición

## Narrativa central (apréndetela)

> "ARKA es un sistema B2B de microservicios reactivos implementado con Clean Architecture
> hexagonal, comunicación asíncrona por Kafka y sagas coreografiadas. Usamos LocalStack
> para simular los servicios AWS que usaríamos en producción — MSK para Kafka, RDS para
> PostgreSQL, S3 para almacenamiento de reportes. Nuestro código ya está preparado para eso;
> solo cambiaría la URL de conexión en las variables de entorno. Eso es exactamente lo que
> nos da la arquitectura hexagonal: el dominio no sabe ni le importa si está hablando con
> Kafka local o AWS MSK."

---

## Flujo de demo (15 minutos)

```
[0:00–2:00]  Arquitectura general
             → Mostrar diagrama Mermaid de ARKA-DOCUMENTACION.md
             → "9 microservicios, 1 api-gateway, Kafka como backbone"
             → Señalar docker-compose.yml: "todo esto corre en VPS cloud"

[2:00–6:00]  Demo live HU1 — Crear producto (la saga más impresionante)
             → Terminal izquierda: docker compose logs -f ms-catalog ms-provider ms-inventory
             → Terminal/Postman derecha: POST /api/v1/products
             → El evaluador ve en tiempo real cómo el evento viaja entre servicios
             → GET /api/v1/products/{id} → mostrar status: CONFIRMADO

[6:00–10:00] Code walkthrough — Clean Architecture
             → Abrir ms-catalog en IDE
             → Mostrar capas: model → usecase → infrastructure
             → Punto clave: "el dominio no tiene ninguna anotación Spring"
             → Mostrar EventPublisher.java: "esto es un puerto — el dominio define
               el contrato, la infraestructura lo implementa"

[10:00–13:00] SOLID + ACID en el código
             → S: RegisterProductUseCase hace UNA sola cosa
             → D: EventPublisher es interfaz — inversión de dependencias
             → ACID: mostrar R2DBC con transacciones en ms-payment

[13:00–15:00] LocalStack + AWS ECR
             → Mostrar LocalStack corriendo: docker ps
             → Mostrar AWS ECR con las imágenes subidas
             → Narrativa de arriba

[15:00+]    Q&A — ver banco de preguntas abajo
```

---

## Banco de preguntas del evaluador

### Clean Architecture / Hexagonal

**P: ¿Por qué los use cases no tienen `@Component` o `@Service`?**
R: El dominio no puede depender de Spring. Los use cases son lógica de negocio pura.
   El app-service los registra automáticamente con un ComponentScan que busca clases
   cuyo nombre termina en `UseCase` — el framework se adapta al dominio, no al revés.
   Mostrar: `UseCasesConfig.java` con el regex `^.+UseCase$`.

**P: ¿Cuál es la diferencia entre un puerto y un adaptador?**
R: El puerto es la interfaz que define el dominio — `EventPublisher`, `StockRepository`.
   El adaptador es la implementación concreta — `KafkaEventPublisher`, `R2dbcStockRepository`.
   El dominio solo conoce el puerto. La implementación puede cambiar sin tocar el dominio.
   Mostrar: `gateways/EventPublisher.java` (en model) vs `KafkaEventPublisher.java` (en infra).

**P: ¿Qué pasaría si quisieras cambiar MongoDB por DynamoDB?**
R: Solo cambiaríamos el adaptador `MongoProductRepository`. El use case, el modelo de dominio,
   los tests — nada cambia. Eso es exactamente lo que nos da la inversión de dependencias.

**P: ¿Por qué separan model, usecase e infrastructure en módulos Gradle distintos?**
R: Gradle enforcea las dependencias en tiempo de compilación. Si alguien accidentalmente
   importa Spring en el modelo, el build falla. No es convención — es constraint técnico.

---

### SOLID

**P: Explícame Single Responsibility con un ejemplo de tu código.**
R: `RegisterProductUseCase` tiene una responsabilidad: registrar un producto e iniciar la saga.
   No valida HTTP, no serializa JSON, no escribe en Kafka directamente. Cada una de esas
   responsabilidades vive en su propio adaptador.

**P: ¿Dónde ves Open/Closed en el proyecto?**
R: `EventPublisher` está abierto a extensión (puedo crear `SQSEventPublisher`, `SNSEventPublisher`)
   y cerrado a modificación. Los use cases no cambian cuando agrego una nueva implementación.
   En el docker-compose, `@ConditionalOnProperty` selecciona cuál se activa.

**P: ¿Qué es inversión de dependencias y dónde la ven?**
R: Las capas de alto nivel (dominio) no dependen de las capas de bajo nivel (Kafka, PostgreSQL).
   Ambas dependen de abstracciones (interfaces/puertos). El flujo de dependencias va hacia adentro,
   nunca hacia afuera. Mostrar el diagrama de capas.

---

### Sagas y EDA

**P: ¿Por qué eligieron choreography saga en lugar de orchestration?**
R: En HU1 preferimos coreografía porque son pocos pasos y queremos bajo acoplamiento.
   Cada servicio reacciona a eventos sin conocer a los demás. Para HU4 (carrito→pedido→pago)
   usamos ms-order como orquestador porque la secuencia es más compleja y necesitamos
   compensación coordinada.

**P: ¿Qué pasa si ms-provider falla después de que ms-catalog emitió el evento?**
R: Tenemos compensación: ms-provider emitiría `provider.provider-rejected`, ms-catalog
   consumiría ese evento y transicionaría el producto a estado RECHAZADO. En la implementación
   actual el stub siempre acepta, pero el mecanismo está definido en la máquina de estados.

**P: ¿Cómo garantizan idempotencia en los consumers de Kafka?**
R: Configuramos `MANUAL_IMMEDIATE` ack mode — el offset solo se confirma en `doOnSuccess`.
   Si el procesamiento falla, el mensaje se re-procesa. Para idempotencia completa necesitaríamos
   el Outbox Pattern (documentado en el proyecto, pendiente implementación completa).

**P: ¿Por qué KRaft en lugar de Zookeeper?**
R: Kafka 3.3+ soporta KRaft nativo — el broker gestiona el quorum internamente sin necesidad
   de un proceso Zookeeper separado. Menos infraestructura, mismas garantías.

---

### ACID

**P: ¿Cómo garantizan atomicidad en el servicio de pagos?**
R: ms-payment usa PostgreSQL con R2DBC y transacciones reactivas. El pago se registra
   y el evento se emite solo si la transacción commitea. Mostramos el use case con
   `.transactional()` del operador Reactor.

**P: ¿Qué nivel de aislamiento usan?**
R: READ COMMITTED por defecto en PostgreSQL R2DBC. Para operaciones de reserva de stock
   usamos pessimistic locking (`SELECT FOR UPDATE`) para evitar race conditions cuando
   múltiples pedidos compiten por el mismo stock.

**P: ¿Por qué PostgreSQL para inventory/order/payment y MongoDB para catalog/cart?**
R: ACID vs flexibilidad de esquema. Los productos tienen atributos muy variables (electrónicos
   vs ropa vs alimentos) → MongoDB. Las transacciones financieras y de stock necesitan
   consistencia fuerte → PostgreSQL.

---

### Programación reactiva

**P: ¿Por qué WebFlux en lugar de Spring MVC?**
R: Un sistema B2B que maneja múltiples pedidos concurrentes se beneficia del modelo
   non-blocking. Con MVC, cada request ocupa un hilo durante toda la operación I/O.
   Con WebFlux, el mismo hilo atiende miles de requests concurrentes mientras espera
   respuestas de DB o Kafka.

**P: ¿Qué es un Mono?**
R: Un publisher reactivo que emite 0 o 1 elemento. Es el equivalente reactivo de
   `CompletableFuture<Optional<T>>`. `Flux<T>` emite 0..N elementos. Ambos son lazy —
   nada sucede hasta que alguien se suscribe.

---

## Archivos clave para mostrar en el IDE

```
ms-catalog/
├── domain/model/.../Product.java              ← entidad con máquina de estados
├── domain/model/.../gateways/EventPublisher.java ← puerto (interfaz)
├── domain/usecase/.../RegisterProductUseCase.java ← use case + saga trigger
├── infrastructure/.../KafkaEventPublisher.java    ← adaptador Kafka
├── infrastructure/.../ProviderValidatedConsumer.java ← consumer saga
└── applications/.../InMemoryEventPublisher.java   ← @ConditionalOnProperty

ms-inventory/
└── domain/usecase/.../CreateStockUseCase.java  ← emite inventory.stock-created
```

---

## Comandos para la demo en vivo

```bash
# 1. Ver que todo está corriendo
docker compose ps

# 2. Logs en tiempo real (terminal separada)
docker compose logs -f ms-catalog ms-provider ms-inventory

# 3. Crear producto (HU1) — pegar en Postman o terminal
curl -X POST http://187.77.192.190:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Monitor 4K Demo",
    "category": "MONITORES",
    "basePriceUsd": 599.99,
    "supplier": {
      "supplierId": "sup-001",
      "name": "DisplayCorp",
      "leadTimeDays": 14
    }
  }'

# 4. Ver el producto confirmado
curl http://187.77.192.190:8080/api/v1/products/{productId}
# Esperar status: "CONFIRMADO" — la saga completó

# 5. Mostrar LocalStack corriendo
docker exec arka-localstack awslocal s3 ls
docker exec arka-localstack awslocal sqs list-queues
```

---

## Si algo falla durante la demo

| Problema | Solución rápida |
|---|---|
| Microservicio no responde | `docker compose restart ms-catalog` |
| Kafka no conecta | `docker compose logs kafka` — verificar healthcheck |
| Base de datos caída | `docker compose restart postgresql` + wait 10s |
| Saga no completa | Verificar con `docker compose logs` — mostrar los logs igual (el flujo se ve) |
| VPS sin internet | Demo local desde laptop — LocalStack corre igual |

---

## Cierre de la expo

> "Lo más importante de ARKA no son los 9 microservicios — es que cada uno puede
> evolucionar independientemente, desplegarse por separado, y fallar sin tumbar el sistema
> completo. Eso es lo que nos dan la Clean Architecture y el diseño orientado a eventos:
> un sistema que escala como un ecosistema biológico, donde cada especie tiene su nicho
> y se comunica por señales químicas — eventos — sin dependencias directas."
