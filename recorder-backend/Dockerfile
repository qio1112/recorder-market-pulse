# recorder-backend/Dockerfile

# Use a slim JRE base
FROM eclipse-temurin:17 AS runtime

# Create a non-root user for safety (optional)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

# Set working directory
WORKDIR /app

ARG MARKET_PULSE_PATH
ARG BACKEND_APP_LOG_PATH
ARG BACKEND_APP_FILE_PATH

# Create directories for logs & uploads
RUN mkdir -p ${MARKET_PULSE_PATH} ${BACKEND_APP_LOG_PATH} ${BACKEND_APP_FILE_PATH}

# Copy the fat JAR in
COPY target/recorder-backend-0.0.1-SNAPSHOT.jar ./recorder-backend.jar

# Expose HTTP port
EXPOSE 8080

# Start your Spring Boot app, wiring in the paths
ENTRYPOINT ["java", "-jar", "recorder-backend.jar"]
