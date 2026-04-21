# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source (faster rebuilds)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build, skipping tests (tests run in CI, not the image build)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
# Use UBI (Universal Base Image) — Red Hat's preferred base image for OpenShift.
# ubi9-minimal is small, RHEL-compatible, and passes OpenShift security scans.
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

WORKDIR /deployments

# OpenShift runs containers as a random non-root UID in group 0.
# Give group 0 write access so the app can create temp files.
RUN chmod -R g+rwX /deployments

COPY --from=build /app/target/*.jar app.jar

# Document the port (OpenShift reads this for route auto-detection)
EXPOSE 8080

# JVM tuning for containers: respect cgroup memory/CPU limits
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
