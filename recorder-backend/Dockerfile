# recorder-backend/Dockerfile

# Use a slim JRE base
FROM eclipse-temurin:17 AS runtime

# Set working directory
WORKDIR /app

ARG MARKET_PULSE_PATH
ARG BACKEND_APP_LOG_PATH
ARG BACKEND_APP_FILE_PATH

# Create directories for logs & uploads
RUN mkdir -p ${MARKET_PULSE_PATH} ${BACKEND_APP_LOG_PATH} ${BACKEND_APP_FILE_PATH} ${MARKET_PULSE_PATH}/resources/logs

RUN touch ${MARKET_PULSE_PATH}/resources/logs/log.txt

# Copy the fat JAR in
COPY target/recorder-backend-0.0.1-SNAPSHOT.jar ./recorder-backend.jar

# COPY python requirements.txt into the image
COPY market_pulse/requirements.txt ${MARKET_PULSE_PATH}/requirements.txt

# 1) Install Python 3.12 + venv support
RUN apt-get update && \
    apt-get install -y python3.12 python3.12-venv python3-pip && \
    rm -rf /var/lib/apt/lists/*

# 2) Create a Python virtualenv and upgrade pip
RUN python3.12 -m venv /opt/venv && \
    /opt/venv/bin/pip install --upgrade pip

# then during build, install them once:
RUN /opt/venv/bin/pip install --no-cache-dir -r ${MARKET_PULSE_PATH}/requirements.txt

## Create a non-root user for safety (optional)
#RUN groupadd -r appgroup && useradd -r -g appgroup appuser
#USER appuser

# Expose HTTP port
EXPOSE 8080

# Start your Spring Boot app, wiring in the paths
ENTRYPOINT ["java", "-jar", "recorder-backend.jar"]
