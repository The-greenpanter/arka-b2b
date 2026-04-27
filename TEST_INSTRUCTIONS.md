# ARKA — Instrucciones de test y ejecución local

> No pude correr los tests en el sandbox (Java 11 vs Java 21 requerido + gradle
> necesita red para descargar distribución). Estas son las instrucciones
> exactas para correrlo en tu máquina.

## Prerequisitos

| Herramienta | Versión mínima | Verificar con |
|-------------|----------------|---------------|
| JDK         | 21             | `java -version` |
| Docker      | 24+            | `docker --version` |
| Docker Compose | v2          | `docker compose version` |

Si no tienes Java 21, usa [SDKMAN](https://sdkman.io):
```bash
sdk install java 21.0.5-tem
sdk use java 21.0.5-tem
```

## 1. Tests unitarios de ms-catalog (sin Docker, offline)

```bash
cd arka/ms-catalog
./gradlew test
```

Debería correr **30 tests** distribuidos así:

| Módulo | Archivo | Tests | Qué verifica |
|---|---|---|---|
| domain/model | `ProductTest` | 14 | State machine del aggregate `Product` (transiciones válidas e inválidas) |
| domain/usecase | `RegisterProductUseCaseTest` | 4 | Generación UUID, preservación de ID custom, propagación de errores, ArgumentCaptor |
| domain/usecase | `GetProductUseCaseTest` | 2 | Happy path + 404 |
| domain/usecase | `ConfirmProductUseCaseTest` | 3 | Confirmación OK, producto no existe, ya confirmado |
| entry-points/reactive-web | `RouterRestTest` | 7 | Integración HTTP con `@WebFluxTest` + `WebTestClient` |

**Reporte HTML:**
`ms-catalog/applications/app-service/build/reports/tests/test/index.html`

## 2. Tests con cobertura (Jacoco)

```bash
./gradlew test jacocoTestReport
```

Reporte: `build/reports/jacoco/test/html/index.html`

Meta del scaffold Bancolombia: **≥90%** de cobertura en domain/usecase.
El `build.gradle` ya falla el build si baja de ese umbral.

## 3. Ejecutar ms-catalog en modo standalone (in-memory, sin MongoDB)

```bash
cd arka/ms-catalog
./gradlew :app-service:bootRun
```

Se levanta en `http://localhost:8081` con `InMemoryProductRepository`.

**Smoke test con curl:**
```bash
# Crear producto
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Teclado Mecánico Pro",
    "category":"TECLADOS",
    "basePriceUsd":129.99,
    "supplier":{"supplierId":"sup-001","name":"TechDistribuidora","leadTimeDays":7}
  }'

# Listar
curl http://localhost:8081/api/products

# Confirmar (transición manual EN_CREACION → CONFIRMADO)
curl -X POST http://localhost:8081/api/products/{productId}/confirm
```

## 4. Levantar infraestructura completa (Kafka + MongoDB + PostgreSQL + Traefik)

```bash
cd arka
chmod +x scripts/init-multiple-postgres-dbs.sh
docker compose up -d
```

Servicios expuestos:

| Servicio | URL | Notas |
|---|---|---|
| Kafka | `localhost:9092` | PLAINTEXT, sin auth |
| Kafka UI | http://localhost:8090 | Inspecciona topics y mensajes |
| MongoDB | `localhost:27017` | user: `arka` / pass: `arka123` |
| PostgreSQL | `localhost:5432` | 4 DBs: inventory, order, payment, provider |
| Traefik dashboard | http://localhost:8070 | Rutas dinámicas |

Parar: `docker compose down` (conserva datos). Borrar datos: `docker compose down -v`.

## 5. Correr ms-catalog contra MongoDB real

Edita `ms-catalog/applications/app-service/src/main/resources/application.yaml`:
```yaml
arka:
  catalog:
    use-in-memory-repository: false  # <- cambia a false
```

Luego `./gradlew :app-service:bootRun`. Spring activará `MongoProductRepositoryAdapter`
vía `@ConditionalOnProperty`.

## 6. Tests de ms-inventory

```bash
cd arka/ms-inventory
./gradlew test
```

52+ tests (state machine `Stock`, use cases con StepVerifier, `RouterRestTest`).

## 7. Mutation testing (PIT) — avanzado

```bash
./gradlew pitest
```

Reporte: `build/reports/pitest/index.html`. PIT muta el código y verifica si los
tests detectan los cambios — mide **calidad** de los tests, no solo cobertura.

Analogía biológica: cobertura = qué % del ADN secuenciaste; mutation testing =
qué % de mutaciones reales detecta tu ensayo funcional.

## Troubleshooting

**`Could not find tools.jar`** → Estás en JDK 8. Instala JDK 21.

**`.git/config.lock` bloqueado** → El scaffold quedó con un repo corrupto.
```bash
rm -rf .git && git init -b main
```

**Gradle no descarga distribución** → Usa wrapper offline o configura proxy en
`~/.gradle/gradle.properties`:
```properties
systemProp.http.proxyHost=tu-proxy
systemProp.http.proxyPort=8080
```

**Puerto 8081/8082 ocupado** → Cambia `server.port` en `application.yaml`.

## Orden recomendado para validar progreso

1. `cd arka/ms-catalog && ./gradlew test` → 30 tests verdes
2. `./gradlew :app-service:bootRun` → smoke test HTTP con curl
3. `cd ../ms-inventory && ./gradlew test` → tests de inventory
4. `cd .. && docker compose up -d` → infra lista
5. Re-correr ambos microservicios apuntando a DBs reales
6. Siguiente fase: Outbox Pattern + Kafka (pendiente)
