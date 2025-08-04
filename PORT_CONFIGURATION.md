# Port Configuration Summary

This document outlines all the ports used by the different services in the recorder system.

## Service Ports

### 1. **recorder-backend** - Port 8080
- **External Port**: 8080
- **Internal Port**: 8080
- **Configuration**: `server.port=8080` in `application.properties`
- **Docker**: `ports: ["8080:8080"]`
- **Purpose**: Main backend API for user management, records, files, and authentication
- **Endpoints**: 
  - `/api/auth/*` - Authentication endpoints
  - `/api/records/*` - Record management
  - `/api/labels/*` - Label management
  - `/api/files/*` - File management
  - `/api/processed-stock-data/*` - Processed stock data access

### 2. **stock-api-service** - Port 8081
- **External Port**: 8081
- **Internal Port**: 8081
- **Configuration**: `server.port: 8081` in `application.yml`
- **Docker**: `ports: ["8081:8081"]`
- **Purpose**: Real-time stock data generation and WebSocket streaming
- **Endpoints**:
  - `/api/stocks/*` - Stock data REST endpoints
  - `/ws` - WebSocket endpoint for real-time data
  - `/topic/*` - WebSocket topics for stock updates

### 3. **data-processor** - Port 8082
- **External Port**: 8082
- **Internal Port**: 8082
- **Configuration**: `server.port: 8082` in `application.yml`
- **Docker**: `ports: ["8082:8082"]`
- **Purpose**: Real-time stock data processing with Spark Streaming
- **Endpoints**:
  - `/api/data-processor/status` - Processing status
  - `/api/data-processor/start` - Start processing
  - `/api/data-processor/stop` - Stop processing
  - `/api/data-processor/processed-data/*` - Processed data access
  - `/actuator/health` - Health check

### 4. **MySQL Database** - Port 3306
- **External Port**: 3306
- **Internal Port**: 3306
- **Docker**: `ports: ["3306:3306"]`
- **Purpose**: Shared database for all services
- **Databases**:
  - `recorder` - Main application data
  - `stock_data` - Processed stock data

### 5. **Kafka** - Port 9092
- **External Port**: 9092
- **Internal Port**: 9092
- **Docker**: `ports: ["9092:9092"]`
- **Purpose**: Message streaming between services
- **Topics**:
  - `stock-data` - Raw stock data from stock-api-service
  - `processed-data` - Processed data from data-processor

## Internal Service Communication

### Service-to-Service URLs (Docker Network)

| Service | Target | URL | Purpose |
|---------|--------|-----|---------|
| recorder-backend | data-processor | `http://data-processor:8082` | Access processed stock data |
| data-processor | stock-api-service | `http://stock-api-service:8081` | Get raw stock data |
| data-processor | stock-api-service | `ws://stock-api-service:8081/ws` | WebSocket connection |
| data-processor | kafka | `kafka:9092` | Message streaming |
| data-processor | mysql | `mysql:3306` | Database access |
| recorder-backend | mysql | `mysql:3306` | Database access |

## Environment Variables

### data-processor Environment Variables
```yaml
STOCK_API_BASE_URL: http://stock-api-service:8081
STOCK_API_WEBSOCKET_URL: ws://stock-api-service:8081/ws
KAFKA_BOOTSTRAP_SERVERS: kafka:9092
SERVER_PORT: 8082
```

### recorder-backend Environment Variables
```properties
data-processor.base-url=http://data-processor:8082
server.port=8080
```

## Health Check Endpoints

| Service | Health Check URL | Method |
|---------|------------------|--------|
| stock-api-service | `http://localhost:8081/api/stocks/market-summary` | GET |
| data-processor | `http://localhost:8082/actuator/health` | GET |
| kafka | `kafka-topics.sh --bootstrap-server localhost:9092 --list` | CLI |
| mysql | `mysqladmin ping -h localhost -u root -p$$MYSQL_ROOT_PASSWORD` | CLI |

## External Access

### For Development/Local Access
- **Backend API**: `http://localhost:8080`
- **Stock API**: `http://localhost:8081`
- **Data Processor**: `http://localhost:8082`
- **Database**: `localhost:3306`
- **Kafka**: `localhost:9092`

### For Production/Docker
- **Backend API**: `http://your-server:8080`
- **Stock API**: `http://your-server:8081`
- **Data Processor**: `http://your-server:8082`
- **Database**: `your-server:3306`
- **Kafka**: `your-server:9092`

## Port Conflicts

If you encounter port conflicts, you can modify the external ports in `docker-compose.yml`:

```yaml
ports:
  - "8080:8080"  # Change 8080 to any available port
```

## Security Considerations

- **External Ports**: Only 8080, 8081, 8082, 3306, and 9092 are exposed externally
- **Internal Communication**: Services communicate via Docker network using internal hostnames
- **Database**: Consider restricting external database access in production
- **Kafka**: Consider restricting external Kafka access in production 