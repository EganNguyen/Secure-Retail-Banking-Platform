# Secure Retail Banking Platform — LLM Implementation Specification

> **Target Stack:** Java 21 · Spring Boot 3.x · Apache Kafka · PostgreSQL + EventStoreDB · Redis · Keycloak  
> **Architectural Patterns:** Domain-Driven Design (DDD) · CQRS · Event Sourcing · Event-Driven Architecture (EDA)  
> **Audience:** LLM agent / developer implementing this platform end-to-end

---

## Table of Contents

1. [Platform Vision & Constraints](#1-platform-vision--constraints)
2. [Domain Model & Bounded Contexts](#2-domain-model--bounded-contexts)
3. [System Architecture Overview](#3-system-architecture-overview)
4. [Technology Stack Reference](#4-technology-stack-reference)
5. [Event Sourcing & CQRS Design](#5-event-sourcing--cqrs-design)
6. [Domain Events Catalog](#6-domain-events-catalog)
7. [Microservice Blueprints](#7-microservice-blueprints)
8. [Real-Time Transfer Engine](#8-real-time-transfer-engine)
9. [Intelligent Transaction Management](#9-intelligent-transaction-management)
10. [Security Architecture](#10-security-architecture)
11. [API Design Contracts](#11-api-design-contracts)
12. [Data Models & Schema Definitions](#12-data-models--schema-definitions)
13. [Infrastructure & Deployment](#13-infrastructure--deployment)
14. [Testing Strategy](#14-testing-strategy)
15. [Observability & Monitoring](#15-observability--monitoring)
16. [Implementation Roadmap](#16-implementation-roadmap)
17. [LLM Implementation Instructions](#17-llm-implementation-instructions)

---

## 1. Platform Vision & Constraints

### 1.1 Platform Goals

The platform is a **secure, cloud-native retail banking system** that provides:

- Real-time domestic and international fund transfers (target: < 500ms P99 latency)
- Full audit trail via event sourcing — every state change is an immutable event
- Intelligent fraud detection and transaction enrichment via ML-backed rules engine
- Multi-channel access: REST API, WebSocket push notifications, mobile SDK
- Regulatory compliance: PCI-DSS Level 1, PSD2 Open Banking, GDPR, AML/KYC

### 1.2 Non-Functional Requirements

| Concern | Requirement |
|---|---|
| Availability | 99.95% uptime (< 4.4 hrs/year downtime) |
| Transfer Latency | P50 < 150ms, P99 < 500ms |
| Transaction Throughput | 10,000 TPS sustained, 50,000 TPS burst |
| Data Retention | Event log: 10 years; PII: right-to-be-forgotten compliant |
| RTO / RPO | RTO < 15min, RPO < 1min |
| Security | Zero-trust network, mTLS between services, AES-256 at rest |

### 1.3 Key Design Decisions & Rationale

- **Event Sourcing over CRUD** — Banking requires a complete, immutable audit log. Events are the source of truth; projections are derived.
- **CQRS** — Read and write workloads have vastly different shapes. Separate models optimize each independently.
- **DDD Bounded Contexts** — Prevents domain logic leakage. Each context owns its data and publishes integration events.
- **Saga Pattern for Transfers** — Distributed transactions across accounts require compensating transactions, not 2PC.
- **Outbox Pattern** — Guarantees at-least-once event publishing without distributed transaction between DB and Kafka.

---

## 2. Domain Model & Bounded Contexts

### 2.1 Bounded Context Map

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        BANKING PLATFORM DOMAIN                          │
│                                                                         │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │  IDENTITY &      │    │    ACCOUNT        │    │   TRANSFER       │  │
│  │  ACCESS          │───▶│    MANAGEMENT     │───▶│   ORCHESTRATION  │  │
│  │  [Keycloak/OIDC] │    │    [Core BC]      │    │   [Saga BC]      │  │
│  └──────────────────┘    └──────────────────┘    └──────────────────┘  │
│           │                       │                       │             │
│           │                       ▼                       ▼             │
│           │              ┌──────────────────┐    ┌──────────────────┐  │
│           │              │   LEDGER &        │    │   FRAUD &        │  │
│           └─────────────▶│   BALANCE         │    │   RISK ENGINE    │  │
│                          │   [Accounting BC] │    │   [ML BC]        │  │
│                          └──────────────────┘    └──────────────────┘  │
│                                   │                       │             │
│                                   ▼                       ▼             │
│                          ┌──────────────────┐    ┌──────────────────┐  │
│                          │  NOTIFICATION     │    │   AUDIT &        │  │
│                          │  & MESSAGING      │    │   COMPLIANCE     │  │
│                          │  [Support BC]     │    │   [Support BC]   │  │
│                          └──────────────────┘    └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Bounded Context Definitions

#### Context: Account Management (Core Domain)

**Responsibility:** Lifecycle of customer accounts — opening, closing, status changes, product assignment.

**Aggregates:**
- `CustomerAggregate` — identity, KYC status, linked accounts
- `AccountAggregate` — account state machine (PENDING → ACTIVE → FROZEN → CLOSED)
- `ProductAggregate` — account product definitions (savings, checking, term deposit)

**Domain Services:**
- `AccountOpeningService` — orchestrates KYC check + account creation
- `AccountStatusService` — handles freeze/unfreeze with reason audit

**Value Objects:** `AccountNumber`, `IBAN`, `Currency`, `Money`, `InterestRate`, `CustomerTier`

#### Context: Transfer Orchestration (Core Domain)

**Responsibility:** Orchestrates fund movements between accounts — local, domestic inter-bank, SEPA, SWIFT.

**Aggregates:**
- `TransferAggregate` — transfer state machine (saga root)
- `TransferLimitAggregate` — per-customer daily/monthly limits

**Domain Services:**
- `TransferValidationService` — limit checks, account eligibility, AML screening
- `TransferRoutingService` — selects payment rail (local / SEPA / SWIFT / RTP)
- `TransferSagaOrchestrator` — coordinates debit, credit, settlement steps

**Value Objects:** `TransferReference`, `PaymentRail`, `RemittanceInfo`, `FxRate`

#### Context: Ledger & Balance (Core Domain)

**Responsibility:** Double-entry bookkeeping. Maintains account balances as projections of ledger events.

**Aggregates:**
- `LedgerEntryAggregate` — immutable debit/credit entries
- `AccountBalanceAggregate` — current + available + reserved balance

**Domain Services:**
- `DoubleEntryService` — enforces balanced journal entries
- `BalanceProjectionService` — rebuilds balance from event stream

**Value Objects:** `LedgerEntryId`, `DebitCredit`, `BalanceSnapshot`

#### Context: Fraud & Risk Engine (Supporting Domain)

**Responsibility:** Real-time and batch risk scoring of transactions and behavioral anomaly detection.

**Aggregates:**
- `RiskProfileAggregate` — per-customer velocity, pattern, geo data
- `FraudCaseAggregate` — investigation lifecycle for flagged transactions

**Domain Services:**
- `RealTimeRiskScoringService` — synchronous scoring within transfer flow (< 50ms SLA)
- `BehavioralAnalysisService` — async ML model inference
- `FraudCaseManagementService` — human-in-the-loop review workflow

#### Context: Identity & Access (Generic Subdomain)

**Responsibility:** Authentication, authorization, customer profile. Delegated to Keycloak.

**Integration:** JWT bearer tokens (RS256). Claims carry `customerId`, `accountIds`, `roles`, `tier`.

#### Context: Notification & Messaging (Supporting Domain)

**Responsibility:** Push, email, SMS, in-app alerts triggered by domain events.

**Pattern:** Consumes integration events from Kafka, delivers via channel adapters.

---

## 3. System Architecture Overview

### 3.1 High-Level Component Diagram

```
                        ┌─────────────────────────────────────┐
                        │          API GATEWAY (Kong)          │
                        │  Rate Limit · Auth · TLS Termination │
                        └──────────────┬──────────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          ▼                            ▼                            ▼
 ┌────────────────┐          ┌────────────────┐          ┌────────────────┐
 │  Account Svc   │          │  Transfer Svc  │          │  Ledger Svc    │
 │  (Write Side)  │          │  (Saga Orch.)  │          │  (Write Side)  │
 └───────┬────────┘          └───────┬────────┘          └───────┬────────┘
         │                          │                            │
         ▼                          ▼                            ▼
 ┌─────────────────────────────────────────────────────────────────────────┐
 │                       EVENT STORE (EventStoreDB)                        │
 │            Stream per aggregate: account-{id}, transfer-{id}            │
 └──────────────────────────────┬──────────────────────────────────────────┘
                                │  (Persistent Subscriptions)
                                ▼
 ┌─────────────────────────────────────────────────────────────────────────┐
 │                     Apache Kafka (Event Bus)                            │
 │   Topics: account-events · transfer-events · ledger-events · fraud-*   │
 └────────┬──────────────┬────────────────┬───────────────┬───────────────┘
          ▼              ▼                ▼               ▼
 ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
 │  Account     │ │  Ledger      │ │  Fraud &     │ │ Notification │
 │  Query Svc   │ │  Query Svc   │ │  Risk Svc    │ │  Svc         │
 │  (Read Side) │ │  (Read Side) │ │              │ │              │
 └──────┬───────┘ └──────┬───────┘ └──────────────┘ └──────────────┘
        ▼                ▼
 ┌──────────────┐ ┌──────────────┐
 │  PostgreSQL  │ │  Redis Cache │
 │  (Read DBs)  │ │  (Hot Data)  │
 └──────────────┘ └──────────────┘
```

### 3.2 Deployment Architecture

- **Runtime:** Kubernetes (EKS / GKE / AKS)
- **Service Mesh:** Istio (mTLS, circuit breaker, traffic management)
- **Config:** Kubernetes Secrets + HashiCorp Vault (secret injection)
- **CDN / WAF:** Cloudflare in front of API Gateway
- **Regions:** Active-active multi-region (primary + DR), Kafka MirrorMaker 2 for replication

---

## 4. Technology Stack Reference

### 4.1 Core Technologies

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Language | Java | 21 LTS | Virtual threads (Project Loom) for high concurrency |
| Framework | Spring Boot | 3.3.x | Application framework, actuator, auto-config |
| Event Store | EventStoreDB | 24.x | Immutable event streams per aggregate |
| Message Bus | Apache Kafka | 3.7.x | Integration events between bounded contexts |
| Read DB | PostgreSQL | 16 | CQRS read-side projections |
| Cache | Redis | 7.x | Account balance cache, idempotency keys, session |
| Identity | Keycloak | 25.x | OIDC/OAuth2 provider, RBAC |
| API Gateway | Kong | 3.x | Rate limiting, JWT validation, routing |
| Service Mesh | Istio | 1.22 | mTLS, observability, traffic policies |
| Containers | Docker + Kubernetes | 1.30 | Orchestration |
| Secret Mgmt | HashiCorp Vault | 1.17 | Dynamic secrets, PKI |

### 4.2 Key Spring Dependencies (pom.xml excerpt)

```xml
<!-- Event Sourcing -->
<dependency>
    <groupId>com.eventstore</groupId>
    <artifactId>db-client-java</artifactId>
    <version>5.3.1</version>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- Axon Framework (CQRS/ES scaffolding) — optional but recommended -->
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring-boot-starter</artifactId>
    <version>4.10.x</version>
</dependency>

<!-- Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Observability -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Resilience -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

### 4.3 Java 21 Feature Usage

- **Virtual Threads** (`--enable-preview` not needed in 21) — Use `Executors.newVirtualThreadPerTaskExecutor()` for Kafka consumers and HTTP handlers to maximize throughput without reactive complexity.
- **Sealed Classes** — Model domain events and commands as sealed hierarchies for exhaustive pattern matching.
- **Records** — Use for Value Objects, DTOs, and immutable event payloads.
- **Pattern Matching** — `switch` expressions for event handler dispatch in projectors.
- **Structured Concurrency (Preview)** — For coordinating multi-step transfer operations.

---

## 5. Event Sourcing & CQRS Design

### 5.1 Core Concepts Implementation

#### Aggregate Base Class

```java
public abstract class AggregateRoot {
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private long version = -1;

    protected void apply(DomainEvent event) {
        handle(event); // update internal state
        uncommittedEvents.add(event);
    }

    protected void rehydrate(List<DomainEvent> events) {
        events.forEach(e -> {
            handle(e);
            version++;
        });
    }

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    protected abstract void handle(DomainEvent event);

    public long getVersion() { return version; }
}
```

#### Event Store Repository Pattern

```java
public interface EventSourcedRepository<T extends AggregateRoot> {
    void save(T aggregate, long expectedVersion);
    T load(String aggregateId);
    Optional<T> loadOptional(String aggregateId);
}

@Repository
public class EventStoreDBAccountRepository implements EventSourcedRepository<AccountAggregate> {

    private final EventStoreDBClient client;
    private final DomainEventSerializer serializer;

    @Override
    public void save(AccountAggregate aggregate, long expectedVersion) {
        String streamName = "account-" + aggregate.getAccountId();
        List<EventData> events = aggregate.getUncommittedEvents().stream()
            .map(serializer::toEventData)
            .toList();

        AppendToStreamOptions options = AppendToStreamOptions.get()
            .expectedRevision(expectedVersion == -1
                ? ExpectedRevision.NO_STREAM
                : ExpectedRevision.expectedRevision(expectedVersion));

        client.appendToStream(streamName, options, events.iterator()).get();
        aggregate.markEventsAsCommitted();
    }

    @Override
    public AccountAggregate load(String accountId) {
        String streamName = "account-" + accountId;
        ReadStreamOptions options = ReadStreamOptions.get().fromStart().forwards();

        List<DomainEvent> events = client.readStream(streamName, options).get()
            .getEvents().stream()
            .map(e -> serializer.fromResolvedEvent(e))
            .toList();

        if (events.isEmpty()) throw new AggregateNotFoundException(accountId);

        AccountAggregate account = new AccountAggregate();
        account.rehydrate(events);
        return account;
    }
}
```

#### Outbox Pattern for Reliable Event Publishing

```java
// Domain event is first saved to outbox table in same DB transaction
@Transactional
public void publishWithOutbox(DomainEvent event, String aggregateId) {
    OutboxMessage message = OutboxMessage.builder()
        .id(UUID.randomUUID())
        .aggregateId(aggregateId)
        .eventType(event.getClass().getSimpleName())
        .payload(serializer.toJson(event))
        .occurredAt(Instant.now())
        .status(OutboxStatus.PENDING)
        .build();
    outboxRepository.save(message);
}

// Separate polling publisher (or Debezium CDC) reads outbox and publishes to Kafka
@Scheduled(fixedDelay = 100)
public void processOutbox() {
    List<OutboxMessage> pending = outboxRepository.findPendingBatch(100);
    pending.forEach(msg -> {
        kafkaTemplate.send(resolveTopicFor(msg.getEventType()), msg.getAggregateId(), msg.getPayload())
            .whenComplete((result, ex) -> {
                if (ex == null) outboxRepository.markPublished(msg.getId());
                else outboxRepository.markFailed(msg.getId(), ex.getMessage());
            });
    });
}
```

### 5.2 CQRS Command & Query Separation

#### Command Side

```java
// Commands are simple records
public record OpenAccountCommand(
    String customerId,
    AccountType type,
    Currency currency,
    String productCode
) implements Command {}

// Command handler dispatches to aggregate
@CommandHandler
@Transactional
public String handle(OpenAccountCommand command) {
    customerRepository.findById(command.customerId())
        .filter(Customer::isKycApproved)
        .orElseThrow(() -> new KycNotApprovedEx(command.customerId()));

    AccountAggregate account = AccountAggregate.open(command);
    long expectedVersion = -1; // new stream
    accountRepository.save(account, expectedVersion);

    // Outbox publish happens here — same transaction
    account.getUncommittedEvents().forEach(e ->
        outboxService.publishWithOutbox(e, account.getAccountId()));

    return account.getAccountId();
}
```

#### Query Side (Projection)

```java
// Projection listens to Kafka topic and updates read model
@KafkaListener(topics = "account-events", groupId = "account-projector")
public void project(ConsumerRecord<String, String> record) {
    DomainEvent event = deserializer.deserialize(record.value());

    switch (event) {
        case AccountOpenedEvent e -> {
            AccountReadModel view = AccountReadModel.builder()
                .accountId(e.accountId())
                .customerId(e.customerId())
                .iban(e.iban())
                .type(e.type())
                .currency(e.currency())
                .status(AccountStatus.ACTIVE)
                .balance(Money.zero(e.currency()))
                .openedAt(e.occurredAt())
                .build();
            accountReadRepository.save(view);
        }
        case AccountFrozenEvent e -> accountReadRepository.updateStatus(e.accountId(), AccountStatus.FROZEN);
        case BalanceUpdatedEvent e -> accountReadRepository.updateBalance(e.accountId(), e.newBalance());
        default -> log.debug("Unhandled event type in account projector: {}", event.getClass().getSimpleName());
    }
}
```

### 5.3 Snapshot Strategy

For aggregates with long event streams (> 500 events), create periodic snapshots:

```java
public class SnapshotService {
    private static final int SNAPSHOT_THRESHOLD = 500;

    public void saveSnapshotIfNeeded(AccountAggregate aggregate) {
        if (aggregate.getVersion() % SNAPSHOT_THRESHOLD == 0) {
            AccountSnapshot snapshot = AccountSnapshot.from(aggregate);
            snapshotRepository.save(snapshot);
        }
    }
}

// Loading with snapshot
public AccountAggregate loadWithSnapshot(String accountId) {
    Optional<AccountSnapshot> snapshot = snapshotRepository.findLatest(accountId);
    AccountAggregate account;
    long startVersion;

    if (snapshot.isPresent()) {
        account = AccountAggregate.fromSnapshot(snapshot.get());
        startVersion = snapshot.get().version();
    } else {
        account = new AccountAggregate();
        startVersion = 0;
    }

    // Load only events after snapshot version
    List<DomainEvent> tail = eventStore.loadFrom(accountId, startVersion);
    account.rehydrate(tail);
    return account;
}
```

---

## 6. Domain Events Catalog

### 6.1 Event Design Principles

- Events are **past tense**, immutable facts: `AccountOpened`, not `OpenAccount`
- Every event carries: `eventId (UUID)`, `aggregateId`, `version`, `occurredAt (Instant)`, `correlationId`, `causationId`
- Events are **versioned** — use `@JsonTypeInfo` + discriminator for schema evolution
- Never use database IDs as event identifiers — use domain-meaningful UUIDs

### 6.2 Account Management Events

```java
// Base sealed interface
public sealed interface AccountEvent extends DomainEvent
    permits AccountOpenedEvent, AccountActivatedEvent, AccountFrozenEvent,
            AccountUnfrozenEvent, AccountClosedEvent, AccountLimitChangedEvent {}

public record AccountOpenedEvent(
    String eventId,
    String accountId,
    String customerId,
    String iban,
    AccountType type,
    String productCode,
    Currency currency,
    long version,
    Instant occurredAt,
    String correlationId
) implements AccountEvent {}

public record AccountFrozenEvent(
    String eventId,
    String accountId,
    FreezeReason reason,
    String initiatedBy,    // operator ID or "SYSTEM"
    String notes,
    long version,
    Instant occurredAt,
    String correlationId
) implements AccountEvent {}
```

### 6.3 Transfer Events

```java
public sealed interface TransferEvent extends DomainEvent
    permits TransferInitiatedEvent, TransferValidatedEvent, TransferDebitedEvent,
            TransferCreditedEvent, TransferCompletedEvent, TransferFailedEvent,
            TransferReversedEvent {}

public record TransferInitiatedEvent(
    String eventId,
    String transferId,
    String sourceAccountId,
    String destinationAccountId,
    Money amount,
    Currency targetCurrency,
    PaymentRail rail,
    RemittanceInfo remittanceInfo,
    TransferType type,          // INTERNAL / SEPA / SWIFT / RTP
    String initiatedBy,
    Instant occurredAt,
    String correlationId
) implements TransferEvent {}

public record TransferFailedEvent(
    String eventId,
    String transferId,
    TransferFailureReason reason,
    String details,
    boolean reversalRequired,
    long version,
    Instant occurredAt,
    String correlationId
) implements TransferEvent {}
```

### 6.4 Ledger Events

```java
public record LedgerEntryCreatedEvent(
    String eventId,
    String entryId,
    String accountId,
    String transferId,
    DebitCredit debitCredit,
    Money amount,
    Money balanceBefore,
    Money balanceAfter,
    String description,
    Instant valueDate,
    Instant occurredAt,
    String correlationId
) implements LedgerEvent {}
```

### 6.5 Fraud / Risk Events

```java
public record TransactionRiskScoredEvent(
    String eventId,
    String transferId,
    String customerId,
    int riskScore,                  // 0–1000
    RiskLevel riskLevel,            // LOW / MEDIUM / HIGH / CRITICAL
    List<RiskSignal> signals,       // triggered rules
    RiskDecision decision,          // APPROVE / REVIEW / BLOCK
    long inferenceMs,
    Instant occurredAt,
    String correlationId
) implements FraudEvent {}
```

---

## 7. Microservice Blueprints

### 7.1 Service Structure (per service)

Each microservice follows this package structure:

```
com.bank.{service}/
├── api/                        # REST controllers, WebSocket handlers
│   ├── command/                # Command endpoints (write side)
│   └── query/                  # Query endpoints (read side)
├── application/                # Application layer (use cases)
│   ├── command/                # Command handlers
│   ├── query/                  # Query handlers
│   └── saga/                   # Saga orchestrators
├── domain/                     # Domain model
│   ├── model/                  # Aggregates, entities, value objects
│   ├── event/                  # Domain events (sealed interfaces)
│   ├── command/                # Commands (records)
│   ├── service/                # Domain services
│   └── repository/             # Repository interfaces (ports)
├── infrastructure/             # Adapters
│   ├── persistence/            # EventStoreDB + PostgreSQL implementations
│   ├── messaging/              # Kafka producers/consumers
│   ├── external/               # HTTP clients to external systems
│   └── config/                 # Spring configuration classes
└── BankingServiceApplication.java
```

### 7.2 Account Service

**Service name:** `account-service`  
**Port:** 8081  
**Kafka topics produced:** `account-events`  
**Kafka topics consumed:** `fraud-events` (for auto-freeze on critical risk)

#### Key Endpoints

```
POST   /api/v1/accounts                    — OpenAccountCommand
GET    /api/v1/accounts/{accountId}        — GetAccountQuery
GET    /api/v1/accounts?customerId={id}    — GetAccountsByCustomerQuery
PUT    /api/v1/accounts/{accountId}/freeze — FreezeAccountCommand
PUT    /api/v1/accounts/{accountId}/unfreeze
GET    /api/v1/accounts/{accountId}/history — paginated event log
```

#### AccountAggregate State Machine

```
PENDING ──[KYC approved + activate]──▶ ACTIVE
ACTIVE  ──[freeze]──────────────────▶ FROZEN
FROZEN  ──[unfreeze]────────────────▶ ACTIVE
ACTIVE  ──[close request]───────────▶ CLOSING
CLOSING ──[balance zero + confirmed]─▶ CLOSED
Any     ──[regulatory block]─────────▶ BLOCKED (terminal for transfers)
```

### 7.3 Transfer Service

**Service name:** `transfer-service`  
**Port:** 8082  
**Kafka topics produced:** `transfer-events`  
**Kafka topics consumed:** `account-events`, `fraud-events`

This service is the **saga orchestrator** for fund transfers. It coordinates:

1. Transfer initiation and validation
2. Risk scoring (synchronous call to risk service within 50ms)
3. Source account debit
4. Destination account credit (or external rail submission)
5. Ledger entry creation
6. Transfer completion or compensating transactions

#### Transfer Saga States

```
INITIATED → RISK_SCORED → VALIDATED → DEBIT_PENDING → DEBITED
         → CREDIT_PENDING → CREDITED → SETTLING → COMPLETED

Failure paths:
RISK_SCORED     [BLOCK]  → BLOCKED (terminal)
VALIDATED       [fail]   → VALIDATION_FAILED (terminal)
DEBIT_PENDING   [fail]   → DEBIT_FAILED (terminal)
DEBITED         [fail]   → REVERSING → REVERSED (terminal)
CREDIT_PENDING  [fail]   → CREDIT_FAILED → REVERSING → REVERSED
```

### 7.4 Ledger Service

**Service name:** `ledger-service`  
**Port:** 8083  
**Kafka topics produced:** `ledger-events`  
**Kafka topics consumed:** `transfer-events`

Responsible exclusively for double-entry bookkeeping. Each transfer produces two ledger entries (debit source, credit destination) plus contra-entries if FX conversion applies.

**Balance computation:** Account balance is always the sum of all ledger entries for that account, projected into a `AccountBalanceReadModel`. Redis caches the current balance with a 5-second TTL; cache is invalidated on `LedgerEntryCreatedEvent`.

### 7.5 Risk & Fraud Service

**Service name:** `risk-service`  
**Port:** 8084  
**Kafka topics produced:** `fraud-events`  
**Kafka topics consumed:** `transfer-events`, `account-events`

Provides two interfaces:

1. **Synchronous scoring endpoint** — `POST /api/v1/risk/score` — called by Transfer Service during saga, 50ms SLA
2. **Async event consumer** — enriches risk profiles, updates behavioral models

---

## 8. Real-Time Transfer Engine

### 8.1 Transfer Flow (Internal — Same Bank)

```
Client Request
    │
    ▼
POST /api/v1/transfers
    │
    ▼
[1] IDEMPOTENCY CHECK (Redis — transferIdempotencyKey TTL 24h)
    │
    ▼
[2] VALIDATE REQUEST (schema + business rules in command handler)
    │
    ▼
[3] RISK SCORE (sync HTTP to risk-service, circuit breaker, 50ms timeout)
    │  ├── BLOCK → reject immediately, publish TransactionBlockedEvent
    │  └── APPROVE/REVIEW → continue
    ▼
[4] TRANSFER SAGA INITIATED
    │
    ▼
[5] DEBIT SOURCE ACCOUNT
    │  AccountAggregate.debit(amount) → BalanceReservedEvent persisted
    │  Optimistic concurrency: expectedVersion check
    │
    ▼
[6] CREDIT DESTINATION ACCOUNT
    │  AccountAggregate.credit(amount) → FundsReceivedEvent
    │
    ▼
[7] LEDGER ENTRIES CREATED (async, published via Kafka)
    │
    ▼
[8] TRANSFER COMPLETED
    │  TransferCompletedEvent → Kafka → Notification Service (push + email)
    │
    ▼
Response: 200 OK { transferId, status: COMPLETED, completedAt }
```

### 8.2 Transfer Flow (External — SEPA/SWIFT)

For external transfers, step [6] is replaced by submission to the payment rail adapter. The saga remains in `SETTLING` state until the external ACK arrives via webhook or polling.

```java
@Component
public class TransferSagaOrchestrator {

    @SagaEventHandler(associationProperty = "transferId")
    public void on(TransferInitiatedEvent event) {
        // Step 1: synchronous risk check
        RiskScoreResult risk = riskClient.score(RiskScoreRequest.from(event));
        if (risk.decision() == BLOCK) {
            commandGateway.sendAndWait(new BlockTransferCommand(event.transferId(), risk));
            return;
        }
        // Step 2: debit source account
        commandGateway.sendAndWait(new DebitAccountCommand(
            event.sourceAccountId(), event.amount(), event.transferId()));
    }

    @SagaEventHandler(associationProperty = "transferId")
    public void on(AccountDebitedEvent event) {
        // Step 3: credit or submit to rail
        if (isInternal(event.transferId())) {
            commandGateway.sendAndWait(new CreditAccountCommand(
                resolveDestination(event.transferId()), event.amount(), event.transferId()));
        } else {
            paymentRailGateway.submit(event.transferId());
        }
    }

    @SagaEventHandler(associationProperty = "transferId")
    public void on(AccountCreditedEvent event) {
        commandGateway.sendAndWait(new CompleteTransferCommand(event.transferId()));
        SagaLifecycle.end();
    }

    @SagaEventHandler(associationProperty = "transferId")
    public void on(TransferFailedEvent event) {
        if (event.reversalRequired()) {
            commandGateway.sendAndWait(new ReverseDebitCommand(event.transferId()));
        }
        SagaLifecycle.end();
    }
}
```

### 8.3 Idempotency

Every transfer request must carry a client-generated `X-Idempotency-Key` header (UUID v4).

```java
@Around("@annotation(Idempotent)")
public Object checkIdempotency(ProceedingJoinPoint pjp, HttpServletRequest request) throws Throwable {
    String key = request.getHeader("X-Idempotency-Key");
    if (key == null) throw new MissingIdempotencyKeyException();

    String cacheKey = "idempotency:" + key;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return objectMapper.readValue(cached, IdempotencyResponse.class).response();

    Object result = pjp.proceed();
    redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
        Duration.ofHours(24));
    return result;
}
```

### 8.4 Optimistic Concurrency Control

All writes to EventStoreDB use **expected version** to prevent concurrent modification:

```java
// If two simultaneous debits race, the second will fail with WrongExpectedVersionException
AppendToStreamOptions.get().expectedRevision(ExpectedRevision.expectedRevision(currentVersion));

// In command handler, catch and retry with exponential backoff (max 3 attempts)
@Retryable(retryFor = WrongExpectedVersionException.class,
           maxAttempts = 3,
           backoff = @Backoff(delay = 50, multiplier = 2))
public void handleDebit(DebitAccountCommand command) { ... }
```

---

## 9. Intelligent Transaction Management

### 9.1 Real-Time Risk Scoring Engine

The risk engine runs **rule-based signals** combined with an **ML inference layer**:

#### Rule-Based Signals

```java
public interface RiskSignal {
    int evaluate(TransactionContext context); // returns 0–100 score contribution
    String getSignalId();
    RiskSignalCategory category();
}

// Examples:
public class VelocityRiskSignal implements RiskSignal {
    // More than N transfers in M minutes
    public int evaluate(TransactionContext ctx) {
        int count = velocityStore.countRecent(ctx.customerId(), Duration.ofMinutes(10));
        return switch (count) {
            case 0, 1, 2 -> 0;
            case 3, 4 -> 10;
            case 5, 6, 7 -> 25;
            default -> 60;
        };
    }
}

public class UnusualAmountSignal implements RiskSignal {
    // Compares to customer's 90-day average
    public int evaluate(TransactionContext ctx) {
        double avg = statsStore.getAvgTransferAmount(ctx.customerId(), 90);
        double ratio = ctx.amount().toDouble() / avg;
        if (ratio > 10) return 50;
        if (ratio > 5) return 25;
        if (ratio > 3) return 10;
        return 0;
    }
}
```

#### Score Aggregation

```java
public RiskScoreResult score(TransactionContext context) {
    List<RiskSignal> signals = signalRegistry.getApplicableSignals(context);
    int totalScore = signals.stream()
        .mapToInt(s -> s.evaluate(context))
        .sum();

    // Clamp to 0-1000
    int score = Math.min(totalScore * 10, 1000);

    // ML model override for high-confidence predictions
    if (mlModelEnabled) {
        MlPrediction prediction = mlClient.predict(context);
        if (prediction.confidence() > 0.90) {
            score = (int) (score * 0.3 + prediction.score() * 0.7);
        }
    }

    RiskLevel level = RiskLevel.fromScore(score);
    RiskDecision decision = decisionPolicy.decide(level, context.customerId());

    return new RiskScoreResult(score, level, decision,
        signals.stream().filter(s -> s.evaluate(context) > 0).toList());
}
```

### 9.2 Transaction Enrichment Pipeline

Transactions are enriched asynchronously after completion:

```java
// Kafka Streams topology for enrichment
StreamsBuilder builder = new StreamsBuilder();

KStream<String, TransferCompletedEvent> transfers =
    builder.stream("transfer-events", Consumed.with(Serdes.String(), transferSerde))
        .filter((k, v) -> v instanceof TransferCompletedEvent);

KTable<String, MerchantInfo> merchants =
    builder.table("merchant-reference", Consumed.with(Serdes.String(), merchantSerde));

transfers
    .leftJoin(merchants,
        (transfer, merchant) -> TransactionEnrichmentService.enrich(transfer, merchant),
        Joined.with(Serdes.String(), transferSerde, merchantSerde))
    .mapValues(enriched -> TransactionEnrichedEvent.from(enriched))
    .to("enriched-transactions");
```

### 9.3 Intelligent Transaction Limits

Limits are dynamic — adjusted based on customer tier, KYC level, and behavioral score:

```java
public TransferLimit resolveLimit(String customerId, TransferType type) {
    CustomerProfile profile = customerQueryService.getProfile(customerId);

    // Base limits by tier
    Money baseLimit = switch (profile.tier()) {
        case STANDARD  -> Money.of(5_000, EUR);
        case PREMIUM   -> Money.of(25_000, EUR);
        case PRIVATE   -> Money.of(250_000, EUR);
    };

    // KYC level multiplier
    BigDecimal kycMultiplier = switch (profile.kycLevel()) {
        case BASIC    -> BigDecimal.valueOf(0.5);
        case ENHANCED -> BigDecimal.ONE;
        case FULL     -> BigDecimal.valueOf(2.0);
    };

    // Behavioral trust score boost (0–20%)
    double trustBoost = 1.0 + (profile.trustScore() / 1000.0 * 0.2);

    Money effectiveLimit = baseLimit.multiply(kycMultiplier).multiply(trustBoost);

    // Daily consumed amount from ledger
    Money consumed = ledgerQueryService.getDailyTransferTotal(customerId, type);

    return TransferLimit.of(effectiveLimit, consumed, effectiveLimit.subtract(consumed));
}
```

### 9.4 Fraud Case Management

When a transfer is flagged for review, a `FraudCase` aggregate is created:

```java
public class FraudCaseAggregate extends AggregateRoot {

    public static FraudCaseAggregate open(OpenFraudCaseCommand cmd) {
        FraudCaseAggregate c = new FraudCaseAggregate();
        c.apply(new FraudCaseOpenedEvent(
            UUID.randomUUID().toString(),
            cmd.caseId(), cmd.transferId(), cmd.customerId(),
            cmd.riskScore(), cmd.signals(), Instant.now(), cmd.correlationId()));
        return c;
    }

    public void review(ReviewFraudCaseCommand cmd) {
        if (status != CaseStatus.OPEN) throw new InvalidCaseStatusException(status);
        apply(new FraudCaseReviewedEvent(...));
    }

    public void resolve(ResolveFraudCaseCommand cmd) {
        if (status != CaseStatus.UNDER_REVIEW) throw new InvalidCaseStatusException(status);
        // APPROVE → release held transfer; REJECT → trigger reversal
        apply(new FraudCaseResolvedEvent(...));
    }
}
```

---

## 10. Security Architecture

### 10.1 Authentication & Authorization

**Authentication:** Keycloak OIDC. Customers authenticate via Authorization Code Flow with PKCE (mobile/web). Service-to-service uses Client Credentials flow.

**JWT Claims Structure:**

```json
{
  "sub": "customer-uuid",
  "customerId": "CUST-001234",
  "accountIds": ["ACC-000001", "ACC-000002"],
  "roles": ["CUSTOMER"],
  "tier": "PREMIUM",
  "kycLevel": "ENHANCED",
  "mfaVerified": true,
  "iss": "https://auth.bank.com/realms/retail",
  "exp": 1712345678,
  "iat": 1712342078
}
```

**Spring Security Configuration:**

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(bankingJwtConverter())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(POST, "/api/v1/transfers").hasRole("CUSTOMER")
                .requestMatchers(GET,  "/api/v1/accounts/**").hasAnyRole("CUSTOMER", "OPERATOR")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .csrf(AbstractHttpConfigurer::disable) // Stateless JWT — CSRF not applicable
            .build();
    }

    @Bean
    public JwtAuthenticationConverter bankingJwtConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("roles");
        converter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }
}
```

### 10.2 Account-Level Authorization

Customers must only access their own accounts. Enforce this at the service layer:

```java
@Service
public class AccountAuthorizationService {

    public void assertAccountOwnership(String customerId, String accountId) {
        AccountOwnershipReadModel ownership = accountQueryService.getOwnership(accountId);
        if (!ownership.customerId().equals(customerId)) {
            // Log security event before throwing
            auditService.logUnauthorizedAccess(customerId, accountId);
            throw new AccountAccessDeniedException(accountId);
        }
    }
}

// Applied in command handlers:
@CommandHandler
public String handle(InitiateTransferCommand cmd, @AuthenticationPrincipal JwtPrincipal principal) {
    authorizationService.assertAccountOwnership(principal.getCustomerId(), cmd.sourceAccountId());
    // proceed...
}
```

### 10.3 Sensitive Data Handling

- **PAN / Card Numbers:** Never stored. Tokenized via Vault transit secrets engine.
- **Account Numbers:** Masked in logs — last 4 digits only: `****1234`
- **PII Encryption:** All PII columns encrypted at rest (AES-256) using column-level encryption in PostgreSQL with keys in Vault.
- **Event Payload Encryption:** Sensitive fields in domain events encrypted with envelope encryption before EventStoreDB write.
- **GDPR Right to Erasure:** PII stored in a separate `customer-pii-store`; events reference `customerId` only. Erasure deletes PII store entry; event log is retained for regulatory compliance (pseudonymization strategy).

### 10.4 mTLS Between Services

All service-to-service calls use Istio-managed mTLS:

```yaml
# PeerAuthentication — enforce mTLS cluster-wide
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: banking
spec:
  mtls:
    mode: STRICT
```

### 10.5 Rate Limiting

Kong rate limiting plugin per customer per endpoint:

```yaml
plugins:
  - name: rate-limiting
    config:
      minute: 60        # 60 requests/minute per customer
      hour: 500
      policy: redis
      redis_host: redis-cluster
      limit_by: consumer
```

Transfer endpoint stricter:

```yaml
  - name: rate-limiting
    route: transfer-route
    config:
      minute: 10        # 10 transfer initiations/minute
      hour: 100
```

---

## 11. API Design Contracts

### 11.1 API Versioning

- URL path versioning: `/api/v1/`, `/api/v2/`
- Breaking changes require new major version
- Deprecated versions supported for minimum 12 months

### 11.2 Transfer API

#### Initiate Transfer

```
POST /api/v1/transfers
Content-Type: application/json
Authorization: Bearer {jwt}
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

Request:
{
  "sourceAccountId": "ACC-000001",
  "destination": {
    "type": "INTERNAL" | "SEPA" | "SWIFT",
    "accountId": "ACC-000002",          // for INTERNAL
    "iban": "DE89370400440532013000",    // for SEPA
    "swiftCode": "DEUTDEDB",            // for SWIFT
    "accountNumber": "12345678",
    "bankCode": "37040044"
  },
  "amount": {
    "value": "250.00",
    "currency": "EUR"
  },
  "remittanceInfo": "Invoice INV-2024-042",
  "scheduledAt": null                   // null = immediate
}

Response 202 Accepted:
{
  "transferId": "TRF-20240315-000123",
  "status": "INITIATED",
  "estimatedCompletionAt": "2024-03-15T14:32:10Z",
  "links": {
    "self": "/api/v1/transfers/TRF-20240315-000123",
    "status": "/api/v1/transfers/TRF-20240315-000123/status"
  }
}

Response 422 Unprocessable:
{
  "error": "INSUFFICIENT_FUNDS",
  "message": "Available balance EUR 100.00 is less than transfer amount EUR 250.00",
  "transferId": null
}

Response 403 Forbidden:
{
  "error": "TRANSFER_BLOCKED",
  "message": "Transfer blocked by risk engine",
  "riskScore": 820,
  "caseId": "CASE-20240315-000042"
}
```

#### Get Transfer Status

```
GET /api/v1/transfers/{transferId}

Response 200:
{
  "transferId": "TRF-20240315-000123",
  "status": "COMPLETED",
  "sourceAccount": { "id": "ACC-000001", "maskedIban": "DE89****3000" },
  "destinationAccount": { "id": "ACC-000002", "maskedIban": "DE89****4001" },
  "amount": { "value": "250.00", "currency": "EUR" },
  "fees": { "value": "0.00", "currency": "EUR" },
  "initiatedAt": "2024-03-15T14:32:01Z",
  "completedAt": "2024-03-15T14:32:09Z",
  "remittanceInfo": "Invoice INV-2024-042",
  "events": [
    { "status": "INITIATED",  "at": "2024-03-15T14:32:01Z" },
    { "status": "RISK_SCORED","at": "2024-03-15T14:32:02Z", "riskScore": 120 },
    { "status": "DEBITED",    "at": "2024-03-15T14:32:04Z" },
    { "status": "CREDITED",   "at": "2024-03-15T14:32:07Z" },
    { "status": "COMPLETED",  "at": "2024-03-15T14:32:09Z" }
  ]
}
```

### 11.3 Account API

```
GET  /api/v1/accounts/{accountId}
GET  /api/v1/accounts/{accountId}/balance
GET  /api/v1/accounts/{accountId}/transactions?page=0&size=20&from=2024-01-01&to=2024-03-31
GET  /api/v1/accounts/{accountId}/statements/{year}/{month}

Response — Account Balance:
{
  "accountId": "ACC-000001",
  "iban": "DE89370400440532013000",
  "currency": "EUR",
  "balance": {
    "current":   { "value": "5420.50", "currency": "EUR" },
    "available": { "value": "5170.50", "currency": "EUR" },  // current - reserved
    "reserved":  { "value": "250.00",  "currency": "EUR" }   // pending debits
  },
  "status": "ACTIVE",
  "asOf": "2024-03-15T14:35:00Z"
}
```

### 11.4 WebSocket — Real-Time Notifications

```
WS /api/v1/ws/notifications
Authorization: Bearer {jwt}   // sent as query param or first message

Server → Client messages:
{
  "type": "TRANSFER_COMPLETED",
  "data": {
    "transferId": "TRF-20240315-000123",
    "amount": "-250.00",
    "currency": "EUR",
    "balanceAfter": "5170.50",
    "completedAt": "2024-03-15T14:32:09Z"
  }
}
{
  "type": "TRANSFER_FAILED",
  "data": { "transferId": "...", "reason": "INSUFFICIENT_FUNDS" }
}
{
  "type": "ACCOUNT_FROZEN",
  "data": { "accountId": "ACC-000001", "reason": "SUSPICIOUS_ACTIVITY" }
}
```

### 11.5 Error Response Standard

All errors follow RFC 7807 Problem Details:

```json
{
  "type": "https://api.bank.com/errors/insufficient-funds",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Available balance EUR 100.00 is less than requested EUR 250.00",
  "instance": "/api/v1/transfers",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2024-03-15T14:32:01Z"
}
```

---

## 12. Data Models & Schema Definitions

### 12.1 EventStoreDB Streams

| Stream Pattern | Aggregate | Retention |
|---|---|---|
| `account-{accountId}` | AccountAggregate | Forever |
| `transfer-{transferId}` | TransferAggregate | Forever |
| `ledger-{entryId}` | LedgerEntryAggregate | Forever |
| `fraud-case-{caseId}` | FraudCaseAggregate | 7 years |
| `$all` | All events | Forever |
| `$ce-account` | Category projection | Forever |

### 12.2 PostgreSQL Read-Side Schema

```sql
-- Account read model
CREATE TABLE account_read_model (
    account_id      VARCHAR(50)  PRIMARY KEY,
    customer_id     VARCHAR(50)  NOT NULL,
    iban            VARCHAR(34)  NOT NULL UNIQUE,
    product_code    VARCHAR(20)  NOT NULL,
    account_type    VARCHAR(20)  NOT NULL,
    currency        CHAR(3)      NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    balance_current NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_avail   NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_reserved NUMERIC(19,4) NOT NULL DEFAULT 0,
    opened_at       TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL
);
CREATE INDEX idx_account_customer ON account_read_model(customer_id);

-- Transaction history read model (append-only)
CREATE TABLE transaction_read_model (
    entry_id        VARCHAR(50)   PRIMARY KEY,
    account_id      VARCHAR(50)   NOT NULL,
    transfer_id     VARCHAR(50),
    debit_credit    CHAR(1)       NOT NULL CHECK (debit_credit IN ('D','C')),
    amount          NUMERIC(19,4) NOT NULL,
    currency        CHAR(3)       NOT NULL,
    balance_after   NUMERIC(19,4) NOT NULL,
    description     VARCHAR(500),
    counterpart_name VARCHAR(200),
    counterpart_iban VARCHAR(34),
    category        VARCHAR(50),
    value_date      DATE          NOT NULL,
    booked_at       TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_txn_account_date ON transaction_read_model(account_id, booked_at DESC);
CREATE INDEX idx_txn_transfer     ON transaction_read_model(transfer_id);

-- Transfer read model
CREATE TABLE transfer_read_model (
    transfer_id       VARCHAR(50)  PRIMARY KEY,
    source_account_id VARCHAR(50)  NOT NULL,
    dest_account_id   VARCHAR(50),
    dest_iban         VARCHAR(34),
    dest_name         VARCHAR(200),
    amount            NUMERIC(19,4) NOT NULL,
    currency          CHAR(3)       NOT NULL,
    payment_rail      VARCHAR(20)   NOT NULL,
    status            VARCHAR(30)   NOT NULL,
    risk_score        INTEGER,
    initiated_at      TIMESTAMPTZ   NOT NULL,
    completed_at      TIMESTAMPTZ,
    failure_reason    VARCHAR(100),
    remittance_info   VARCHAR(500),
    correlation_id    VARCHAR(50)   NOT NULL
);

-- Outbox table
CREATE TABLE outbox_messages (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(50)   NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         JSONB         NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    occurred_at     TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    retry_count     INTEGER       NOT NULL DEFAULT 0,
    error_message   TEXT
);
CREATE INDEX idx_outbox_pending ON outbox_messages(status, occurred_at) WHERE status = 'PENDING';
```

### 12.3 Redis Key Patterns

| Key Pattern | Value | TTL | Purpose |
|---|---|---|---|
| `balance:{accountId}` | JSON balance object | 5s | Hot balance cache |
| `idempotency:{key}` | JSON response | 24h | Idempotency store |
| `velocity:{customerId}:{window}` | integer count | TTL = window | Transfer velocity |
| `session:{sessionId}` | JWT claims | 30m | Session cache |
| `transfer-lock:{accountId}` | transferId | 10s | Optimistic debit lock |
| `risk-profile:{customerId}` | JSON risk data | 1h | Risk scoring cache |

---

## 13. Infrastructure & Deployment

### 13.1 Kubernetes Deployment (per service)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: transfer-service
  namespace: banking
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
      - name: transfer-service
        image: bank/transfer-service:1.0.0
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes,prod"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: transfer-db-secret
              key: password
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8082
          initialDelaySeconds: 20
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8082
          initialDelaySeconds: 60
          periodSeconds: 30
        lifecycle:
          preStop:
            exec:
              command: ["sleep", "15"]   # drain in-flight requests
```

### 13.2 Kafka Topic Configuration

```bash
# Create topics with appropriate replication and retention
kafka-topics --create \
  --topic account-events \
  --partitions 12 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \   # 30 days
  --config min.insync.replicas=2 \
  --config compression.type=lz4

kafka-topics --create \
  --topic transfer-events \
  --partitions 24 \                    # Higher parallelism for transfers
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --config min.insync.replicas=2
```

### 13.3 EventStoreDB Configuration

```yaml
# eventstore.conf
ClusterSize: 3
RunProjections: All
EnableAtomPubOverHTTP: false
StartStandardProjections: true
DisableInternalTcpTls: false
CertificateFile: /etc/eventstore/certs/node.crt
CertificatePrivateKeyFile: /etc/eventstore/certs/node.key
TrustedRootCertificatesPath: /etc/eventstore/certs/ca

# Scavenge (cleanup) schedule
ScavengeHistory: 30    # days to keep scavenge history
```

### 13.4 Spring Application Properties

```yaml
# application-prod.yml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
      isolation-level: read_committed
  data:
    redis:
      cluster:
        nodes: ${REDIS_CLUSTER_NODES}
      password: ${REDIS_PASSWORD}
      ssl:
        enabled: true

resilience4j:
  circuitbreaker:
    instances:
      risk-service:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 5
  timelimiter:
    instances:
      risk-service:
        timeoutDuration: 50ms   # hard SLA for sync risk scoring

management:
  tracing:
    sampling:
      probability: 0.1   # 10% sampling in prod; 100% for errors
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT}
```

---

## 14. Testing Strategy

### 14.1 Test Pyramid

```
                    ┌───────────────┐
                    │   E2E Tests   │  5%  — full platform, production-like
                    ├───────────────┤
                    │ Integration   │  20% — Spring context, Kafka, DB (TestContainers)
                    ├───────────────┤
                    │  Component    │  30% — service slice tests
                    ├───────────────┤
                    │  Unit Tests   │  45% — domain model, business logic
                    └───────────────┘
```

### 14.2 Domain Unit Tests

```java
class AccountAggregateTest {

    @Test
    void should_open_account_and_emit_opened_event() {
        OpenAccountCommand cmd = new OpenAccountCommand("CUST-001", CHECKING, EUR, "PROD-001");
        AccountAggregate account = AccountAggregate.open(cmd);

        assertThat(account.getUncommittedEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(AccountOpenedEvent.class)
            .extracting("customerId", "currency")
            .containsExactly("CUST-001", EUR);
    }

    @Test
    void should_reject_debit_when_insufficient_funds() {
        AccountAggregate account = loadActiveAccount(Money.of(100, EUR));

        assertThatThrownBy(() -> account.debit(Money.of(200, EUR), "TRF-001"))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("100.00");
    }

    @Test
    void should_enforce_state_machine_frozen_account_cannot_debit() {
        AccountAggregate account = loadFrozenAccount();

        assertThatThrownBy(() -> account.debit(Money.of(10, EUR), "TRF-001"))
            .isInstanceOf(AccountFrozenException.class);
    }
}
```

### 14.3 Integration Tests with TestContainers

```java
@SpringBootTest
@Testcontainers
class TransferSagaIntegrationTest {

    @Container
    static EventStoreDBContainer eventStore = new EventStoreDBContainer("eventstore/eventstore:24.x");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void should_complete_internal_transfer_end_to_end() {
        // Arrange
        String sourceId = createActiveAccountWithBalance(Money.of(1000, EUR));
        String destId = createActiveAccountWithBalance(Money.of(0, EUR));

        // Act
        String transferId = transferService.initiate(
            new InitiateTransferCommand(sourceId, destId, Money.of(250, EUR), EUR));

        // Assert — wait for saga completion
        await().atMost(Duration.ofSeconds(10)).until(() ->
            transferQueryService.getStatus(transferId) == TransferStatus.COMPLETED);

        AccountBalance sourceBal = accountQueryService.getBalance(sourceId);
        AccountBalance destBal = accountQueryService.getBalance(destId);

        assertThat(sourceBal.available()).isEqualTo(Money.of(750, EUR));
        assertThat(destBal.current()).isEqualTo(Money.of(250, EUR));
    }
}
```

### 14.4 Contract Tests (Pact)

Between Transfer Service (consumer) and Risk Service (provider):

```java
@ExtendWith(PactConsumerTestExt.class)
class TransferServiceRiskContractTest {

    @Pact(consumer = "transfer-service", provider = "risk-service")
    RequestResponsePact scoreTransactionPact(PactDslWithProvider builder) {
        return builder
            .given("risk service is available")
            .uponReceiving("score a low-risk transaction")
            .path("/api/v1/risk/score")
            .method("POST")
            .body(/* request body */)
            .willRespondWith()
            .status(200)
            .body(new PactDslJsonBody()
                .integerType("riskScore", 120)
                .stringType("riskLevel", "LOW")
                .stringType("decision", "APPROVE"))
            .toPact();
    }
}
```

---

## 15. Observability & Monitoring

### 15.1 Metrics (Micrometer → Prometheus → Grafana)

Key custom metrics to instrument:

```java
// In TransferService:
meterRegistry.counter("transfers.initiated", "rail", transfer.rail().name()).increment();
meterRegistry.counter("transfers.completed", "rail", transfer.rail().name()).increment();
meterRegistry.counter("transfers.failed", "reason", failure.reason().name()).increment();

meterRegistry.timer("transfers.duration")
    .record(Duration.between(transfer.initiatedAt(), Instant.now()));

meterRegistry.gauge("transfers.saga.inflight", sagaRepository, SagaRepository::countActive);

// Risk scoring SLA tracking
meterRegistry.timer("risk.scoring.duration",
    Tags.of("decision", result.decision().name()))
    .record(Duration.ofMillis(inferenceMs));
```

### 15.2 Key Dashboards

**Transfer Operations Dashboard:**
- Transfer throughput (TPS) by rail
- Transfer success/failure rate
- P50/P95/P99 transfer duration
- Active sagas count
- Failed saga backlog

**Risk Engine Dashboard:**
- Risk scoring latency (must stay < 50ms P99)
- Score distribution histogram
- Block/approve/review ratios
- Open fraud cases by status

**Infrastructure Dashboard:**
- Kafka consumer lag per topic/group
- EventStoreDB write/read throughput
- PostgreSQL query performance
- Redis hit rate and latency
- JVM virtual thread pool metrics

### 15.3 Distributed Tracing

Every request carries `X-Trace-ID` (W3C TraceContext). Trace spans cross service boundaries via Kafka headers and HTTP headers. Sampled traces shipped to Jaeger/Tempo via OTLP.

Critical trace attributes to add:

```java
Span.current()
    .setAttribute("transfer.id", transferId)
    .setAttribute("transfer.rail", rail.name())
    .setAttribute("account.source", sourceAccountId)
    .setAttribute("risk.score", riskScore)
    .setAttribute("customer.tier", customerTier);
```

### 15.4 Alerting Rules (Prometheus)

```yaml
groups:
- name: banking-critical
  rules:
  - alert: TransferSuccessRateLow
    expr: rate(transfers_completed_total[5m]) / rate(transfers_initiated_total[5m]) < 0.95
    for: 2m
    labels:
      severity: critical

  - alert: RiskScoringLatencyHigh
    expr: histogram_quantile(0.99, risk_scoring_duration_seconds_bucket) > 0.05
    for: 1m
    labels:
      severity: warning

  - alert: KafkaConsumerLagHigh
    expr: kafka_consumer_group_lag{topic="transfer-events"} > 1000
    for: 5m
    labels:
      severity: warning

  - alert: SagaDeadLetterQueueGrowing
    expr: increase(transfer_saga_dead_letter_total[10m]) > 0
    for: 0m
    labels:
      severity: critical
```

---

## 16. Implementation Roadmap

### Phase 1 — Foundation (Weeks 1–4)

- [ ] Repository and module structure setup (multi-module Maven)
- [ ] EventStoreDB + Kafka + PostgreSQL + Redis local dev environment (Docker Compose)
- [ ] `AggregateRoot` base class + `EventSourcedRepository` interface
- [ ] `AccountAggregate` — full state machine + events + unit tests
- [ ] Account Service: command side (open, freeze, unfreeze, close)
- [ ] Account Service: query side (projector + read model)
- [ ] Outbox pattern implementation + Kafka publisher
- [ ] Keycloak integration + Spring Security setup

### Phase 2 — Transfer Core (Weeks 5–8)

- [ ] `TransferAggregate` + saga state machine
- [ ] Transfer validation (limits, account status, AML name check)
- [ ] `TransferSagaOrchestrator` — internal transfer flow
- [ ] `LedgerService` — double-entry bookkeeping
- [ ] Balance projection + Redis cache
- [ ] Idempotency middleware
- [ ] Optimistic concurrency handling + retry logic
- [ ] Transfer API endpoints + WebSocket notifications

### Phase 3 — Risk Engine (Weeks 9–11)

- [ ] Rule-based risk signals (velocity, amount, geo, time-of-day)
- [ ] Risk scoring API (synchronous, 50ms SLA with circuit breaker)
- [ ] Risk profile aggregate + behavioral storage
- [ ] Fraud case management (open, review, resolve)
- [ ] Transfer saga integration with risk scoring
- [ ] Auto-freeze on critical risk events

### Phase 4 — External Rails & Intelligence (Weeks 12–15)

- [ ] SEPA payment rail adapter
- [ ] SWIFT payment rail adapter
- [ ] FX rate service integration
- [ ] Transaction enrichment Kafka Streams pipeline
- [ ] Dynamic limit calculation service
- [ ] ML model inference client + score blending
- [ ] Statement generation service

### Phase 5 — Hardening & Production (Weeks 16–18)

- [ ] Full integration test suite (TestContainers)
- [ ] Contract tests (Pact)
- [ ] Performance testing (Gatling — target 10,000 TPS)
- [ ] Chaos engineering (Chaos Monkey for Spring)
- [ ] Kubernetes deployment manifests + Helm charts
- [ ] Observability stack (Prometheus + Grafana + Jaeger)
- [ ] Security audit + penetration test
- [ ] Runbook documentation

---

## 17. LLM Implementation Instructions

This section provides explicit guidance for an LLM implementing this specification.

### 17.1 Coding Conventions to Follow

1. **Domain purity** — Aggregates must have zero Spring/infrastructure dependencies. No `@Autowired`, no `@Repository`, no database calls inside aggregates.
2. **Immutable events** — All domain events are Java `record` types. Never a mutable class.
3. **Sealed event hierarchies** — Declare `sealed interface AccountEvent permits ...` for each aggregate's event family.
4. **No setters on aggregates** — State is only changed via `apply(event)` calls. Private fields, package-private `handle()` method.
5. **Value Objects for all domain primitives** — Never pass raw `String accountId` across domain boundaries; use `AccountId`, `Money`, `IBAN` value objects.
6. **Fail fast with domain exceptions** — Throw specific domain exceptions (`InsufficientFundsException`, `AccountFrozenException`) not generic `RuntimeException`.
7. **Explicit transaction boundaries** — `@Transactional` on application-layer command handlers only, never on domain services.

### 17.2 File Creation Order

Implement in this order to avoid dependency issues:

```
1. Shared Kernel module (Money, Currency, AggregateRoot, DomainEvent)
2. Account domain module (model → domain service → repository interface)
3. Account infrastructure module (EventStoreDB impl → outbox → Kafka)
4. Account application module (command handlers → query handlers)
5. Account API module (controllers → DTOs → mappers)
6. [Repeat steps 2–5 for Transfer, Ledger, Risk]
7. Integration / E2E tests last
```

### 17.3 Critical Implementation Details

- **EventStoreDB stream naming**: Always `{aggregateName}-{aggregateId}` in lowercase-hyphen format
- **Kafka message key**: Always use `aggregateId` as the Kafka key to ensure event ordering per aggregate
- **Consumer group naming**: `{service-name}-{bounded-context}` e.g. `ledger-service-transfer-consumer`
- **Optimistic concurrency**: Every save to EventStoreDB must specify `expectedRevision`. Never use `ANY`.
- **Correlation IDs**: Propagate `correlationId` and `causationId` through the full event chain. `causationId` = previous event's `eventId`. `correlationId` = original request trace ID.
- **Balance operations**: Never compute balance in real-time by summing ledger events. Always use the `account_read_model.balance_*` columns, updated by the ledger projector.
- **Saga compensation**: Every step in the transfer saga must have a compensation command defined before the step is implemented.
- **Health checks**: Implement both `/actuator/health/liveness` (JVM alive) and `/actuator/health/readiness` (dependencies ready — Kafka, DB, EventStoreDB).

### 17.4 Anti-Patterns to Avoid

- ❌ Do not use `@Transactional` spanning EventStoreDB and PostgreSQL writes — use the Outbox pattern instead
- ❌ Do not query the EventStoreDB from the read/query side — use PostgreSQL projections
- ❌ Do not call other bounded contexts' databases directly — use integration events or API calls
- ❌ Do not put business logic in Kafka consumers — consumers delegate to domain/application services
- ❌ Do not use Kafka transactions as a substitute for idempotent consumers — implement idempotency at the consumer level
- ❌ Do not expose internal domain events directly as API responses — map to DTOs
- ❌ Do not store money as `double` or `float` — use `BigDecimal` with `HALF_EVEN` rounding

### 17.5 Testing Requirements per Component

| Component | Minimum Test Coverage | Test Type |
|---|---|---|
| Aggregate state machines | 100% of transitions | Unit |
| Domain services | 95% branch coverage | Unit |
| Command handlers | All happy + error paths | Unit + Integration |
| Saga orchestrator | All state transitions + compensation | Integration |
| Risk signals | Each signal with boundary values | Unit |
| API controllers | All endpoints + auth scenarios | Slice (@WebMvcTest) |
| Kafka consumers | Idempotency + ordering | Integration |
| Balance projector | Balance accuracy over 100 events | Integration |

### 17.6 Configuration Reference

Minimum required environment variables per service:

```bash
# All services
SPRING_PROFILES_ACTIVE=kubernetes,prod
KAFKA_BROKERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

# Account / Transfer / Ledger services
EVENTSTORE_CONNECTION_STRING=esdb://es-1:2113,es-2:2113,es-3:2113?tls=true
DB_URL=jdbc:postgresql://postgres-read:5432/banking_read
DB_USERNAME=banking_app
DB_PASSWORD=${vault:banking/db#password}
REDIS_CLUSTER_NODES=redis-1:6379,redis-2:6379,redis-3:6379

# Transfer service additional
RISK_SERVICE_URL=http://risk-service:8084
SEPA_GATEWAY_URL=https://sepa-gateway.bank.com
SWIFT_GATEWAY_URL=https://swift-gateway.bank.com

# Security (all services)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://auth.bank.com/realms/retail
```

---

*Specification Version: 1.0 — Generated for LLM-assisted implementation*  
*Architecture: Event Sourcing + CQRS + DDD + Event-Driven*  
*Target: Java 21 · Spring Boot 3.x · EventStoreDB · Apache Kafka*
