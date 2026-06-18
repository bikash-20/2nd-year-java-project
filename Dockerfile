# Multi‑stage Dockerfile: build with Maven, run with lightweight JDK

# ---------- Builder stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source and build the JAR
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jdk-alpine AS runtime
WORKDIR /app

# Copy the repackaged Spring Boot JAR from the builder
COPY --from=builder /app/target/AutoRentWeb-1.0.jar app.jar

# Expose the port (Railway provides $PORT)
ENV SERVER_PORT=${PORT:-8080}
EXPOSE ${SERVER_PORT}

# Run the application
ENTRYPOINT ["java","-jar","app.jar"]

# Install utilities needed by Maven
RUN apk add --no-cache curl git unzip

# Create non-root user
ARG USER=appuser
ARG UID=1000
RUN adduser -D -u ${UID} -s /bin/sh -h /home/${USER} ${USER}
WORKDIR /app

# Maven wrapper and pom
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

# Source code
COPY src/ src/

# Build JAR
RUN ./mvnw -B -DskipTests clean package -Pproduction

# Runtime stage
FROM eclipse-temurin:17-jdk-alpine AS runtime
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Use same non-root user
ARG USER=appuser
ARG UID=1000
RUN adduser -D -u ${UID} -s /bin/sh -h /home/${USER} ${USER}
USER ${USER}

# Port handling
ENV SERVER_PORT=${PORT:-8080}
EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java","-jar","app.jar"]
