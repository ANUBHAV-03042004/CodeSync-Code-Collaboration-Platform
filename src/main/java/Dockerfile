# ── Generic Dockerfile for all CodeSync microservices ────────────────────────
# Copy this file into each service directory and update ARTIFACT_NAME.
#
# Usage:
#   docker build --build-arg ARTIFACT_NAME=auth-service -t codesync/auth-service .

ARG ARTIFACT_NAME=service

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
ARG ARTIFACT_NAME
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
ARG ARTIFACT_NAME

# Security: run as non-root
RUN addgroup -S codesync && adduser -S codesync -G codesync
USER codesync

WORKDIR /app
COPY --from=build /app/target/${ARTIFACT_NAME}-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]