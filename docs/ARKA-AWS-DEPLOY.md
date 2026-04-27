# ARKA B2B — Despliegue en AWS ECS Fargate

> Documentación del deploy cloud realizado el 2026-04-27.
> Para la presentación de sustentación del curso AceleraTI Cohorte 5 (enyoi).

---

## URLs de acceso

| Endpoint | URL |
|---|---|
| **Frontend** | http://arka-alb-1673054971.us-east-1.elb.amazonaws.com |
| **API Gateway** | http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/api/v1/ |
| **Health check** | http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/actuator/health |

---

## Arquitectura en AWS

```
Internet
    │
    ▼
┌─────────────────────────────────────────┐
│  ALB: arka-alb (us-east-1)              │
│  /api/*, /actuator/* → api-gateway:8080  │
│  /*                  → frontend:80       │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  VPC: vpc-0b157ebc574892ac8 (172.31.0.0/16)         │
│  Cluster: arka-cluster (ECS Fargate)                │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │  Service Discovery: arka.local (Cloud Map)   │   │
│  │                                              │   │
│  │  Infraestructura:                            │   │
│  │    kafka.arka.local:9092  (Kafka KRaft 3.8)  │   │
│  │    mongodb.arka.local:27017 (MongoDB 7)      │   │
│  │    postgresql.arka.local:5432 (Postgres 16)  │   │
│  │                                              │   │
│  │  Microservicios:                             │   │
│  │    ms-catalog.arka.local:8081                │   │
│  │    ms-inventory.arka.local:8082              │   │
│  │    ms-order.arka.local:8083                  │   │
│  │    ms-payment.arka.local:8084                │   │
│  │    ms-provider.arka.local:8085               │   │
│  │    ms-cart.arka.local:8086                   │   │
│  │    ms-notifications.arka.local:8087          │   │
│  │    ms-reporter.arka.local:8088               │   │
│  │    api-gateway.arka.local:8080               │   │
│  │    frontend.arka.local:80                    │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## Recursos AWS creados

| Recurso | ID / Nombre |
|---|---|
| ECS Cluster | arka-cluster |
| ALB | arka-alb-1673054971.us-east-1.elb.amazonaws.com |
| Security Group | sg-0eb87b2e7a57c3261 (arka-sg) |
| Cloud Map Namespace | arka.local (ns-tkyjs3hojp463eto) |
| IAM Role | ecsTaskExecutionRole |
| CloudWatch Logs | /arka |
| ECR Repos | arka/ms-catalog, ms-inventory, ms-provider, ms-cart, ms-order, ms-payment, ms-notifications, ms-reporter, api-gateway, arka-frontend, postgresql |

---

## Stack tecnológico por servicio

| Servicio | Imagen | CPU | RAM | DB |
|---|---|---|---|---|
| kafka | apache/kafka:3.8.0 | 512 | 1024 MB | — |
| mongodb | mongo:7 | 512 | 1024 MB | — |
| postgresql | ECR (custom init) | 512 | 1024 MB | — |
| ms-catalog | ECR (Spring Boot 4) | 256 | 512 MB | MongoDB |
| ms-inventory | ECR (Spring Boot 4) | 256 | 512 MB | PostgreSQL |
| ms-provider | ECR (Spring Boot 4) | 256 | 512 MB | — |
| ms-cart | ECR (Spring Boot 4) | 256 | 512 MB | MongoDB |
| ms-order | ECR (Spring Boot 4) | 256 | 512 MB | PostgreSQL |
| ms-payment | ECR (Spring Boot 4) | 256 | 512 MB | PostgreSQL |
| ms-notifications | ECR (Spring Boot 4) | 256 | 512 MB | — |
| ms-reporter | ECR (Spring Boot 4) | 256 | 512 MB | PostgreSQL |
| api-gateway | ECR (Spring Boot 3.4 + Cloud Gateway) | 256 | 512 MB | — |
| frontend | ECR (nginx + Vue 3) | 256 | 512 MB | — |

---

## Cómo hacer redeploy (actualizar una imagen)

```bash
# 1. Reconstruir el JAR (desde arka/)
cd ms-catalog && ./gradlew :app-service:bootJar -x test --no-daemon && cd ..

# 2. Reconstruir y subir imagen a ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 863771938027.dkr.ecr.us-east-1.amazonaws.com
docker build -f scripts/Dockerfile.prebuilt --build-arg JAR_FILE=applications/app-service/build/libs/ms-catalog.jar -t arka/ms-catalog ./ms-catalog
docker tag arka/ms-catalog:latest 863771938027.dkr.ecr.us-east-1.amazonaws.com/arka/ms-catalog:latest
docker push 863771938027.dkr.ecr.us-east-1.amazonaws.com/arka/ms-catalog:latest

# 3. Forzar nuevo deploy en ECS
aws ecs update-service --cluster arka-cluster --service arka-ms-catalog --force-new-deployment --region us-east-1
```

---

## Ver logs en tiempo real

```bash
# Logs de un microservicio específico
aws logs tail /arka --log-stream-name-prefix ms-catalog --follow --region us-east-1

# Ver eventos de un servicio ECS
aws ecs describe-services --cluster arka-cluster --services arka-ms-catalog --region us-east-1 --query 'services[0].events[:5]'
```

---

## Comandos útiles para la demo

```bash
# Estado de todos los servicios
aws ecs describe-services --cluster arka-cluster --region us-east-1 \
  --services arka-kafka arka-mongodb arka-postgresql arka-ms-catalog arka-ms-inventory arka-ms-provider arka-ms-cart arka-ms-order arka-ms-payment arka-ms-notifications \
  --query 'services[*].{Name:serviceName,Running:runningCount}' --output table

# Health del API Gateway vía ALB
curl http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/actuator/health

# Crear producto (HU1 — saga completa)
curl -X POST http://arka-alb-1673054971.us-east-1.elb.amazonaws.com/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop Pro","description":"High performance laptop","price":1500.00,"providerId":"provider-001","category":"ELECTRONICS"}'
```

---

## Decisiones de arquitectura cloud

### Por qué ECS Fargate y no EC2
- **Serverless containers**: no hay que gestionar VMs ni parches de SO
- **Escala a cero**: si no hay tráfico, no se cobra CPU/RAM en espera
- **Alineado con el curso**: tema 13 (Docker + Cloud) + tema 14 (Microservicios)

### Por qué Cloud Map (service discovery) y no variables de entorno con IPs
- En Fargate, las IPs de los containers cambian en cada redeploy
- Cloud Map registra automáticamente los IPs de las tasks y los expone como DNS
- Los microservicios se llaman por `kafka.arka.local:9092` igual que en docker-compose

### Por qué Spring Boot 3.4 en api-gateway vs 4.0 en microservicios
- Spring Cloud Gateway 2024.0.0 requiere Spring Boot 3.4.x
- Spring Cloud aún no lanzó versión compatible con Spring Boot 4.0.x
- Los microservicios no usan Spring Cloud, pueden usar Boot 4.0

### Alternativas cloud consideradas

| Opción | Por qué no |
|---|---|
| AWS MSK para Kafka | ~$0.75/hora — caro para demo |
| Amazon RDS para PostgreSQL | Viable, pero añade complejidad de gestión |
| AWS DocumentDB para MongoDB | 5x más caro que correr mongo:7 en ECS |
| Railway / Render | Sin soporte nativo para Kafka |
