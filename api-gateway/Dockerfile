FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY gradle.properties .
COPY settings.gradle .
COPY src src
COPY --from=gradle-dists gradle-9.2.1-bin.zip /tmp/gradle-9.2.1-bin.zip
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S arka && adduser -S arka -G arka
COPY --from=builder /app/build/libs/api-gateway.jar app.jar
USER arka
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
