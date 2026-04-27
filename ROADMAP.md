# ARKA B2B — Roadmap y estado actual

## Estado (2026-04-14)

### Completado

| # | Entregable | Estado | Ubicación |
|---|---|---|---|
| 1 | Diagramas C4, ER, Kafka, Sagas, State Machines | Listo | `notas-curso/17-Proyecto-Arka/img/` |
| 2 | **ms-catalog** (MongoDB + WebFlux) | Core listo | `arka/ms-catalog/` |
| 3 | Tests ms-catalog (30 tests) | Listo | domain + usecase + reactive-web |
| 4 | **ms-inventory** (PostgreSQL R2DBC) | Core listo | `arka/ms-inventory/` |
| 5 | Tests ms-inventory (52+ tests) | Listo | domain + usecase + reactive-web |
| 6 | Docker Compose (Kafka KRaft + Mongo + PG + Traefik) | Listo | `arka/docker-compose.yml` |
| 7 | Instrucciones de test | Listo | `arka/TEST_INSTRUCTIONS.md` |

### Pendiente (prioridad descendente)

| # | Entregable | Por qué importa |
|---|---|---|
| 1 | **Outbox Pattern** en ms-catalog | Publicar `ProductValidatedEvent` atómico con save (sin dual-write) |
| 2 | **Kafka Producer/Consumer** en ambos ms | Orquestar saga HU1 |
| 3 | **ms-provider** | Validación real de proveedor (saga HU1 paso 1) |
| 4 | **ms-order** | Saga HU4 |
| 5 | **ms-cart** | Entrada de HU4 |
| 6 | **ms-payment** | Salida de HU4 |
| 7 | **api-gateway** (Spring Cloud Gateway) | Punto único de entrada |
| 8 | **ms-notifications** | Kafka consumer → email/SMS |
| 9 | **ms-reporter** | CQRS read model |

## Por qué ms-catalog + ms-inventory primero

La saga HU1 (crear producto end-to-end) es el **cambio de estado más importante**
del sistema. Si este flujo funciona, validas:

- Event sourcing básico (outbox)
- Comunicación asíncrona Kafka
- Compensación ante fallos (producto queda RECHAZADO si inventory falla)
- Eventual consistency entre Mongo y Postgres

Los demás microservicios son **variantes del mismo patrón** con datos distintos.
Una vez que HU1 funciona, replicar cuesta ~30% del tiempo del primero.

Analogía: una vez secuenciado el primer genoma bacteriano (1995, *H. influenzae*),
los siguientes se volvieron rutina. Lo caro fue la primera vez.

## Próximos pasos sugeridos (orden de rentabilidad)

### Paso 1 (30 min) — Validar lo que ya existe
```bash
cd arka/ms-catalog && ./gradlew test
cd ../ms-inventory && ./gradlew test
```
Ambos deben pasar. Si no, reportar errores antes de seguir.

### Paso 2 (1-2h) — Outbox Pattern en ms-catalog
Agregar colección `catalog_outbox` y un scheduler reactivo que publique a Kafka.
Esto habilita la saga HU1. Alto retorno: desbloquea ms-inventory como consumer.

### Paso 3 (2-3h) — Kafka Consumer en ms-inventory
Subscribir a `product.validated` → invocar `CreateStockUseCase` → emitir
`stock.created`. Cierra el primer arco de la saga.

### Paso 4 (1-2h) — ms-provider (mínimo viable)
Puede ser un stub que siempre responde OK con leadTime=7 días. Importa más el
contrato (Kafka topics) que la lógica real.

### Paso 5 (varias horas) — Resto de microservicios
En este punto ya tienes el patrón. Cada ms nuevo es **~400 líneas de código de
producción + tests** siguiendo el mismo molde.

## Decisión económica

Si tu objetivo es **entregar el curso** (no producción), con ms-catalog + ms-inventory
+ Outbox + sagas básicas ya tienes el 80% del valor pedagógico. Completar los 9
microservicios es 3-4x más trabajo por ~20% más de aprendizaje.

**Recomendación:** Avanza paso a paso, valida cada hito, y decide después de HU1
si vale la pena extender a HU4 o profundizar en observabilidad/security en lo
que ya tienes.
