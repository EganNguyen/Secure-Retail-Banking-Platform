# Build & Verify Guide — Phase 2

This guide provides instructions for setting up, building, and verifying the Phase 1 and 2 implementation of the Secure Retail Banking Platform.

## Roadmap & Features Implemented

### Phase 1 — Foundation (Weeks 1–4)
- [x] Repository and module structure setup (multi-module Maven)
- [x] EventStoreDB + Kafka + PostgreSQL + Redis local dev environment (Docker Compose)
- [x] `AggregateRoot` base class + `EventSourcedRepository` interface
- [x] `AccountAggregate` — full state machine + events + unit tests
- [x] Account Service: command side (open, freeze, unfreeze, close)
- [x] Account Service: query side (projector + read model)
- [x] Outbox pattern implementation + Kafka publisher
- [x] Keycloak integration + Spring Security setup

### Phase 2 — Transfer Core (Weeks 5–8)
- [x] `TransferAggregate` + saga state machine
- [x] Transfer validation (limits, account status, AML name check)
- [x] `TransferSagaOrchestrator` — internal transfer flow
- [x] `LedgerService` — double-entry bookkeeping
- [x] Balance projection + Redis cache
- [x] Idempotency middleware
- [x] Optimistic concurrency handling + retry logic
- [x] Transfer API endpoints + WebSocket notifications

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

> On Windows,
> Download Maven https://maven.apache.org/download.cgi
> Binary zip archive (apache-maven-3.x.x-bin.zip)
> Unzip to a clean location, for example: C:\Program Files\Apache\Maven
> Set Environment Variables
> Edit the system environment variables
> New System Variable: `MAVEN_HOME = C:\Program Files\Apache\Maven`
> Edit Path: Add `C:\Program Files\Apache\Maven\bin`

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
Verify the domain logic and state machine for both accounts and transfers:
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
Expected tables: `account_read_model`, `outbox_messages`, `transfer_read_model`, `ledger_entry`, `balance_projection`, `idempotency_record`.

### 4.5 API Testing
With the `account-service` running, you can test the REST APIs. Note that security validation is currently bypassed for `/api/v1/**` to facilitate local testing.

**1. Open a New Account (Source):**
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

**2. Open a New Account (Destination):**
```bash
curl -X POST http://localhost:8081/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-2",
    "type": "CHECKING",
    "currency": "USD",
    "productCode": "CHK-001"
  }'
```

**3. Initiate an Internal Transfer:**
*(Replace the account IDs below and specify an `X-Idempotency-Key`)*
```bash
curl -X POST http://localhost:8081/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: transfer-req-001" \
  -d '{
    "sourceAccountId": "{sourceAccountId}",
    "destinationAccountId": "{destinationAccountId}",
    "beneficiaryName": "Bob Doe",
    "amount": 25.0000,
    "currency": "USD",
    "reference": "Dinner bill split"
  }'
```
Expected response: `202 Accepted` with JSON containing `{ "transferId": "..." }`.

**4. Check Transfer Status:**
```bash
curl -X GET http://localhost:8081/api/v1/transfers/{transferId}
```

**5. Get Account Balances:**
```bash
curl -X GET http://localhost:8081/api/v1/accounts/{sourceAccountId}/balance
curl -X GET http://localhost:8081/api/v1/accounts/{destinationAccountId}/balance
```

### 4.6 Verification through Integration Tests
The repository includes full integration tests utilizing Testcontainers for the Database, Kafka, Redis, and EventStoreDB. You can run all integration tests for both Phase 1 and Phase 2 logic using:

```bash
mvn test -pl account-service -Dtest=AccountControllerIT,TransferControllerIT
```
*Wait for the tests to complete. You should see 0 failures and 0 errors across 10 passing tests.*

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
If the database role `banking` or idempotency table is missing or corrupted inside the container, reset the volumes:
```bash
docker-compose down -v
docker-compose up -d
```
