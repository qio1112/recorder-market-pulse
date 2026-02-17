# Use an official Python runtime as a parent image
FROM python:3.12-slim

ARG APP_TIMEZONE

ENV TZ=${APP_TIMEZONE}

# Prevent Python from writing pyc files and buffer logs
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app/market_pulse

# Install system dependencies (wheels cover most, but keep build tools minimal)
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies first to leverage Docker layer caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application code
COPY . .

# Declare resources as a volume so data/logs can be persisted or mounted
VOLUME ["/app/market_pulse/resources"]

EXPOSE 8000

# Run the FastAPI app with uvicorn
CMD ["uvicorn", "main.api.app:app", "--host", "0.0.0.0", "--port", "8000"]
