# Build & Verify Guide — Phase 1: Foundation

This guide provides instructions for setting up, building, and verifying the Phase 1 implementation of the Secure Retail Banking Platform.

## 1. Prerequisites

Ensure the following tools are installed:
- **Java 21**: Verify with `java -version`.
- **Maven 3.9+**: Verify with `mvn -version`.
- **Docker & Docker Compose**: Verify with `docker compose version`.

> [!NOTE]
> On macOS, if using Homebrew, ensure `JAVA_HOME` is set:
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)
> export PATH=$JAVA_HOME/bin:$PATH
> ```

## 2. Infrastructure Setup

Start the local development environment using Docker Compose:

```bash
cd backend/banking-platform
docker-compose up -d
```

This starts:
- **EventStoreDB**: Port 2113 (UI: http://localhost:2113)
- **Kafka / Zookeeper**: Port 9092
- **PostgreSQL**: Port 5432 (DB: `banking_read`, User: `banking`)
- **Redis**: Port 6379
- **Keycloak**: Port 8080

## 3. Building the Project

Run the following command from the `backend/banking-platform` directory:

```bash
mvn clean install -DskipTests
```

## 4. Verification Steps

### 4.1 Unit Tests
Verify the domain logic and state machine:
```bash
mvn test -pl account-domain
```

### 4.2 Application Startup
Start the `account-service`:
```bash
mvn spring-boot:run -pl account-service
```

### 4.3 Health Check
Verify the service is `UP` and connected to its dependencies:
```bash
curl -s http://localhost:8081/actuator/health | jq .
```
Expected response: `{"status": "UP", ...}`

### 4.4 Database Verification
Check if the read model tables are created:
```bash
docker exec postgres psql -U banking -d banking_read -c "\dt"
```
Expected tables: `account_read_model`, `outbox_messages`.

### 4.5 API Testing
With the `account-service` running, you can test the REST APIs. Note that security validation is currently bypassed for `/api/v1/accounts/**` to facilitate local testing.

**1. Open a New Account:**
```bash
curl -X POST http://localhost:8081/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-1",
    "type": "SAVINGS",
    "currency": "USD",
    "productCode": "SAV-001"
  }'
```
Expected response: `201 Created` with JSON containing the new `accountId` (e.g., `{"accountId":"..."}`).

**2. Get Account Details:**
*(Replace `{accountId}` with the ID returned from the previous step)*
```bash
curl -X GET http://localhost:8081/api/v1/accounts/{accountId}
```

**3. Freeze an Account:**
```bash
curl -X POST http://localhost:8081/api/v1/accounts/{accountId}/freeze \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Suspicious activity detected"
  }'
```
Expected response: `202 Accepted`

**4. Unfreeze an Account:**
```bash
curl -X POST http://localhost:8081/api/v1/accounts/{accountId}/unfreeze \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Identity verified"
  }'
```
Expected response: `202 Accepted`

**5. Close an Account:**
```bash
curl -X POST http://localhost:8081/api/v1/accounts/{accountId}/close \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Customer request"
  }'
```
Expected response: `202 Accepted`

**6. List Accounts for Customer:**
```bash
curl -X GET http://localhost:8081/api/v1/customers/cust-1/accounts
```

**7. Test Integration Tests:**
The repository includes integration tests utilizing Testcontainers and mocked repositories to ensure correct behavior:
```bash
mvn test -pl account-service -Dtest=AccountControllerIT
```

## 5. Troubleshooting & Common Issues

### 5.1 Port 5432 Conflict
If you see `FATAL: role "banking" does not exist` or connection failures, ensure no native PostgreSQL instance is running on your host:
```bash
# macOS
brew services stop postgresql@18
```

### 5.2 EventStoreDB API Changes
If you encounter compilation errors in `EventStoreDBAccountRepository`, ensure you are using `ExpectedRevision.noStream()` instead of `ExpectedRevision.NO_STREAM` (depending on the client version).

### 5.3 Spring Bean Discovery
If `OutboxRepository` or other JPA beans are not found, ensure `AccountServiceApplication` is configured to scan all required packages:
```java
@SpringBootApplication(scanBasePackages = "com.bank")
@EnableJpaRepositories(basePackages = "com.bank")
@EntityScan(basePackages = "com.bank")
```

### 5.4 Docker Volume Reset
If the database role `banking` is missing inside the container, reset the volumes:
```bash
docker-compose down -v
docker-compose up -d
```
