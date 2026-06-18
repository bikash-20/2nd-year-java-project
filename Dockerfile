FROM eclipse-temurin:17-jdk-alpine AS builder

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
