# syntax=docker/dockerfile:1

# --- build -------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper first so the dependency layer is cached across source edits.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# --- runtime -----------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user: the previous image ran as root with no USER directive.
RUN groupadd --system adi && useradd --system --gid adi --home /app adi

# curl is installed solely for the healthcheck. eclipse-temurin:*-jre is Ubuntu
# based, and the minimal Ubuntu image ships neither curl nor wget, so the
# previous `wget` healthcheck could only ever fail -- which Docker reports as
# the container being unhealthy, failing `depends_on: service_healthy`.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Rules-as-data lives outside the JAR, so the service reads it from a mounted path.
# RULES_PATH must point here; see infra/docker-compose.yml.
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /rules && chown -R adi:adi /app /rules

USER adi
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport \
    -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Exec form only. This previously read
#   CMD ["curl", ...] || exit 1
# which mixes the JSON exec form with a shell operator: Docker's JSON detection
# stops at the closing bracket, so the `|| exit 1` was almost certainly dropped
# silently. It was also redundant -- `curl -fsS` already exits non-zero on an
# HTTP error, a connection refusal or a timeout, and that exit code is all
# Docker looks at.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["curl", "-fsS", "http://127.0.0.1:8080/actuator/health", "-o", "/dev/null"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
