FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the pre-built JAR file
COPY target/data-processor-*.jar app.jar

# Expose port
EXPOSE 8082

# Run the application
CMD ["java", "-jar", "app.jar"] 