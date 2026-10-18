# Multi-stage Dockerfile for Quarkus JVM mode
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew --version

COPY src src
RUN ./gradlew build -Dquarkus.package.jar.type=fast-jar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /deployments

COPY --from=build /workspace/build/quarkus-app/lib/ /deployments/lib/
COPY --from=build /workspace/build/quarkus-app/*.jar /deployments/
COPY --from=build /workspace/build/quarkus-app/app/ /deployments/app/
COPY --from=build /workspace/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1001

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
