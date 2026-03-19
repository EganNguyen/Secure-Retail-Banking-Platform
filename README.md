# Secure Retail Banking Platform 🏦

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache-Kafka-black.svg)](https://kafka.apache.org/)
[![EventStoreDB](https://img.shields.io/badge/EventStore-DB-red.svg)](https://www.eventstore.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A cloud-native, highly secure, and extremely scalable retail banking platform built with a modern enterprise "recipe". This project demonstrates advanced architectural patterns including **Domain-Driven Design (DDD)**, **CQRS**, **Event Sourcing**, and **Event-Driven Architecture (EDA)**.

---

## 🚀 Mission & Vision

The platform is designed to handle high-frequency retail banking operations with zero data loss, guaranteed auditability, and sub-500ms P99 latency. Every state change is captured as an immutable event, providing a perfect audit trail for regulatory compliance (PCI-DSS, PSD2, AML/KYC).

---

## 🛠️ Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | **Java 21** | High performance with Virtual Threads (Project Loom). |
| **Framework** | **Spring Boot 3.3.x** | Core application framework and ecosystem. |
| **Event Store** | **EventStoreDB** | Primary source of truth for all aggregates. |
| **Message Bus** | **Apache Kafka** | Asynchronous integration events between services. |
| **Read Database** | **PostgreSQL** | Optimized read models for CQRS projections. |
| **Cache** | **Redis** | High-speed balance caching and idempotency control. |
| **Security** | **Keycloak** | OIDC/OAuth2 authentication and RBAC. |
| **Communication** | **gRPC & REST** | Efficient internal and external communication. |
| **Resiliency** | **Resilience4j** | Circuit breakers, retries, and rate limiters. |

---

## 🏗️ Architectural Patterns

The project serves as a practical implementation of several enterprise-grade patterns:

### 1. Domain-Driven Design (DDD)
The system is divided into clearly defined **Bounded Contexts**:
- **Account Management**: Customer lifecycle and account state machines.
- **Transfer Orchestration**: Managing complex fund movements.
- **Ledger & Balance**: Double-entry bookkeeping and accounting.
- **Fraud & Risk**: Real-time ML-backed transaction scoring.

### 2. Event Sourcing & CQRS
- **Write Side**: Aggregates are hydrated from event streams in **EventStoreDB**. No state is updated; only new events are appended.
- **Read Side**: High-performance projections are maintained in **PostgreSQL** and cached in **Redis**.
- **Result**: Perfect auditability and independent scaling of read/write workloads.

### 3. Event-Driven Architecture (EDA)
- **Kafka** acts as the central nervous system, delivering integration events across service boundaries.
- **Outbox Pattern**: Ensures at-least-once delivery of events by persisting them in a local outbox before publishing to Kafka.

---

## 👨‍🍳 The Enterprise "Recipe"

This project follows a strict recipe for large-scale production environments:

- **Distributed Transactions via Saga Pattern**: Specifically the *Orchestration-based Saga* for multi-step transfers, ensuring data consistency without distributed locks.
- **Idempotency**: All write operations are protected by idempotency keys stored in Redis, preventing duplicate processing.
- **Virtual Threads**: Utilizing Java 21's `newVirtualThreadPerTaskExecutor` to maximize throughput for I/O-bound tasks (Kafka consumers, HTTP handlers).
- **Observability**: Standardized logging, metrics (Micrometer), and distributed tracing (OpenTelemetry).
- **Security-First**: mTLS between services, AES-256 encryption at rest, and zero-trust perimeter.

---

## 🚦 Getting Started

### 📦 Infrastructure & Backend Setup
The entire infrastructure (Kafka, EventStoreDB, Redis, Keycloak, Postgres) along with the core backend services are orchestrated via Docker Compose.

```bash
# Navigate to the banking platform directory
cd backend/banking-platform

# Spin up the ecosystem
docker-compose up -d
```

> [!NOTE]
> Backend services run inside Docker by default for consistent environments. To run a service independently for development: `mvn spring-boot:run -pl account-service`.

### 💻 Frontend Setup
The frontend is a Next.js application that can be run containerized for production parity:

```bash
# Build the frontend image
docker build -t nextjs-app ./frontend

# Run the frontend container
docker run -p 3000:3000 nextjs-app
```

---

## 🧪 Testing Strategy

### 🟢 Unit & Integration Tests
- **Unit Tests**: Focus on domain logic within Aggregates.
- **Integration Tests**: Verify persistence and Kafka messaging using **Testcontainers**.
  ```bash
  mvn test -pl account-service
  ```

### 🎭 End-to-End (E2E) Testing
We use **Playwright** for comprehensive browser-based verification of the entire system flow.

```bash
# Navigate to the frontend directory
cd frontend

# Install dependencies
npm install

# Run E2E tests in headless mode (CI)
npx playwright test tests/flow.spec.ts

# Run E2E tests in headed mode (Local Debugging)
npx playwright test tests/flow.spec.ts --headed
```

---

## 🗺️ Roadmap

- [x] Core Account & Ledger microservices.
- [x] Event Sourcing implementation with EventStoreDB.
- [x] Basic Transfer Saga.
- [ ] Multi-currency support and FX integration.
- [ ] Real-time Fraud detection with ML inference.
- [ ] Mobile SDK support.

---

*Built for learning and practicing high-scale enterprise Java development.*
