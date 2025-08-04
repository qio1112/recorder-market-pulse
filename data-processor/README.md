# Data Processor

A Spring Boot application that processes real-time stock data using Apache Spark Streaming. Consumes data from external sources (REST APIs, WebSockets), processes with moving averages, and stores results in MySQL. **Independent of any specific stock API service.**

## Quick Start

### 1. Environment Setup
Create `.env` file in project root:
```bash
# Database Configuration
DB_USER=recorder_user
DB_PASS=recorder_password
JWT_SECRET=your_jwt_secret_key_here_make_it_long_and_secure
```

### 2. Start Services
From the project root directory:
```bash
docker compose up -d
```

### 3. Verify
- Health: http://localhost:8082/actuator/health
- Status: http://localhost:8082/api/data-processor/status
- Data: http://localhost:8082/api/data-processor/processed-data

## Architecture

```
External Data Sources → Kafka → Spark Streaming → MySQL
(REST/WS APIs)        (Topic)   (Processing)    (Storage)
```

**Note**: The data-processor is designed to be independent of any specific stock API service. It uses its own local `StockData` model and can consume data from any external source that provides compatible JSON format.

## Data Model Independence

The data-processor module is completely independent of external services:

- **Local Models**: Uses its own `StockData` model in `com.yipeng.recorder.dataprocessor.model`
- **No External Dependencies**: No imports from `com.yipeng.stockapi` or other external service packages
- **Flexible Data Sources**: Can consume data from any REST API or WebSocket that provides compatible JSON format
- **JSON Compatibility**: Expects JSON with fields: `symbol`, `price`, `change`, `changePercent`, `open`, `high`, `low`, `previousClose`, `volume`, `timestamp`

### Expected JSON Format
```json
{
  "symbol": "AAPL",
  "price": 150.25,
  "change": 2.50,
  "changePercent": 1.69,
  "open": 148.00,
  "high": 151.00,
  "low": 147.50,
  "previousClose": 147.75,
  "volume": 1250000,
  "timestamp": "2024-01-15T10:30:00"
}
```

## Features

- **Real-time Processing**: 10-second batch intervals with 1-minute moving averages
- **Kafka Integration**: Reliable message streaming with automatic topic creation
- **Spark Streaming**: Scala-based processing with technical indicators
- **Flexible Storage**: Original data in columns, processed metrics in JSON
- **REST API**: Monitoring, control, and data access endpoints
- **Health Monitoring**: Spring Boot Actuator with comprehensive checks
- **Docker Integration**: Designed to run as part of the complete stack via docker-compose

## Technology Stack

- **Spring Boot 3.3.3** + **Java 17**
- **Apache Spark 3.5.2** + **Scala 2.12**
- **Apache Kafka 3.6.1**
- **MySQL 8.0**
- **Docker** + **Docker Compose**

## Project Structure

```
data-processor/
├── src/
│   ├── main/
│   │   ├── java/                    # Spring Boot components
│   │   │   ├── config/              # Spark configuration
│   │   │   ├── controller/          # REST endpoints
│   │   │   ├── model/               # JPA entities
│   │   │   ├── repository/          # Data access
│   │   │   └── service/             # Business logic
│   │   ├── scala/                   # Spark processing
│   │   │   ├── model/               # Case classes
│   │   │   └── spark/               # Streaming processor
│   │   └── resources/
│   │       └── application.yml      # Configuration
│   └── test/
├── Dockerfile
├── pom.xml
└── README.md
```

## Configuration

### Key Settings (`application.yml`)
```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/stock_data
    username: ${DB_USER}
    password: ${DB_PASS}

kafka:
  bootstrap-servers: kafka:9092
  topics:
    stock-data: stock-data
    processed-data: processed-data
  consumer:
    group-id: data-processor-group
    auto-offset-reset: earliest

# Note: Kafka topics are created automatically by the application on startup

# Stock API Configuration (External Data Sources)
stock-api:
  base-url: http://stock-api-service:8081  # External data source URL
  websocket-url: ws://stock-api-service:8081/ws/stocks  # External WebSocket URL
  symbols:
    - AAPL
    - GOOGL
    - MSFT
    - AMZN
    - TSLA

spark:
  streaming:
    batch-duration: 10000 # 10 seconds

processing:
  moving-average:
    window-size: 6 # 6 batches = 1 minute
```

## API Endpoints

### Control
- `GET /api/data-processor/status` - Pipeline status
- `POST /api/data-processor/start` - Start processing
- `POST /api/data-processor/stop` - Stop processing

### Data Access
- `GET /api/data-processor/processed-data` - All processed data
- `GET /api/data-processor/processed-data/{symbol}` - Data by symbol
- `GET /api/data-processor/processed-data/{symbol}/metrics` - Parsed JSON metrics
- `GET /api/data-processor/processed-data/{symbol}/latest` - Latest data for symbol
- `GET /api/data-processor/processed-data/{symbol}/latest/{limit}` - Latest N records for symbol
- `GET /api/data-processor/processed-data/{symbol}/count` - Data count since timestamp
- `GET /api/data-processor/processed-data/high-change` - Significant price changes
- `GET /api/data-processor/processed-data/high-volume` - High volume activity
- `GET /api/data-processor/active-symbols` - Symbols with recent activity
- `GET /api/data-processor/stats` - Processing statistics

### Health
- `GET /actuator/health` - Application health
- `GET /actuator/metrics` - Application metrics

## Database Schema

### Table: `processed_stock_data`
```sql
CREATE TABLE processed_stock_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Original StockData columns
    symbol VARCHAR(10) NOT NULL,
    price DECIMAL(10,2),
    change DECIMAL(10,2),
    change_percent DECIMAL(5,2),
    open DECIMAL(10,2),
    high DECIMAL(10,2),
    low DECIMAL(10,2),
    previous_close DECIMAL(10,2),
    volume BIGINT,
    timestamp DATETIME NOT NULL,
    
    -- Processed metrics (JSON)
    processed_metrics TEXT,
    
    -- Metadata
    data_points_count INT,
    
    -- Performance Indexes
    INDEX idx_symbol (symbol),
    INDEX idx_symbol_timestamp (symbol, timestamp DESC)
);
```

### Database Indexes for Performance
- **`idx_symbol`**: Optimizes queries filtering by stock symbol only
- **`idx_symbol_timestamp`**: Optimizes range queries by symbol and time (most common pattern)

## Query Performance Optimization

### Optimized Query Patterns
The database indexes are designed to optimize the most common query patterns:

1. **Symbol-based queries** (most common):
   - `GET /api/data-processor/processed-data/{symbol}` - Uses `idx_symbol_timestamp`
   - `GET /api/data-processor/processed-data/{symbol}/latest` - Uses `idx_symbol_timestamp`

2. **Time-range queries by symbol**:
   - `GET /api/data-processor/processed-data/range?symbol=AAPL` - Uses `idx_symbol_timestamp`
   - `GET /api/data-processor/processed-data/{symbol}/count` - Uses `idx_symbol_timestamp`

3. **Analytics queries**:
   - `GET /api/data-processor/processed-data/high-change?symbol=AAPL` - Uses `idx_symbol_timestamp`
   - `GET /api/data-processor/processed-data/high-volume?symbol=AAPL` - Uses `idx_symbol_timestamp`

### Performance Benefits
- **Symbol queries**: O(log n) instead of O(n) for symbol filtering
- **Range queries**: Efficient range scans with timestamp ordering within symbol
- **Composite queries**: Single index scan for symbol + time combinations
- **Pagination**: Fast LIMIT operations with proper ordering

### Index Strategy Rationale
- **No timestamp-only index**: Users typically don't query across all symbols by time alone
- **Composite index priority**: Most queries filter by symbol first, then use timestamp for ordering/range
- **Minimal overhead**: Only essential indexes to balance performance and storage

## Processed Metrics (JSON)
```json
{
  "movingAverage1Min": 150.25,
  "volumeAverage": 1250000,
  "priceRange": 2.50,
  "volatility": 0.85,
  "trendDirection": "UP",
  "momentum": 1.25,
  "processingTimestamp": "2024-01-15T10:30:00",
  "windowSize": 6,
  "dataPointsUsed": 6,
  "customMetrics": {}
}
```

## Data Flow

1. **Ingestion**: `StockDataConsumerService` fetches data every 10s via REST API
2. **Streaming**: Data sent to Kafka `stock-data` topic
3. **Processing**: `SparkStreamingProcessor` (Scala) calculates metrics
4. **Storage**: Original data in columns, processed metrics as JSON
5. **Access**: REST API provides monitoring and data retrieval

## Monitoring

### Health Checks
- Database connectivity
- Kafka connectivity  
- Application status
- Processing pipeline status

### Key Metrics
- Records processed per second
- Error rates
- Memory usage
- Processing latency

### Logs
- Data processing start/stop
- Kafka production/consumption
- Database operations
- Error conditions

## Troubleshooting

### Common Issues

1. **Port Conflicts**
   ```bash
   lsof -i :8082  # data-processor
   lsof -i :9092  # kafka
   lsof -i :3306  # mysql
   ```

2. **Service Health**
   ```bash
   # From project root
   docker compose ps
   docker compose logs data-processor
   ```

3. **Environment**
   ```bash
   # Ensure .env exists in project root
   ls -la .env
   ```

4. **Reset Everything**
   ```bash
   # From project root
   docker compose down -v
   docker compose up -d
   ```

### Debug Mode
```yaml
logging:
  level:
    com.yipeng.recorder.dataprocessor: DEBUG
```

## Performance Tuning

### Spark Settings
```yaml
spark:
  streaming:
    batch-duration: 10000    # Processing interval
  master: local[*]           # Cluster mode
```

### Kafka Settings
```yaml
kafka:
  consumer:
    group-id: data-processor-group
    auto-offset-reset: earliest
```

### Processing Settings
```yaml
processing:
  moving-average:
    window-size: 6           # Window size for calculations
```

## Development

### Docker Development (Recommended)
```bash
# From project root
docker compose up -d
docker compose logs -f data-processor

# Rebuild and restart
docker compose build data-processor
docker compose up -d data-processor
```

### Local Development (Advanced)
```bash
# Prerequisites: Java 17, Maven, MySQL, Kafka
# Note: Requires running Kafka and MySQL separately
mvn clean package
java -jar target/data-processor-0.0.1-SNAPSHOT.jar
```

## Security Notes

- Change default passwords in `.env`
- Use strong JWT secret for production
- Configure proper email settings
- Restrict network access in production

## Contributing

1. Use Scala for Spark-related code
2. Use Java for Spring Boot components
3. Follow existing code structure
4. Add appropriate tests
5. Update documentation 