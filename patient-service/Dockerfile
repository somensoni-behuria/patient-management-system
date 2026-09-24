# syntax=docker/dockerfile:1
# Build context is the repository root (see docker-compose.yml).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY billing-service/pom.xml billing-service/pom.xml
COPY patient-service/pom.xml patient-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY integration-tests/pom.xml integration-tests/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl patient-service -am dependency:go-offline || true
COPY patient-service/src patient-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl patient-service -am -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/patient-service/target/*.jar app.jar
USER app
EXPOSE 4000
ENTRYPOINT ["java", "-jar", "app.jar"]
