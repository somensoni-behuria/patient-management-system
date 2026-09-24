# Minimal runtime image used by the release pipeline.
#
# The service fat-jar is built once (natively) in CI and copied in here. A Spring Boot fat-jar is
# architecture-independent Java bytecode, so this image builds for every target platform
# (linux/amd64, linux/arm64) with no per-arch compilation — just the multi-arch JRE base + a COPY.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run as a non-root, non-numeric-collision UID (no RUN step, so the build stays pure metadata).
USER 1000:1000

COPY app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
