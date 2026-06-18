FROM openjdk:17-jdk-slim AS builder

# Install utilities Maven may need
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl git unzip && \
    rm -rf /var/lib/apt/lists/*

# Create non‑root user for security
ARG USER=appuser
ARG UID=1000
RUN adduser --disabled-password --gecos "" --uid ${UID} ${USER}
WORKDIR /app

# Copy Maven wrapper and pom for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Pre‑download dependencies (cached by Docker layer)
RUN ./mvnw -B dependency:go-offline

# Copy source code
COPY src/ src/

# Build the JAR (skip tests for CI speed)
RUN ./mvnw -B -DskipTests clean package -Pproduction

# Runtime stage – thin image
FROM openjdk:17-jdk-slim AS runtime
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Use same non‑root user
ARG USER=appuser
ARG UID=1000
RUN adduser --disabled-password --gecos "" --uid ${UID} ${USER}
USER ${USER}

# Port handling (Railway provides $PORT)
ENV SERVER_PORT=${PORT:-8080}
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java","-jar","app.jar"]
