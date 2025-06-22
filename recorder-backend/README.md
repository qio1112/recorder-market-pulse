# Recorder Backend

A comprehensive Spring Boot application that provides a RESTful API for managing records, files, and user authentication with integrated stock market data processing capabilities.

## Overview

The Recorder Backend is a multi-module application that combines:
- **Record Management System**: Create, update, delete, and search records with file attachments
- **User Authentication & Authorization**: JWT-based authentication with role-based access control
- **File Management**: Upload and manage images and documents with size limits
- **Stock Market Data Processing**: Python-based market data collection and analysis
- **Email Notifications**: Automated email alerts and notifications
- **Scheduled Tasks**: Automated stock data updates and alerts

## Features

### Core Features
- **Record Management**: Create, read, update, delete records with rich content
- **File Upload**: Support for images and documents with configurable size limits
- **Labeling System**: Tag records with custom labels for organization
- **Public/Private Records**: Control visibility of records
- **Search & Filtering**: Advanced record search with pagination
- **Alert System**: One-time and recurring alerts with email notifications

### Authentication & Security
- **JWT Authentication**: Secure token-based authentication
- **Role-Based Access Control**: User and Admin roles
- **Password Encryption**: BCrypt password hashing
- **CORS Configuration**: Cross-origin resource sharing support

### Stock Market Integration
- **Automated Data Collection**: Scheduled stock and option data updates
- **Flexible Symbol Management**: Support for custom symbol lists
- **Parallel Processing**: Multi-threaded data collection
- **Market Date Handling**: Intelligent trading day detection

### Technical Features
- **RESTful API**: Standard HTTP endpoints
- **Database Integration**: MySQL with JPA/Hibernate
- **File Storage**: Local file system with organized structure
- **Logging**: Comprehensive application logging
- **Docker Support**: Containerized deployment
- **Email Integration**: SMTP-based email notifications

## Architecture

### Technology Stack
- **Backend**: Spring Boot 3.x, Java 17
- **Database**: MySQL 8.x
- **Security**: Spring Security, JWT
- **Data Processing**: Python 3.12 with pandas, yfinance
- **Containerization**: Docker
- **Build Tool**: Maven

### Project Structure
```
recorder-backend/
├── src/main/java/com/yipeng/recorder/
│   ├── config/           # Configuration classes
│   ├── controller/       # REST API controllers
│   ├── exception/        # Custom exception handlers
│   ├── model/           # JPA entities
│   ├── repository/      # Data access layer
│   ├── request/         # DTOs for API requests
│   ├── service/         # Business logic layer
│   └── utils/           # Utility classes
├── market_pulse/        # Python stock data processing
│   ├── main/           # Python modules
│   └── requirements.txt # Python dependencies
├── src/main/resources/  # Configuration files
└── Dockerfile          # Container configuration
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- Docker (for containerized deployment)
- Python 3.12 (for stock data processing)

## Environment Configuration

Create a `.env.prod` file in the parent directory with the following variables:

```bash
# Database Configuration
DB_HOST=jdbc:mysql://localhost:3306/recorder_db
DB_USER=your_db_user
DB_PASS=your_db_password

# Email Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USER=your_email@gmail.com
MAIL_PASS=your_email_password
MAIL_PROTOCOL=smtp
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true

# Application Paths
MARKET_PULSE_PATH=/app/market_pulse
BACKEND_APP_LOG_PATH=/app/logs
BACKEND_APP_FILE_PATH=/app/uploaded_files
MARKET_PULSE_PATH_SERVER=/path/on/host/market_pulse
BACKEND_APP_LOG_PATH_SERVER=/path/on/host/logs
BACKEND_APP_FILE_PATH_SERVER=/path/on/host/uploaded_files

# Admin Configuration
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin_password
ADMIN_EMAIL=admin@example.com

# JWT Configuration
JWT_SECRET=your_jwt_secret_key
```

## Quick Start

### Using Docker (Recommended)

1. **Build and run using the provided script:**
   ```bash
   cd recorder-backend
   chmod +x run_recorder_backend.sh
   ./run_recorder_backend.sh
   ```

2. **Manual Docker commands:**
   ```bash
   # Build the application
   mvn clean package -DskipTests
   
   # Build Docker image
   docker build -t recorder-backend:latest .
   
   # Run container
   docker run -d \
     --name recorder-backend \
     --restart unless-stopped \
     --env-file ../.env.prod \
     -p 8080:8080 \
     recorder-backend:latest
   ```

### Local Development

1. **Set up the database:**
   ```sql
   CREATE DATABASE recorder_db;
   CREATE USER 'recorder_user'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON recorder_db.* TO 'recorder_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

3. **Access the API:**
   - Base URL: `http://localhost:8080`
   - API Documentation: Available at `/api/*` endpoints

## API Endpoints

### Authentication
- `POST /api/auth/authenticate` - User login
- `POST /api/auth/signup` - User registration

### Records
- `POST /api/records/create-record` - Create new record
- `GET /api/records/record/{id}` - Get record by ID
- `POST /api/records/update-record` - Update existing record
- `GET /api/records/delete-record/{id}` - Delete record
- `POST /api/records/list-records` - List records with filtering

### Labels
- `GET /api/labels` - Get all labels
- `POST /api/labels` - Create new label

### Files
- `GET /api/files/{id}` - Download file
- `DELETE /api/files/{id}` - Delete file

### Scripts
- `POST /api/scripts/run` - Execute Python scripts

## Stock Data Processing

The application includes a Python module for stock market data processing:

### Features
- Automated stock and option data collection
- Support for custom symbol lists
- Parallel processing with configurable workers
- Market date-aware updates

### Usage
```bash
# Update stock data for specific symbols
python -m main.main --jobName update_stock_data --symbols AAPL,GOOGL,MSFT

# Update with custom symbol file
python -m main.main --jobName update_stock_data --symbolPath /path/to/symbols.txt

# Flexible data update
python -m main.main --jobName update_stock_data_flexible --maxWorkers 4
```

## Configuration

### Application Properties
Key configuration options in `application.properties`:

- `spring.jpa.hibernate.ddl-auto=update` - Database schema management
- `recfile.upload.dir` - File upload directory
- `recfile.image.file.size=5` - Image file size limit (MB)
- `recfile.regular.file.size=5` - Document file size limit (MB)
- `logging.file.name` - Application log file location

### File Upload Limits
- Maximum file size: 5MB per file
- Maximum request size: 10MB
- Supported file types: Images and documents

## Monitoring & Logs

### Application Logs
- Location: Configured via `APP_LOG_PATH` environment variable
- Default: `logs/application.log`
- Log levels: Configurable per package

### Docker Logs
```bash
# View application logs
docker logs -f recorder-backend

# View specific log files
docker exec recorder-backend cat /app/logs/application.log
```

## Security Considerations

- JWT tokens for authentication
- Password encryption using BCrypt
- Role-based access control
- File upload validation
- CORS configuration for web clients

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Verify MySQL is running
   - Check database credentials in `.env.prod`
   - Ensure database exists

2. **File Upload Errors**
   - Check directory permissions
   - Verify file size limits
   - Ensure sufficient disk space

3. **Python Module Errors**
   - Verify Python 3.12 installation
   - Check `requirements.txt` dependencies
   - Ensure virtual environment is activated

### Debug Mode
Enable debug logging by setting:
```properties
logging.level.com.yipeng.recorder=DEBUG
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is proprietary software. All rights reserved.

## Support

For support and questions, please contact the development team or create an issue in the project repository. 