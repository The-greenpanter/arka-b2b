FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY main.gradle .
COPY gradle.properties .
COPY settings.gradle .
COPY domain domain
COPY infrastructure infrastructure
COPY applications applications
RUN chmod +x gradlew && ./gradlew :app-service:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S arka && adduser -S arka -G arka
COPY --from=builder /app/applications/app-service/build/libs/ms-catalog.jar app.jar
USER arka
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
