# 24Online MDM (Mobile Device Management)

A comprehensive Mobile Device Management solution built with Spring Boot and Reactor-based reactive architecture.

## 🚀 Features

- **Device Enrollment & Management** - Secure device onboarding with setup keys
- **Trust Score Policies** - Dynamic trust scoring based on device posture
- **Policy Center** - Configurable rules for system information, remediation, and trust decisions
- **Posture Evaluation** - Automated device compliance checking
- **Application Catalog** - Manage allowed/blocked applications
- **OS Lifecycle Tracking** - Monitor operating system versions and end-of-life dates
- **Audit Trail** - Complete audit logging for policy changes and device events
- **Multi-Tenant Support** - Isolated tenant data with API key authentication
- **RESTful API** - Versioned API endpoints (v1, v2)
- **Responsive UI** - Server-rendered Thymeleaf templates with modern CSS

## 🛠️ Technology Stack

| Component | Technology |
|-----------|------------|
| Backend | Spring Boot 4.x (WebFlux) |
| Database | PostgreSQL |
| Security | JWT Authentication, SHA-512 Password Hashing |
| Messaging | RabbitMQ (Audit & Posture events) |
| Frontend | Thymeleaf + Vanilla JS + CSS |
| Build | Maven |
| Monitoring | Actuator, Prometheus, Zipkin |
| Reporting | Apache Superset (embedded) |

## 📋 Prerequisites

- Java 21+
- PostgreSQL 14+
- Node.js (for package management)
- Docker & Docker Compose (optional, for infrastructure)
- RabbitMQ (optional, for async messaging)

## ⚙️ Configuration

### Database Setup

1. Create a PostgreSQL database:
```sql
CREATE DATABASE mdm;
```

2. Update `src/main/resources/application.yaml` with your database credentials:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mdm
    username: your_username
    password: your_password
```

### RabbitMQ (Optional)

Configure in `application.yaml`:
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

## 🏃 Running the Application

### Using Maven
```bash
./mvnw spring-boot:run
```

### Using Java
```bash
./mvnw clean package
java -jar target/*.jar
```

### Using Docker Compose
```bash
docker-compose up -d
```

## 📁 Project Structure

```
24onlinemdm/
├── src/main/java/com/e24online/mdm/
│   ├── config/          # Security, RabbitMQ, WebClient configs
│   ├── domain/          # JPA entities
│   ├── records/         # Java records for DTOs
│   ├── repository/      # Data access layer
│   ├── service/         # Business logic
│   ├── web/             # REST controllers & DTOs
│   └── OnlineMdmApplication.java
├── src/main/resources/
│   ├── db/migration/    # Flyway migrations
│   ├── static/          # CSS, JS, assets
│   ├── templates/       # Thymeleaf views
│   └── application.yaml
├── sql/                 # Standalone SQL scripts
├── docs/                # Documentation
└── ops/                 # Operational scripts & configs
```

## 🔐 Default Credentials

After first run, the default admin account is created:
- **Username:** `admin`
- **Password:** `admin`

⚠️ **Change this immediately in production!**

## 📖 API Documentation

OpenAPI/Swagger documentation is available at:
- `/v3/api-docs` - OpenAPI JSON
- `/swagger-ui.html` - Swagger UI (if enabled)

See `docs/openapi/` for exported API specifications.

## 📊 Monitoring & Observability

| Tool | Endpoint/Port |
|------|---------------|
| Actuator | `/actuator` (8080) |
| Prometheus | `/actuator/prometheus` |
| Zipkin | Distributed tracing |
| Superset | Embedded reporting (configurable) |

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

## 📝 License

Proprietary - 24online Info Technologies Pvt. Ltd. All rights reserved.

## 👥 Support

For issues and questions, contact the development team.
