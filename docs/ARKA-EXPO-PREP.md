# ARKA B2B — Preparación para la Exposición

## Narrativa central (apréndetela)

> "ARKA es un sistema B2B de microservicios reactivos implementado con Clean Architecture
> hexagonal, comunicación asíncrona por Kafka y sagas coreografiadas. **El sistema está
> desplegado en producción en AWS ECS Fargate** — 13 contenedores corriendo en us-east-1
> comunicados por DNS privado. Las imágenes están en Amazon ECR y el tráfico entra por
> un Application Load Balancer. La arquitectura hexagonal nos permite esto sin tocar el
> dominio: solo cambiamos las variables de entorno para que Kafka apunte a `kafka.arka.local`
> en vez de `localhost`. Eso es exactamente la inversión de dependencias en acción."

### URLs en vivo (AWS)

| Endpoint | URL |
|---|---|
| Frontend | http://arka-alb-1673054971.us-east-1.elb.amazonaws.com |
| API | http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/api/v1/ |
| Health | http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/actuator/health |

---

## Flujo de demo (15 minutos)

```
[0:00–2:00]  Arquitectura general
             → Abrir browser: http://arka-alb-1673054971.us-east-1.elb.amazonaws.com
             → "Esto está corriendo ahora mismo en AWS ECS Fargate, us-east-1"
             → Mostrar diagrama de ARKA-AWS-DEPLOY.md: 13 contenedores, ALB, Cloud Map
             → "9 microservicios, 1 api-gateway, Kafka como backbone, todo en cloud"

[2:00–6:00]  Demo live HU1 — Crear producto (la saga más impresionante)
             → Terminal: aws logs tail /arka --log-stream-name-prefix ms-catalog --follow
             → Postman/curl: POST al ALB → /api/v1/products
             → El evaluador ve en CloudWatch cómo el evento viaja entre servicios
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

[13:00–15:00] Deploy cloud — AWS ECS
             → Mostrar ECS console: 13 servicios ACTIVE
             → Mostrar ECR: 11 repos con imágenes
             → "La arquitectura hexagonal hace que deploy en cloud sea solo variables de entorno"
             → Mostrar task definition: KAFKA_BOOTSTRAP_SERVERS=kafka.arka.local:9092

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
ALB="http://arka-alb-1673054971.us-east-1.elb.amazonaws.com"

# 1. Verificar que todo está corriendo en AWS
aws ecs describe-services --cluster arka-cluster --region us-east-1 \
  --services arka-kafka arka-mongodb arka-postgresql arka-ms-catalog arka-ms-inventory arka-ms-provider arka-ms-cart arka-ms-order arka-ms-payment arka-ms-notifications \
  --query 'services[*].{MS:serviceName,Running:runningCount}' --output table

# 2. Health check del sistema
curl $ALB/actuator/health

# 3. Logs en tiempo real (CloudWatch)
aws logs tail /arka --log-stream-name-prefix ms-catalog --follow --region us-east-1

# 4. Crear producto (HU1 — saga completa)
curl -X POST $ALB/api/v1/products \
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

# 5. Ver el producto confirmado (reemplazar {id})
curl $ALB/api/v1/products/{productId}
# Esperar status: "CONFIRMADO" — la saga completó en la nube
```

---

## Si algo falla durante la demo

| Problema | Solución rápida |
|---|---|
| ALB no responde | `aws ecs describe-services --cluster arka-cluster --services arka-api-gateway --region us-east-1` |
| Microservicio caído | `aws ecs update-service --cluster arka-cluster --service arka-ms-catalog --force-new-deployment --region us-east-1` |
| Kafka no conecta | `aws logs tail /arka --log-stream-name-prefix kafka --region us-east-1` |
| Saga no completa | Mostrar CloudWatch logs — el flujo se ve igual aunque falle |
| AWS tiene problemas | Fallback: VPS `http://187.77.192.190:8080` (docker compose corriendo en paralelo) |

---

## Cierre de la expo

> "Lo más importante de ARKA no son los 9 microservicios — es que cada uno puede
> evolucionar independientemente, desplegarse por separado, y fallar sin tumbar el sistema
> completo. Eso es lo que nos dan la Clean Architecture y el diseño orientado a eventos:
> un sistema que escala como un ecosistema biológico, donde cada especie tiene su nicho
> y se comunica por señales químicas — eventos — sin dependencias directas."
