#!/bin/bash

# Stock API Service Build and Run Script

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
BUILD=true
RUN=false
CONTAINER_NAME="stock-api"
PORT="8081"

# Function to show usage
show_usage() {
    echo -e "${BLUE}Stock API Service Build and Run Script${NC}"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --no-build    Skip building JAR and Docker image"
    echo "  --run         Build and run the container"
    echo "  --name NAME   Container name (default: stock-api)"
    echo "  --port PORT   Port mapping (default: 8081)"
    echo "  --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Build only"
    echo "  $0 --run              # Build and run"
    echo "  $0 --no-build --run   # Run existing image"
    echo "  $0 --run --name my-api --port 8082"
    echo ""
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}❌ Docker is not running. Please start Docker and try again.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Docker is running${NC}"
}

# Function to stop existing container
stop_container() {
    if docker ps -a --format "table {{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "${YELLOW}🛑 Stopping existing container: ${CONTAINER_NAME}${NC}"
        docker stop ${CONTAINER_NAME} > /dev/null 2>&1
        docker rm ${CONTAINER_NAME} > /dev/null 2>&1
        echo -e "${GREEN}✅ Container stopped and removed${NC}"
    fi
}

# Function to build the service
build_service() {
    echo -e "${BLUE}🔨 Building stock-api-service JAR...${NC}"
    cd ..
    mvn clean package -pl stock-api-service -DskipTests

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ JAR built successfully!${NC}"
        
        echo -e "${BLUE}📁 Copying JAR to stock-api-service directory...${NC}"
        cp stock-api-service/target/stock-api-service-0.0.1-SNAPSHOT.jar stock-api-service/
        
        echo -e "${BLUE}🐳 Building Docker image...${NC}"
        cd stock-api-service
        docker build -t stock-api-service:latest .
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Docker image built successfully!${NC}"
            return 0
        else
            echo -e "${RED}❌ Failed to build Docker image!${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ Failed to build JAR!${NC}"
        return 1
    fi
}

# Function to run the service
run_service() {
    echo -e "${BLUE}🚀 Starting stock-api-service container...${NC}"
    echo -e "${YELLOW}📊 Container name: ${CONTAINER_NAME}${NC}"
    echo -e "${YELLOW}🌐 Port mapping: ${PORT}:8081${NC}"
    
    docker run -d --name ${CONTAINER_NAME} -p ${PORT}:8081 stock-api-service:latest
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Container started successfully!${NC}"
        echo ""
        echo -e "${BLUE}📋 Service Information:${NC}"
        echo -e "  🌐 Demo page: http://localhost:${PORT}/"
        echo -e "  📡 REST API: http://localhost:${PORT}/api/stocks/price/AAPL"
        echo -e "  🔌 WebSocket: ws://localhost:${PORT}/ws"
        echo ""
        echo -e "${BLUE}📋 Available endpoints:${NC}"
        echo -e "  - GET http://localhost:${PORT}/api/stocks/price/{symbol}"
        echo -e "  - GET http://localhost:${PORT}/api/stocks/prices?symbols=AAPL,GOOGL,MSFT"
        echo -e "  - GET http://localhost:${PORT}/api/stocks/all"
        echo -e "  - GET http://localhost:${PORT}/api/stocks/market-summary"
        echo -e "  - GET http://localhost:${PORT}/api/stocks/historical/{symbol}?days=30"
        echo ""
        echo -e "${BLUE}🔧 Management commands:${NC}"
        echo -e "  📋 View logs: docker logs ${CONTAINER_NAME}"
        echo -e "  🛑 Stop service: docker stop ${CONTAINER_NAME} && docker rm ${CONTAINER_NAME}"
        echo -e "  🔄 Restart: docker restart ${CONTAINER_NAME}"
        echo ""
        echo -e "${GREEN}🎉 Service is ready! Visit http://localhost:${PORT}/ to see the demo.${NC}"
    else
        echo -e "${RED}❌ Failed to start container!${NC}"
        return 1
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --no-build)
            BUILD=false
            shift
            ;;
        --run)
            RUN=true
            shift
            ;;
        --name)
            CONTAINER_NAME="$2"
            shift 2
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --help|-h)
            show_usage
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Unknown option: $1${NC}"
            show_usage
            exit 1
            ;;
    esac
done

# Check Docker first
check_docker

# Build if requested
if [ "$BUILD" = true ]; then
    if ! build_service; then
        exit 1
    fi
else
    echo -e "${YELLOW}⚠️  Skipping build (--no-build specified)${NC}"
fi

# Run if requested
if [ "$RUN" = true ]; then
    stop_container
    if ! run_service; then
        exit 1
    fi
else
    echo ""
    echo -e "${BLUE}📋 To run the service:${NC}"
    echo -e "  $0 --run"
    echo -e "  docker run -d --name ${CONTAINER_NAME} -p ${PORT}:8081 stock-api-service:latest"
    echo ""
    echo -e "${BLUE}📋 To stop the service:${NC}"
    echo -e "  docker stop ${CONTAINER_NAME} && docker rm ${CONTAINER_NAME}"
fi 