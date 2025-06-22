# Stock API Service

A standalone mock stock data provider service that generates realistic stock market data via REST API and WebSocket endpoints.

## Features

- **Mock Stock Data Generation**: Realistic stock prices with OHLC data, volume, and price changes
- **REST API Endpoints**: Get individual stock prices, multiple stocks, market summary, and historical data
- **WebSocket Streaming**: Real-time stock updates and market summaries
- **Popular Stocks**: Pre-configured with popular stocks (AAPL, GOOGL, MSFT, AMZN, TSLA, NVDA, META, NFLX, SPY, QQQ)
- **Docker Support**: Easy deployment with Docker
- **No External Dependencies**: No database or Kafka required - pure mock data generation

## Quick Start

### Option 1: Using the Build Script
```bash
./run_stock_api.sh
```

### Option 2: Manual Build and Run
```bash
# Build the JAR
cd ..
mvn clean package -pl stock-api-service -DskipTests

# Copy JAR to service directory
cp stock-api-service/target/stock-api-service-0.0.1-SNAPSHOT.jar stock-api-service/

# Build Docker image
cd stock-api-service
docker build -t stock-api-service:latest .

# Run the service
docker run -d --name stock-api -p 8081:8081 stock-api-service:latest
```

## API Endpoints

### REST API

- **GET** `/api/stocks/price/{symbol}` - Get current price for a specific stock
- **GET** `/api/stocks/prices?symbols=AAPL,GOOGL,MSFT` - Get prices for multiple stocks
- **GET** `/api/stocks/all` - Get prices for all supported stocks
- **GET** `/api/stocks/market-summary` - Get market summary statistics
- **GET** `/api/stocks/historical/{symbol}?days=30` - Get historical data for a stock

### WebSocket

- **Endpoint**: `ws://localhost:8081/ws`
- **Topics**:
  - `/topic/stock-updates` - Individual stock updates
  - `/topic/market-summary` - Market summary updates
  - `/topic/all-stocks` - All stock data updates

## Demo

Visit `http://localhost:8081/` to see a live demo of the WebSocket functionality.

## Example Usage

### Get AAPL Stock Price
```bash
curl http://localhost:8081/api/stocks/price/AAPL
```

### Get Multiple Stock Prices
```bash
curl "http://localhost:8081/api/stocks/prices?symbols=AAPL,GOOGL,MSFT"
```

### Get Market Summary
```bash
curl http://localhost:8081/api/stocks/market-summary
```

### Get Historical Data
```bash
curl "http://localhost:8081/api/stocks/historical/AAPL?days=7"
```

## Configuration

The service is configured via `application.yml`:

```yaml
server:
  port: 8081

app:
  stock:
    update-interval: 5000      # Stock update frequency (ms)
    broadcast-interval: 2000   # WebSocket broadcast frequency (ms)
    popular-stocks: AAPL,GOOGL,MSFT,AMZN,TSLA,NVDA,META,NFLX,SPY,QQQ
```

## Data Format

### Stock Data Response
```json
{
  "symbol": "AAPL",
  "price": 150.25,
  "change": 2.15,
  "changePercent": 1.4500,
  "open": 148.50,
  "high": 151.00,
  "low": 147.75,
  "previousClose": 148.10,
  "volume": 4567890,
  "timestamp": "2025-06-22 20:00:00"
}
```

### Market Summary Response
```json
{
  "timestamp": "2025-06-22T20:00:00",
  "totalStocks": 10,
  "totalMarketCap": 8500.50,
  "totalVolume": 50000000,
  "advancingStocks": 6,
  "decliningStocks": 3,
  "unchangedStocks": 1
}
```

## Development

### Prerequisites
- Java 17+
- Maven 3.6+
- Docker (optional)

### Local Development
```bash
# Run locally with Maven
mvn spring-boot:run -pl stock-api-service
```

### Building
```bash
# Build JAR
mvn clean package -pl stock-api-service -DskipTests

# Build Docker image
docker build -t stock-api-service:latest .
```

## Docker Commands

```bash
# Run the service
docker run -d --name stock-api -p 8081:8081 stock-api-service:latest

# Stop the service
docker stop stock-api && docker rm stock-api

# View logs
docker logs stock-api

# Health check
curl http://localhost:8081/api/stocks/market-summary
```

## Architecture

- **Spring Boot 3.3.3**: Main framework
- **Spring WebSocket**: Real-time data streaming
- **Mock Data Service**: Generates realistic stock data
- **Scheduled Broadcasting**: Automatic data updates
- **Docker**: Containerized deployment

## Notes

- This is a mock service - all data is generated randomly
- No real market data is used
- Perfect for development, testing, and demos
- No external dependencies or API keys required 