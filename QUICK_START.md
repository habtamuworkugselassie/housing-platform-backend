# Quick Start Guide

This guide will help you get the Housing Platform Backend up and running quickly.

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker and Docker Compose
- 8GB+ RAM (for Docker containers)

## Step-by-Step Setup

### 1. Start Infrastructure

```bash
# Start PostgreSQL, Redis, Kafka, and Zookeeper
docker-compose up -d

# Verify containers are running
docker-compose ps
```

### 2. Wait for Services to be Ready

```bash
# Check PostgreSQL is ready
docker-compose exec postgres pg_isready -U postgres

# Check Redis is ready
docker-compose exec redis redis-cli ping
```

### 3. Run Database Migrations

Migrations run automatically on startup. To verify:

```bash
# Check Flyway migration status (after app starts)
docker-compose exec postgres psql -U postgres -d housing_platform -c "SELECT * FROM flyway_schema_history;"
```

### 4. Start the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or build first
./mvnw clean package
java -jar target/housing-platform-1.0.0-SNAPSHOT.jar
```

### 5. Verify Application is Running

```bash
# Health check
curl http://localhost:8080/actuator/health

# Should return:
# {"status":"UP","timestamp":"...","service":"housing-platform"}
```

### 6. Access Swagger UI

Open in browser: http://localhost:8080/swagger-ui.html

## OAuth2 Setup (Optional for Development)

For development without OAuth2, you can temporarily disable security or use a mock token.

### Option 1: Disable Security (Development Only)

Add to `application-dev.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/auth/realms/housing-platform
```

### Option 2: Use Keycloak (Recommended)

```bash
# Start Keycloak
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev

# Access: http://localhost:8080
# Username: admin
# Password: admin
```

Then follow [OAUTH2_SETUP.md](./OAUTH2_SETUP.md) for configuration.

## Testing the API

### Without Authentication (Public Endpoints)

```bash
# Health check
curl http://localhost:8080/actuator/health

# Swagger docs
curl http://localhost:8080/api-docs
```

### With Authentication

```bash
# Get token from OAuth2 server
TOKEN=$(curl -X POST http://localhost:8080/auth/realms/housing-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=housing-platform" \
  -d "username=user@example.com" \
  -d "password=password" \
  -d "scope=buyer" | jq -r '.access_token')

# Use token in API calls
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/properties
```

## Troubleshooting

### Port Already in Use

```bash
# Check what's using port 8080
lsof -i :8080

# Kill the process or change port in application.yml
server:
  port: 8081
```

### Database Connection Issues

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Check connection
docker-compose exec postgres psql -U postgres -d housing_platform -c "SELECT 1;"
```

### Migration Issues

```bash
# Check migration status
docker-compose exec postgres psql -U postgres -d housing_platform \
  -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# If needed, repair Flyway
# (This should be done carefully in production)
```

### Kafka Connection Issues

```bash
# Check Kafka is running
docker-compose ps kafka

# Check Kafka logs
docker-compose logs kafka
```

## Next Steps

1. **Configure OAuth2** - See [OAUTH2_SETUP.md](./OAUTH2_SETUP.md)
2. **Set up frontend** - Connect your frontend applications
3. **Configure email** - Set up SMTP for notifications
4. **Set up monitoring** - Add logging and metrics
5. **Deploy to production** - Follow deployment guidelines

## Development Tips

- Use `application-dev.yml` for development-specific settings
- Enable SQL logging: `spring.jpa.show-sql: true`
- Use Swagger UI for API testing
- Check logs: `tail -f logs/application.log`

## Common Commands

```bash
# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v

# View logs
docker-compose logs -f

# Restart a service
docker-compose restart postgres

# Build application
./mvnw clean package

# Run tests
./mvnw test
```
