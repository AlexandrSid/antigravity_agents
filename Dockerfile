# === Stage 1: Build ===
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# === Stage 2: Runtime (Quarkus Fast-JAR) ===
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /deployments
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/*.jar /deployments/
COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/app/ /deployments/app/
COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
