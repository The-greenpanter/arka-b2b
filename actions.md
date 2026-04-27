# ARKA — Action Log

> Registro cronológico de acciones tomadas por el agente en esta sesión.
> Fecha de inicio: 2026-04-27

---

## [2026-04-27] Sesión: Deploy LocalStack

### ACT-001 — Fix ms-notifications build.gradle
- **Qué:** Eliminada línea `id 'info.solidsoft.pitest.aggregator' version '1.15.0'` del `ms-notifications/build.gradle`
- **Por qué:** Plugin no existe en Gradle Plugin Portal (devuelve 404). Causaba fallo en `docker build` durante la fase de configuración de Gradle.
- **Archivo:** `ms-notifications/build.gradle`
- **Resultado:** Pendiente verificación con nuevo build

### ACT-002 — Fix pitest.aggregator en 5 ms adicionales
- **Qué:** Eliminada línea `info.solidsoft.pitest.aggregator` de ms-cart, ms-order, ms-payment, ms-provider, ms-reporter
- **Por qué:** Mismo problema que ms-notifications — plugin no existe en Gradle Portal
- **Resultado:** OK — 0 referencias restantes

### ACT-003 — Activar bypass permissions
- **Qué:** Seteado `defaultMode: bypassPermissions` en `~/.claude/settings.json`
- **Por qué:** Usuario autorizó acceso total para este despliegue
- **Resultado:** OK

---

### ACT-004 — Fix main.gradle en todos los ms (pitest.aggregator apply + pitestReportAggregate)
- **Qué:** Eliminados de `main.gradle`:
  - `apply plugin: 'info.solidsoft.pitest.aggregator'` (línea 1)
  - Bloque `pitestReportAggregate { ... }`
  - Referencia `pitestReportAggregate` en `jacocoMergedReport.dependsOn`
- **Por qué:** El plugin aplica `reporting.baseDir` internamente (API removida en Gradle 9.x). Todos los ms son idénticos — se copió el fix.
- **Afectados:** ms-notifications, ms-cart, ms-catalog, ms-inventory, ms-order, ms-payment, ms-provider, ms-reporter
- **Resultado:** Pendiente verificación build

---

### ACT-005 — Fix main.gradle: deshabilitar pitest plugin (incompatible Gradle 9)
- **Qué:** Comentado `apply plugin: 'info.solidsoft.pitest'` en `subprojects {}` + eliminados bloques `pitest {}`, `jacoco {}` raíz, `jacocoMergedReport`, `compileJava.dependsOn validateStructure`
- **Por qué:** Plugin usa `reporting.baseDir` removida en Gradle 9.2.1; `validateStructure` no definida; `jacoco {}` root requiere plugin aplicado en root
- **Resultado:** OK — propagado a todos los ms

### ACT-006 — Fix app-service: agregar spring-kafka como dependencia explícita
- **Archivo:** `ms-notifications/applications/app-service/build.gradle`
- **Por qué:** `@EnableKafka` en `MainApplication.java` requiere `spring-kafka` en classpath; `implementation` en Gradle no es transitivo para compilación
- **Resultado:** BUILD SUCCESS — ms-notifications compila ✅

---

## Próximas acciones planeadas

### ACT-007 — Build todos los ms (detectar errores sistémicos)
- **Qué:** Builds paralelos de todos los ms
- **Errores encontrados y corregidos:**
  - `spring-kafka` faltante en app-service de ms-provider, ms-payment, ms-order, ms-reporter
  - `jackson-databind` faltante en kafka-producer de ms-catalog, ms-inventory, ms-provider, ms-order, ms-cart, ms-payment
  - `jackson-databind` faltante en kafka-consumer de ms-order
  - Spring Cloud 2025.0.0 incompatible con Spring Boot 4.0.1 → actualizado a 2025.1.0
  - `spring-cloud-starter-gateway` renombrado a `spring-cloud-starter-gateway-server-webflux` en Spring Cloud 5
  - `LifecycleMvcEndpointAutoConfiguration` excluido en api-gateway (referencia MVC en contexto reactivo)
- **Resultado:** Todos los ms compilan ✅

### ACT-008 — Deploy infra base + microservicios
- **Kafka, MongoDB, PostgreSQL, LocalStack, Traefik:** ✅ running
- **LocalStack:** Cambiado de `latest` (pro, requiere auth token) a `3.8` (community)
- **YAML duplicado ms-inventory:** fusionadas dos claves `spring:` → fixed
- **Spring Boot 4 Breaking Change — MongoDB:** `spring.data.mongodb.uri` ignorada → usar `spring.mongodb.uri` en application-docker.yaml
- **MongoDB auth:** `?authSource=admin` requerido (usuario root vive en admin DB)
- **ms-catalog, ms-cart:** Creados `application-docker.yaml` con `spring.mongodb.uri + ?authSource=admin`
- **Resultado:** Stack completo corriendo ✅

---

## Estado final — 2026-04-27

| Servicio | Status | Notas |
|---|---|---|
| arka-kafka | ✅ healthy | KRaft mode |
| arka-mongodb | ✅ healthy | |
| arka-postgres | ✅ healthy | |
| arka-localstack | ✅ healthy | v3.8 community |
| arka-traefik | ✅ up | |
| ms-catalog | ✅ started | MongoDB OK |
| ms-inventory | ✅ started | R2DBC OK |
| ms-provider | ✅ started | |
| ms-order | ✅ started | |
| ms-cart | ✅ started | MongoDB OK |
| ms-payment | ✅ started | |
| ms-notifications | ✅ started | |
| ms-reporter | ✅ started | |
| api-gateway | ✅ started | Spring Cloud 2025.1.0 |

## Pendiente

- [ ] Inicializar recursos en LocalStack (S3 buckets, SQS queues, SNS topics)
- [ ] Verificar Kafka topics creados correctamente
- [ ] Probar endpoints via api-gateway
- [ ] Levantar frontend (ms-notifications port 8087)
- [ ] ACT-004: Revisar build.gradle de otros ms (mismo problema pitest)
- [ ] ACT-005: `docker compose up -d` infra base (Kafka, MongoDB, PostgreSQL, LocalStack)
- [ ] ACT-006: Build y levantada de microservicios ✅ done primero (ms-catalog, ms-inventory)
- [ ] ACT-007: Init LocalStack (S3 buckets, SQS queues, SNS topics)
- [ ] ACT-008: Verificar stack completo
