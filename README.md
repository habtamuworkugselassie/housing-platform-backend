# Ethio Build Connect — Backend

Real estate and construction ecosystem — backend service.

## Overview

This backend powers **Ethio Build Connect**, connecting real estate companies, buyers, financing partners, and construction suppliers in one marketplace.

## Architecture

The backend is implemented as a **modular monolith** using Spring Boot, following Domain-Driven Design (DDD) principles. The architecture is designed to be easily extractable into microservices when needed.

### Domain Modules

- **identity**: User and organization management
- **property**: Property listings and verification
- **banking**: Credit products and financing offers
- **loan**: Loan applications and processing
- **construction**: Materials and Bill of Quantities (BoQ)
- **payment**: Payment processing and disbursements
- **notification**: User notifications
- **audit**: Audit logging and compliance
- **shared**: Common utilities and configurations

## Technology Stack

- **Java**: 21
- **Framework**: Spring Boot 3.2.0
- **Database**: PostgreSQL
- **Caching**: Redis
- **Messaging**: Apache Kafka
- **API Documentation**: OpenAPI 3 (Swagger)
- **Build Tool**: Maven
- **Migration**: Flyway

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker and Docker Compose (for local development)
- PostgreSQL 16+ (or use Docker Compose)
- Redis (or use Docker Compose)
- Kafka (or use Docker Compose)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd housing-platform
```

### 2. Start Infrastructure Services

Start PostgreSQL, Redis, and Kafka using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- PostgreSQL on port 5432
- Redis on port 6379
- Kafka on port 9092
- Zookeeper on port 2181

### 3. Configure Application

The application uses environment variables for configuration. Set the following:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/housing_platform
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
export REDIS_HOST=localhost
export REDIS_PORT=6379
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export OAUTH2_ISSUER_URI=http://localhost:8080/auth/realms/housing-platform
```

Or update `application.yml` directly.

### 4. Database Migration

Flyway will automatically run migrations on startup. The initial schema is created from:
- `src/main/resources/db/migration/V1__Initial_schema.sql`

### 5. Run the Application

Using Maven:

```bash
./mvnw spring-boot:run
```

Or build and run:

```bash
./mvnw clean package
java -jar target/housing-platform-1.0.0-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

### 6. Access API Documentation

Once the application is running, access Swagger UI at:

```
http://localhost:8080/swagger-ui.html
```

API documentation (JSON) is available at:

```
http://localhost:8080/api-docs
```

### 7. OAuth2 Authentication Setup

For OAuth2 authentication, see [OAUTH2_SETUP.md](./OAUTH2_SETUP.md) for detailed instructions.

**Quick Setup (Keycloak):**

```bash
# Start Keycloak
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev

# Access admin console: http://localhost:8080
# Create realm: housing-platform
# Create client: housing-platform
# Create scopes: buyer, banker, realtor, supplier, admin
```

## Project Structure

```
src/main/java/com/housingplatform/
├── identity/          # Identity & Access Management
├── property/          # Property Management
├── banking/           # Banking & Credit Services
├── loan/              # Loan Application Processing
├── construction/      # Construction & Materials
├── payment/           # Payment Processing
├── notification/      # Notification Service
├── audit/             # Audit & Compliance
└── shared/            # Shared Utilities & Configurations
```

## Development

### Running Tests

```bash
./mvnw test
```

### Building for Production

```bash
./mvnw clean package -DskipTests
```

### Docker Build

```bash
docker build -t housing-platform:latest .
```

## Environment Variables

Key environment variables:

- `DATABASE_URL`: PostgreSQL connection URL
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password
- `FRONTEND_BASE_URL`: Public URL of the frontend (e.g. `https://your-app.com`). Used for password-reset email links. Set as GitHub secret `FRONTEND_BASE_URL` for deploy workflow.
- `REDIS_HOST`: Redis host
- `REDIS_PORT`: Redis port
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka bootstrap servers
- `SERVER_PORT`: Application server port (default: 8080)

## API Versioning

All APIs are versioned under `/api/v1/` prefix.

## Security

- **OAuth2 Resource Server** with JWT token validation
- **Scope-based access control** - Each portal has its own scope (buyer, banker, realtor, supplier, admin)
- **Role-based access control (RBAC)** - Additional role-based permissions
- **Method-level security** - `@PreAuthorize` annotations on controllers
- **Portal isolation** - Users from one portal cannot access APIs from another portal
- **CORS configuration** - Configured for cross-origin requests

### Portal Access Control

- **Buyer Portal** (`SCOPE_buyer`): Can view properties, create loan applications, view notifications
- **Banker Portal** (`SCOPE_banker`): Can manage credit products, financing offers, approve/reject loans, process payments
- **Real Estate Portal** (`SCOPE_realtor`): Can manage properties, view BoQs
- **Supplier Portal** (`SCOPE_supplier`): Can manage materials, view BoQs
- **Admin Portal** (`SCOPE_admin`): Full access to all APIs

See [OAUTH2_SETUP.md](./OAUTH2_SETUP.md) for detailed OAuth2 configuration.

## Database Migrations

Database migrations are managed using Flyway. Migration scripts are located in:

```
src/main/resources/db/migration/
```

## Contributing

1. Create a feature branch
2. Make your changes
3. Write/update tests
4. Submit a pull request

## License

Proprietary - All rights reserved
