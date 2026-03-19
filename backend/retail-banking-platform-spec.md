# Secure Retail Banking Platform — Enterprise Implementation Specification v2.0

> **For the implementing LLM:** Read this document in its entirety before producing a single line of code. It is a complete, phase-gated specification. Each phase gate has explicit acceptance criteria that must pass before the next phase begins. All canonical names — stream names, topic names, bean IDs, column names, error codes — are fixed. Do not rename them. Where this spec conflicts with v1, v2 wins.
>
> **Stack:** Java 21 · Spring Boot 3.3.x · Apache Kafka 3.7 · EventStoreDB 24 · PostgreSQL 16 · Redis 7 · Keycloak 25 · Istio 1.22
> **Patterns:** DDD · CQRS · Event Sourcing · Saga Choreography · Transactional Outbox · Outbox-CDC hybrid · Two-phase idempotency
>
> **Non-negotiable rules:**
> - Every write to EventStoreDB specifies `ExpectedRevision`. Never `ANY`.
> - Every Kafka publish goes through the Outbox. No direct `kafkaTemplate.send()` from command handlers.
> - Money is always `BigDecimal` with `HALF_EVEN` rounding. Zero tolerance for `double` or `float`.
> - Domain aggregates have zero Spring dependencies. No `@Autowired`, no `@Component`, no infrastructure imports.
> - Every saga step defines its compensating command before implementation of the forward step.
> - Sensitive fields (PAN, IBAN, account numbers) are masked in all logs, traces, and error messages.
> - RTO < 15 minutes and RPO < 1 minute are hard constraints on every design decision.

---

## Table of Contents

1. [Platform Vision & Enterprise SLOs](#1-platform-vision--enterprise-slos)
2. [Enterprise Architecture Principles](#2-enterprise-architecture-principles)
3. [Domain Model & Bounded Contexts](#3-domain-model--bounded-contexts)
4. [Repository & Module Structure](#4-repository--module-structure)
5. [Shared Kernel](#5-shared-kernel)
6. [Event Sourcing & CQRS Infrastructure](#6-event-sourcing--cqrs-infrastructure)
7. [Transactional Outbox — Canonical Implementation](#7-transactional-outbox--canonical-implementation)
8. [Domain Events Catalog & Schema Registry](#8-domain-events-catalog--schema-registry)
9. [Transfer Saga — Full State Machine & Compensations](#9-transfer-saga--full-state-machine--compensations)
10. [Idempotency — Two-Phase Protocol](#10-idempotency--two-phase-protocol)
11. [Concurrency Control & Conflict Resolution](#11-concurrency-control--conflict-resolution)
12. [Risk & Fraud Engine](#12-risk--fraud-engine)
13. [Security Architecture](#13-security-architecture)
14. [API Design Contracts](#14-api-design-contracts)
15. [Data Models & Schema Definitions](#15-data-models--schema-definitions)
16. [Infrastructure & Deployment](#16-infrastructure--deployment)
17. [Phase 1 — Shared Kernel & Account Foundation](#17-phase-1--shared-kernel--account-foundation)
18. [Phase 2 — Transfer Core & Ledger](#18-phase-2--transfer-core--ledger)
19. [Phase 3 — Risk Engine & Fraud Cases](#19-phase-3--risk-engine--fraud-cases)
20. [Phase 4 — External Rails & Enrichment](#20-phase-4--external-rails--enrichment)
21. [Phase 5 — Hardening, Observability & Load Testing](#21-phase-5--hardening-observability--load-testing)
22. [Testing Strategy](#22-testing-strategy)
23. [Observability & Alerting](#23-observability--alerting)
24. [Operational Runbooks](#24-operational-runbooks)
25. [Full System Acceptance Criteria](#25-full-system-acceptance-criteria)

---

## 1. Platform Vision & Enterprise SLOs

### 1.1 Platform Goals

A **PCI-DSS Level 1, PSD2-compliant, cloud-native retail banking platform** built for:

- Real-time domestic and international fund transfers
- Full cryptographic audit trail via event sourcing — every state change is an immutable, ordered, versioned event
- Intelligent fraud detection within the transfer critical path (≤ 50ms synchronous SLA)
- Multi-channel access: REST, WebSocket push, mobile SDK
- GDPR right-to-erasure via pseudonymisation without compromising the event log
- 99.95% availability — less than 4.4 hours downtime per year

### 1.2 Canonical SLOs — Hard Targets

All SLOs must be verified by Gatling performance tests in Phase 5. No release ships without passing SLO verification.

| Operation | p50 | p99 | p999 | Sustained TPS | Burst TPS |
|-----------|-----|-----|------|---------------|-----------|
| `POST /api/v1/transfers` (internal) | 80ms | 350ms | 800ms | 10,000 | 50,000 |
| `GET /api/v1/accounts/{id}/balance` | 5ms | 20ms | 50ms | 50,000 | 100,000 |
| Risk scoring (sync, in-saga) | 10ms | 45ms | 90ms | 15,000 | — |
| `GET /api/v1/transfers/{id}` | 3ms | 15ms | 40ms | 30,000 | — |
| Account event replay (1,000 events) | — | 200ms | 500ms | — | — |
| Kafka event → read model projection | — | 500ms | 2,000ms | 50,000 ev/s | — |
| SEPA transfer end-to-end | — | 2s | 10s | 2,000 | — |

### 1.3 Non-Functional Requirements

| Concern | Requirement |
|---------|-------------|
| Availability | 99.95% uptime |
| Consistency | Eventual consistency for read models (≤ 500ms lag); strong consistency for account debits via optimistic concurrency |
| Durability | EventStoreDB: 3-node cluster, `min.insync.replicas=2`; PostgreSQL: synchronous streaming replication |
| RTO / RPO | RTO < 15 min, RPO < 1 min |
| Data Retention | Event log: 10 years minimum; PII: GDPR erasure via pseudonymisation |
| Regulatory | PCI-DSS L1, PSD2, GDPR, AML/KYC |
| Security | Zero-trust network, mTLS all services, AES-256 at rest, TLS 1.3 in transit |

---

## 2. Enterprise Architecture Principles

### 2.1 Critical Fixes from v1

**Fix 1 — Saga pattern: choreography, not orchestration with a god-class.**
The v1 `TransferSagaOrchestrator` was a god-object that directly called both `DebitAccountCommand` and `CreditAccountCommand`. At 10,000 TPS with P99 saga duration ~350ms, this creates 3,500 concurrent sagas all going through one Spring bean. The v2 design uses **choreography-based saga** where each service reacts to events it cares about. The Transfer service owns the saga state machine but does not synchronously call other services — it publishes commands as events and waits for outcome events.

**Fix 2 — Outbox: no polling in the hot path.**
The v1 outbox used `@Scheduled(fixedDelay = 100)` polling. At 10,000 TPS this creates 100 polls/second scanning the outbox table under load. v2 uses PostgreSQL `LISTEN/NOTIFY` triggered by an `AFTER INSERT` trigger on the outbox table, waking the relay immediately. Polling at 500ms intervals serves only as a fallback for missed notifications.

**Fix 3 — EventStoreDB + PostgreSQL dual-write is still a distributed transaction.**
The v1 outbox `@Transactional` spans the outbox write but the EventStoreDB append is *outside* that transaction. If EventStoreDB append succeeds but the PostgreSQL commit fails, the aggregate is saved but no event is published. The fix: **write-ahead to PostgreSQL outbox only**; EventStoreDB is populated by the Outbox CDC relay, not directly by command handlers. EventStoreDB is the long-term immutable store, not the source of the initial write.

**Fix 4 — Idempotent consumers must handle at-least-once delivery.**
Kafka consumers will receive duplicate messages on rebalance, retry, or broker restart. Every consumer must implement idempotency at the application level using the `eventId` as the dedup key, checked in Redis (TTL = 25 hours, longer than Kafka retention for retry scenarios).

**Fix 5 — `@Retryable` on command handlers is dangerous.**
Retrying a `DebitAccountCommand` on `WrongExpectedVersionException` can cause double-debit if the first attempt partially committed. The correct pattern is to reload the aggregate, re-validate invariants, and only re-apply if the business state still warrants it — not blind retry.

**Fix 6 — Risk scoring circuit breaker must fail closed, not fail open.**
The v1 Resilience4j config opened the breaker and the fallback was implicit (allow transfer). For a banking platform, the correct fallback for risk service unavailability is **reject the transfer** with reason `RISK_SERVICE_UNAVAILABLE`. Never allow an unscored transfer to proceed.

**Fix 7 — Money arithmetic.**
`Math.min(totalScore * 10, 1000)` in the risk scorer uses `int` arithmetic which silently overflows for large signal scores. Risk score must be computed in `long` or `BigDecimal`.

### 2.2 Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Saga pattern | Choreography via Kafka | Avoids orchestrator SPOF; each service is independently deployable |
| Event store write path | Outbox → CDC → EventStoreDB | Eliminates dual-write problem between PostgreSQL and EventStoreDB |
| Read model consistency | Eventual (Kafka consumer lag) | Read models can tolerate 500ms lag; balance cache kept hot in Redis |
| Concurrency control | Optimistic via EventStoreDB expected revision | No distributed locks; WrongExpectedVersion triggers reload-and-retry with domain re-validation |
| Risk scoring | Synchronous within saga, fail-closed | Latency budget is 50ms; failure blocks transfer — never allows unscored payments |
| GDPR erasure | Pseudonymisation of PII in separate store | Event log intact for regulatory compliance; PII deleted from customer-pii-store |
| Schema evolution | Avro + Schema Registry on Kafka | Enforces backward/forward compatibility for all integration events |
| Secret management | Vault Agent Injector (sidecar) | Secrets never in env vars or Kubernetes Secrets in plaintext |

### 2.3 Data Flow: Write Path (Authoritative)

```
Client Request
    │
    ▼
Kong API Gateway (rate limit, JWT validation, WAF)
    │
    ▼
Service REST Controller
    │
    ▼
Command Handler (@Transactional on PostgreSQL only)
    │
    ├─ 1. Load aggregate from EventStoreDB (or snapshot + tail)
    ├─ 2. Execute command → aggregate emits domain events
    ├─ 3. Append domain events to EventStoreDB (expectedRevision)
    ├─ 4. Write integration events to outbox_messages (PostgreSQL, same TX as read model update)
    └─ 5. Commit
         │
         ▼
    LISTEN/NOTIFY wakes Outbox Relay
         │
         ▼
    Relay publishes to Kafka (at-least-once, idempotent producer)
         │
         ▼
    Kafka consumers update read models, trigger downstream sagas
```

### 2.4 Aggregate Lifecycle: The Correct Version

The v1 aggregate base was missing critical safeguards. The v2 base adds:
- Version conflict detection before applying events
- Event encryption hook for sensitive event payloads
- Snapshot awareness (version cursor)
- Audit metadata on every event (correlation chain)

---

## 3. Domain Model & Bounded Contexts

### 3.1 Bounded Context Map

```
┌─────────────────────────────────────────────────────────────────┐
│                    BANKING PLATFORM DOMAIN                      │
│                                                                 │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────────────┐  │
│  │  IDENTITY   │   │   ACCOUNT   │   │  TRANSFER &         │  │
│  │  & ACCESS   │──▶│  MANAGEMENT │──▶│  PAYMENT RAILS      │  │
│  │  [Keycloak] │   │  [Core BC]  │   │  [Core BC]          │  │
│  └─────────────┘   └─────────────┘   └─────────────────────┘  │
│         │                │                      │              │
│         │                ▼                      ▼              │
│         │       ┌─────────────────┐   ┌─────────────────────┐ │
│         │       │  LEDGER &       │   │  RISK & FRAUD       │ │
│         └──────▶│  BALANCE        │   │  ENGINE             │ │
│                 │  [Core BC]      │   │  [Supporting BC]    │ │
│                 └─────────────────┘   └─────────────────────┘ │
│                          │                      │              │
│                          ▼                      ▼              │
│                 ┌─────────────────┐   ┌─────────────────────┐ │
│                 │  NOTIFICATION   │   │  AUDIT &            │ │
│                 │  & ALERTS       │   │  COMPLIANCE         │ │
│                 │  [Support BC]   │   │  [Support BC]       │ │
│                 └─────────────────┘   └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Integration style between contexts:** Published integration events via Kafka. No direct database cross-context queries. No synchronous HTTP calls except risk scoring (with circuit breaker, fail-closed fallback).

### 3.2 Aggregate Inventory

| Context | Aggregate | Stream Pattern | Snapshot Threshold |
|---------|-----------|----------------|-------------------|
| Account Management | `CustomerAggregate` | `customer-{customerId}` | 200 events |
| Account Management | `AccountAggregate` | `account-{accountId}` | 500 events |
| Transfer | `TransferAggregate` | `transfer-{transferId}` | N/A (short-lived) |
| Transfer | `TransferLimitAggregate` | `transfer-limit-{customerId}` | 100 events |
| Ledger | `LedgerEntryAggregate` | `ledger-{entryId}` | N/A (immutable after creation) |
| Risk | `RiskProfileAggregate` | `risk-profile-{customerId}` | 500 events |
| Risk | `FraudCaseAggregate` | `fraud-case-{caseId}` | N/A (short lifecycle) |

### 3.3 Value Objects (Shared Kernel — all are Java Records)

```java
// shared-kernel module
public record AccountId(String value) {
    public AccountId { Objects.requireNonNull(value); if (!value.startsWith("ACC-")) throw new InvalidAccountIdException(value); }
}
public record CustomerId(String value) { ... }
public record TransferId(String value) { ... }
public record Money(BigDecimal amount, Currency currency) {
    public Money { Objects.requireNonNull(amount); Objects.requireNonNull(currency);
                   if (amount.scale() > 4) throw new IllegalArgumentException("Money scale exceeds 4 decimal places"); }
    public Money add(Money other)      { assertSameCurrency(other); return new Money(amount.add(other.amount), currency); }
    public Money subtract(Money other) { assertSameCurrency(other); return new Money(amount.subtract(other.amount), currency); }
    public Money multiply(BigDecimal factor) { return new Money(amount.multiply(factor).setScale(4, HALF_EVEN), currency); }
    public boolean isNegative()        { return amount.compareTo(BigDecimal.ZERO) < 0; }
    public boolean isGreaterThan(Money other) { assertSameCurrency(other); return amount.compareTo(other.amount) > 0; }
    public static Money zero(Currency currency) { return new Money(BigDecimal.ZERO.setScale(4), currency); }
}
public record IBAN(String value) {
    public IBAN { IBANValidator.validate(value); } // Modulo-97 validation
    public String masked() { return value.substring(0, 4) + "****" + value.substring(value.length() - 4); }
}
public record CorrelationId(String value) { ... }
public record CausationId(String value)   { ... }
```

---

## 4. Repository & Module Structure

### 4.1 Maven Multi-Module Layout

```
banking-platform/
├── pom.xml                           # Parent POM — dependency management
├── shared-kernel/                    # Zero Spring, zero infrastructure deps
│   ├── src/main/java/com/bank/shared/
│   │   ├── domain/
│   │   │   ├── AggregateRoot.java
│   │   │   ├── DomainEvent.java
│   │   │   ├── DomainException.java
│   │   │   ├── Command.java
│   │   │   └── valueobject/          # Money, IBAN, AccountId, etc.
│   │   └── event/
│   │       ├── EventMetadata.java    # correlationId, causationId, traceId
│   │       └── EventEnvelope.java    # wire format for Kafka
├── account-service/
│   ├── src/main/java/com/bank/account/
│   │   ├── domain/                   # Pure domain — no Spring
│   │   │   ├── model/                # AccountAggregate, CustomerAggregate
│   │   │   ├── event/                # Sealed AccountEvent hierarchy
│   │   │   ├── command/              # OpenAccountCommand, etc.
│   │   │   ├── service/              # AccountOpeningService (domain service)
│   │   │   ├── repository/           # Repository interfaces (ports)
│   │   │   └── exception/            # AccountFrozenException, etc.
│   │   ├── application/              # Use cases — Spring @Service allowed here
│   │   │   ├── command/              # OpenAccountCommandHandler
│   │   │   └── query/                # GetAccountQueryHandler
│   │   ├── infrastructure/           # Adapters — Spring @Component allowed
│   │   │   ├── persistence/
│   │   │   │   ├── EventStoreDBAccountRepository.java
│   │   │   │   ├── AccountSnapshotRepository.java
│   │   │   │   └── OutboxRepository.java
│   │   │   ├── messaging/
│   │   │   │   ├── AccountEventProjector.java   # Kafka consumer → read model
│   │   │   │   └── OutboxRelay.java
│   │   │   ├── readmodel/
│   │   │   │   └── AccountReadModelRepository.java
│   │   │   └── config/
│   │   │       ├── KafkaConfig.java
│   │   │       ├── EventStoreConfig.java
│   │   │       └── SecurityConfig.java
│   │   └── api/
│   │       ├── command/              # POST controllers
│   │       ├── query/                # GET controllers
│   │       └── dto/                  # Request/Response DTOs (never domain objects)
├── transfer-service/                 # Same structure
├── ledger-service/                   # Same structure
├── risk-service/                     # Same structure
├── notification-service/             # Same structure
└── infra/
    ├── docker-compose.yml
    ├── docker-compose.dev.yml        # Hot reload with Spring DevTools
    ├── k8s/
    │   ├── helm/
    │   │   └── banking-platform/
    │   └── kustomize/
    │       ├── base/
    │       ├── staging/
    │       └── production/
    ├── kafka/
    │   └── topics.json
    └── monitoring/
        ├── prometheus/
        ├── grafana/dashboards/
        └── alerts/
```

### 4.2 Parent POM — Key Dependency Management

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
</parent>

<properties>
    <java.version>21</java.version>
    <eventstore.version>5.3.1</eventstore.version>
    <resilience4j.version>2.2.0</resilience4j.version>
    <avro.version>1.11.3</avro.version>
    <confluent.version>7.6.0</confluent.version>
    <testcontainers.version>1.19.8</testcontainers.version>
    <pact.version>4.6.14</pact.version>
    <gatling.version>3.11.3</gatling.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
</properties>

<dependencies>
    <!-- EventStoreDB -->
    <dependency>
        <groupId>com.eventstore</groupId>
        <artifactId>db-client-java</artifactId>
        <version>${eventstore.version}</version>
    </dependency>
    <!-- Kafka with Avro -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>io.confluent</groupId>
        <artifactId>kafka-avro-serializer</artifactId>
        <version>${confluent.version}</version>
    </dependency>
    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    <!-- Vault -->
    <dependency>
        <groupId>org.springframework.vault</groupId>
        <artifactId>spring-vault-core</artifactId>
    </dependency>
    <!-- OTEL -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>
</dependencies>
```

---

## 5. Shared Kernel

### 5.1 AggregateRoot Base (v2 — corrected)

```java
// shared-kernel — zero Spring imports
public abstract class AggregateRoot {
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private long version        = -1L;
    private long snapshotVersion = -1L;   // version at which last snapshot was taken
    private String aggregateId;

    /**
     * Apply a new event: update state AND record as uncommitted.
     * Only call from within aggregate methods (command processing).
     */
    protected final void apply(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        handle(event);
        uncommittedEvents.add(event);
        version++;
    }

    /**
     * Rehydrate from stored events (no side effects, no uncommitted recording).
     * Called by repository when loading from EventStoreDB.
     */
    public final void rehydrate(List<DomainEvent> events) {
        events.forEach(e -> {
            handle(e);
            version++;
        });
    }

    /**
     * Restore from snapshot. Repository calls this BEFORE rehydrating tail events.
     */
    public final void restoreFromSnapshot(AggregateSnapshot snapshot) {
        applySnapshot(snapshot);
        this.version         = snapshot.version();
        this.snapshotVersion = snapshot.version();
    }

    /** Subclasses implement: update internal state from event. No side effects. */
    protected abstract void handle(DomainEvent event);

    /** Subclasses implement: restore mutable state from snapshot. */
    protected void applySnapshot(AggregateSnapshot snapshot) {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " does not support snapshots");
    }

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() { uncommittedEvents.clear(); }

    public long getVersion()         { return version; }
    public long getSnapshotVersion() { return snapshotVersion; }
    public String getAggregateId()   { return aggregateId; }

    protected void setAggregateId(String id) {
        if (this.aggregateId != null) throw new IllegalStateException("AggregateId already set");
        this.aggregateId = Objects.requireNonNull(id);
    }
}
```

### 5.2 DomainEvent Base

```java
// All domain events are records implementing this sealed interface.
// The sealed hierarchy per aggregate is defined in the domain module.
public interface DomainEvent {
    String eventId();           // UUIDv4 — globally unique, used as idempotency key
    String aggregateId();
    long   version();           // aggregate version AFTER this event
    Instant occurredAt();
    String correlationId();     // original request trace ID, propagated through full chain
    String causationId();       // eventId of the event that caused this one (or commandId)
    String schemaVersion();     // "1.0" — increment on breaking changes
}
```

### 5.3 EventMetadata — Correlation Chain

```java
public record EventMetadata(
    String correlationId,   // propagated from original HTTP request X-Correlation-ID
    String causationId,     // eventId of direct cause
    String traceId,         // OpenTelemetry W3C trace ID
    String spanId,          // OpenTelemetry span ID
    String initiatedBy,     // customerId, operatorId, or "SYSTEM"
    String serviceOrigin    // "account-service", "transfer-service", etc.
) {
    public static EventMetadata fromRequest(HttpServletRequest req, String initiatedBy) {
        return new EventMetadata(
            req.getHeader("X-Correlation-ID"),
            null,  // no causation on initial command
            Span.current().getSpanContext().getTraceId(),
            Span.current().getSpanContext().getSpanId(),
            initiatedBy,
            System.getenv("SERVICE_NAME")
        );
    }

    public EventMetadata causedBy(String parentEventId) {
        return new EventMetadata(correlationId, parentEventId, traceId, spanId,
                                 initiatedBy, serviceOrigin);
    }
}
```

---

## 6. Event Sourcing & CQRS Infrastructure

### 6.1 EventSourcedRepository — v2 Canonical

```java
public interface EventSourcedRepository<T extends AggregateRoot> {
    /**
     * Persist uncommitted events. Uses expectedRevision for optimistic concurrency.
     * @param aggregate     the aggregate with uncommitted events
     * @param expectedVersion  version before any new events; -1 for new stream
     * @throws WrongExpectedVersionException if concurrent modification detected
     */
    void save(T aggregate, long expectedVersion) throws WrongExpectedVersionException;

    T    load(String aggregateId);
    Optional<T> loadOptional(String aggregateId);

    /**
     * Load only events after a given version (used after restoring snapshot).
     */
    List<DomainEvent> loadFrom(String aggregateId, long fromVersion);
}
```

```java
@Repository
public class EventStoreDBAccountRepository implements EventSourcedRepository<AccountAggregate> {

    private static final String STREAM_PREFIX = "account-";
    private static final int    MAX_READ_COUNT = 4096;

    private final EventStoreDBClient   client;
    private final DomainEventSerializer serializer;
    private final SnapshotService       snapshotService;

    @Override
    public void save(AccountAggregate aggregate, long expectedVersion) {
        if (aggregate.getUncommittedEvents().isEmpty()) return;

        String streamName = STREAM_PREFIX + aggregate.getAggregateId();
        List<EventData> eventData = aggregate.getUncommittedEvents().stream()
            .map(serializer::toEventData)
            .toList();

        ExpectedRevision revision = expectedVersion == -1L
            ? ExpectedRevision.NO_STREAM
            : ExpectedRevision.expectedRevision(expectedVersion);

        AppendToStreamOptions options = AppendToStreamOptions.get().expectedRevision(revision);

        try {
            client.appendToStream(streamName, options, eventData.iterator()).get();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof WrongExpectedVersionException wev) throw wev;
            throw new EventStoreException("Failed to save aggregate " + aggregate.getAggregateId(), ex);
        }

        // Snapshot after saving if threshold reached
        snapshotService.saveIfNeeded(aggregate);
        aggregate.markEventsAsCommitted();
    }

    @Override
    public AccountAggregate load(String accountId) {
        return loadOptional(accountId)
            .orElseThrow(() -> new AggregateNotFoundException("account", accountId));
    }

    @Override
    public Optional<AccountAggregate> loadOptional(String accountId) {
        // 1. Try loading snapshot
        Optional<AccountSnapshot> snapshot = snapshotService.findLatest(accountId);
        AccountAggregate account = new AccountAggregate();

        long startFromVersion = 0L;
        if (snapshot.isPresent()) {
            account.restoreFromSnapshot(snapshot.get());
            startFromVersion = snapshot.get().version() + 1;
        }

        // 2. Load tail events from EventStoreDB
        List<DomainEvent> tailEvents = loadFrom(accountId, startFromVersion);
        if (tailEvents.isEmpty() && snapshot.isEmpty()) return Optional.empty();

        account.rehydrate(tailEvents);
        return Optional.of(account);
    }

    @Override
    public List<DomainEvent> loadFrom(String aggregateId, long fromVersion) {
        String streamName = STREAM_PREFIX + aggregateId;
        ReadStreamOptions options = ReadStreamOptions.get()
            .fromRevision(fromVersion)
            .forwards()
            .maxCount(MAX_READ_COUNT);

        try {
            return client.readStream(streamName, options).get()
                .getEvents().stream()
                .map(serializer::fromResolvedEvent)
                .toList();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof StreamNotFoundException) return Collections.emptyList();
            throw new EventStoreException("Failed to load stream " + streamName, ex);
        }
    }
}
```

### 6.2 Snapshot Service

```java
@Service
public class SnapshotService {

    private static final Map<Class<?>, Integer> THRESHOLDS = Map.of(
        AccountAggregate.class,       500,
        RiskProfileAggregate.class,   500,
        CustomerAggregate.class,      200,
        TransferLimitAggregate.class, 100
    );

    public <T extends AggregateRoot> void saveIfNeeded(T aggregate) {
        int threshold = THRESHOLDS.getOrDefault(aggregate.getClass(), Integer.MAX_VALUE);
        long versionsSinceSnapshot = aggregate.getVersion() - aggregate.getSnapshotVersion();

        if (versionsSinceSnapshot >= threshold) {
            AggregateSnapshot snapshot = SnapshotSerializer.serialize(aggregate);
            snapshotRepository.save(snapshot);
            log.debug("Snapshot saved for {} at version {}",
                      aggregate.getAggregateId(), aggregate.getVersion());
        }
    }
}
```

### 6.3 Concurrency Conflict Resolution — Correct Pattern

**Do NOT use `@Retryable` on command handlers blindly.** The v1 approach retried `DebitAccountCommand` automatically, which risks double-debit. The correct pattern:

```java
@CommandHandler
@Transactional
public TransferResult handle(DebitAccountCommand command) {
    int attempts = 0;
    while (attempts < 3) {
        attempts++;
        AccountAggregate account = accountRepository.load(command.accountId().value());

        // Re-validate domain invariants with fresh state
        account.validateCanDebit(command.amount());  // throws InsufficientFundsException if invalid
        // If validation passes, the debit is still warranted — attempt it

        try {
            account.debit(command.amount(), command.transferId(), command.metadata());
            accountRepository.save(account, account.getVersion() - account.getUncommittedEvents().size());
            return TransferResult.debited(account.getVersion());
        } catch (WrongExpectedVersionException ex) {
            if (attempts == 3) throw new ConcurrentModificationException(
                "Account " + command.accountId() + " modified concurrently after 3 attempts", ex);
            // Exponential backoff: 20ms, 40ms
            Thread.sleep(20L * attempts);
        }
    }
    throw new IllegalStateException("unreachable");
}
```

---

## 7. Transactional Outbox — Canonical Implementation

### 7.1 Outbox Table (per service — PostgreSQL)

```sql
CREATE TABLE outbox_messages (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(100)  NOT NULL,
    aggregate_type  VARCHAR(100)  NOT NULL,
    event_id        VARCHAR(50)   NOT NULL UNIQUE,  -- domain event's eventId — dedup key
    event_type      VARCHAR(200)  NOT NULL,
    topic           VARCHAR(200)  NOT NULL,
    partition_key   VARCHAR(100)  NOT NULL,          -- Kafka partition key (aggregateId)
    payload         BYTEA         NOT NULL,          -- Avro-serialized EventEnvelope
    headers         JSONB         NOT NULL DEFAULT '{}',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     SMALLINT      NOT NULL DEFAULT 0,
    last_error      TEXT,
    dead_at         TIMESTAMPTZ
);

-- Partial index: only pending records need to be scanned by relay
CREATE INDEX idx_outbox_pending
    ON outbox_messages (created_at ASC)
    WHERE status = 'PENDING';

-- Dedup index for idempotent insert
CREATE UNIQUE INDEX idx_outbox_event_id ON outbox_messages (event_id);

-- LISTEN/NOTIFY trigger — wakes relay immediately on insert
CREATE OR REPLACE FUNCTION notify_outbox_insert() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('outbox_insert', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER outbox_after_insert
    AFTER INSERT ON outbox_messages
    FOR EACH ROW EXECUTE FUNCTION notify_outbox_insert();
```

### 7.2 Outbox Writer — In-Transaction

```java
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final EventEnvelopeSerializer serializer;

    /**
     * Write an integration event to the outbox within the caller's active transaction.
     * The transaction MUST be open when this is called.
     * Never call kafkaTemplate.send() directly from command handlers.
     */
    @Transactional(propagation = MANDATORY) // fails if no active transaction — intentional
    public void write(DomainEvent event, String topic) {
        byte[] payload = serializer.toAvro(EventEnvelope.wrap(event));

        OutboxMessage message = OutboxMessage.builder()
            .id(UUID.randomUUID())
            .aggregateId(event.aggregateId())
            .aggregateType(extractAggregateType(event))
            .eventId(event.eventId())          // UNIQUE — prevents duplicate outbox entries
            .eventType(event.getClass().getName())
            .topic(topic)
            .partitionKey(event.aggregateId()) // guarantees ordering per aggregate
            .payload(payload)
            .headers(buildKafkaHeaders(event))
            .status(OutboxStatus.PENDING)
            .build();

        outboxRepository.save(message);
        // The AFTER INSERT trigger fires pg_notify automatically
    }

    private Map<String, String> buildKafkaHeaders(DomainEvent event) {
        return Map.of(
            "eventId",        event.eventId(),
            "correlationId",  event.correlationId(),
            "causationId",    Objects.toString(event.causationId(), ""),
            "schemaVersion",  event.schemaVersion(),
            "eventType",      event.getClass().getSimpleName()
        );
    }
}
```

### 7.3 Outbox Relay — LISTEN/NOTIFY + Fallback Poll

```java
@Service
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE   = 200;
    private static final int MAX_RETRIES  = 5;
    private static final Duration FALLBACK_POLL = Duration.ofMillis(500);

    private final DataSource         dataSource;
    private final OutboxRepository   outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    /**
     * Start LISTEN/NOTIFY connection. Runs in a virtual thread.
     * Falls back to polling every 500ms if NOTIFY is missed.
     */
    @PostConstruct
    public void start() {
        Thread.ofVirtual().name("outbox-relay").start(this::relayLoop);
    }

    private void relayLoop() {
        try (Connection listenConn = dataSource.getConnection()) {
            listenConn.createStatement().execute("LISTEN outbox_insert");
            PGConnection pgConn = listenConn.unwrap(PGConnection.class);

            while (!Thread.currentThread().isInterrupted()) {
                // Block up to FALLBACK_POLL waiting for notification
                PGNotification[] notifications = pgConn.getNotifications(
                    (int) FALLBACK_POLL.toMillis());

                // Whether notified or timed out, process the batch
                processBatch();
            }
        } catch (SQLException ex) {
            log.error("Outbox relay LISTEN connection failed — restarting in 5s", ex);
            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
            start(); // restart
        }
    }

    @Transactional
    public void processBatch() {
        // FOR UPDATE SKIP LOCKED prevents concurrent relay instances from double-publishing
        List<OutboxMessage> batch = outboxRepository.lockPendingBatch(BATCH_SIZE);
        if (batch.isEmpty()) return;

        List<UUID> successIds = new ArrayList<>();
        List<UUID> failIds    = new ArrayList<>();

        for (OutboxMessage msg : batch) {
            try {
                kafkaTemplate.send(
                    msg.topic(),
                    msg.partitionKey(),
                    msg.payload()
                ).get(5, TimeUnit.SECONDS); // synchronous wait for broker ack
                successIds.add(msg.id());
            } catch (Exception ex) {
                log.warn("Outbox publish failed for event {} (attempt {}): {}",
                         msg.eventId(), msg.retryCount() + 1, ex.getMessage());
                failIds.add(msg.id());
            }
        }

        if (!successIds.isEmpty()) outboxRepository.markPublished(successIds);
        if (!failIds.isEmpty())    outboxRepository.incrementRetryCount(failIds);

        // Mark as dead after MAX_RETRIES
        outboxRepository.markDeadIfExceeded(MAX_RETRIES);
    }
}
```

```sql
-- lockPendingBatch — used by relay
SELECT * FROM outbox_messages
WHERE  status = 'PENDING'
ORDER  BY created_at ASC
LIMIT  :batchSize
FOR UPDATE SKIP LOCKED;
```

### 7.4 Canonical Command Handler Pattern (with Outbox)

```java
@Service
@RequiredArgsConstructor
public class OpenAccountCommandHandler {

    private final EventStoreDBAccountRepository accountRepository;
    private final OutboxWriter                  outboxWriter;
    private final AccountReadModelRepository    readModelRepository;

    /**
     * @Transactional applies ONLY to PostgreSQL (read model + outbox).
     * EventStoreDB append is NOT transactional with PostgreSQL.
     * Write order: 1) EventStoreDB 2) PostgreSQL read model + outbox (in one TX)
     * If EventStoreDB succeeds but PostgreSQL TX rolls back:
     *   — EventStoreDB has the event (it's the source of truth)
     *   — A reconciliation job re-derives the read model from EventStoreDB on startup
     * If PostgreSQL TX succeeds but EventStoreDB fails:
     *   — Retry EventStoreDB append (safe — idempotent with same expectedRevision)
     */
    public String handle(OpenAccountCommand command) {
        // Step 1: validate pre-conditions (outside transaction — read-only)
        CustomerReadModel customer = customerQueryService.getCustomer(command.customerId());
        if (!customer.isKycApproved()) {
            throw new KycNotApprovedException(command.customerId());
        }

        // Step 2: execute domain logic — no transaction yet
        AccountAggregate account = AccountAggregate.open(command);

        // Step 3: persist to EventStoreDB (NOT in PostgreSQL transaction)
        accountRepository.save(account, -1L); // -1 = new stream

        // Step 4: PostgreSQL transaction — read model + outbox atomically
        persistReadModelAndOutbox(account);

        return account.getAggregateId();
    }

    @Transactional  // PostgreSQL only
    protected void persistReadModelAndOutbox(AccountAggregate account) {
        // Update read model
        account.getUncommittedEvents().forEach(event -> {
            if (event instanceof AccountOpenedEvent e) {
                readModelRepository.save(AccountReadModel.from(e));
            }
        });
        // Write to outbox — same transaction as read model
        account.getUncommittedEvents().forEach(event ->
            outboxWriter.write(event, KafkaTopics.ACCOUNT_EVENTS)
        );
        account.markEventsAsCommitted();
    }
}
```

---

## 8. Domain Events Catalog & Schema Registry

### 8.1 Event Design Rules

- Events are **past-tense immutable facts** encoded as Java `record` types
- All events carry: `eventId`, `aggregateId`, `version`, `occurredAt`, `correlationId`, `causationId`, `schemaVersion`
- Events are serialized as **Avro** for Kafka (schema evolution enforcement via Confluent Schema Registry)
- Events are serialized as **JSON** for EventStoreDB (human-readable stream browsing)
- Sensitive fields (`iban`, `accountNumber`, `pan`) are **never logged in plaintext** — masked or encrypted before publication
- Schema changes follow semantic versioning; breaking changes increment the major schema version and require a migration topic

### 8.2 Account Events

```java
public sealed interface AccountEvent extends DomainEvent
    permits AccountOpenedEvent, AccountActivatedEvent, AccountFrozenEvent,
            AccountUnfrozenEvent, AccountClosingInitiatedEvent, AccountClosedEvent,
            AccountBlockedEvent, AccountLimitChangedEvent {}

public record AccountOpenedEvent(
    String  eventId,
    String  accountId,
    String  customerId,
    String  iban,            // will be masked in Kafka payload — see Section 13.3
    String  productCode,
    AccountType type,
    Currency currency,
    String  correlationId,
    String  causationId,
    long    version,
    Instant occurredAt,
    String  schemaVersion
) implements AccountEvent {
    public static final String SCHEMA_VERSION = "1.0";
}

public record AccountFrozenEvent(
    String      eventId,
    String      accountId,
    FreezeReason reason,   // enum: FRAUD_DETECTED, AML_HOLD, CUSTOMER_REQUEST, REGULATORY_ORDER
    String      initiatedBy,
    String      notes,
    String      correlationId,
    String      causationId,
    long        version,
    Instant     occurredAt,
    String      schemaVersion
) implements AccountEvent {}
```

### 8.3 Transfer Events (full saga lifecycle)

```java
public sealed interface TransferEvent extends DomainEvent
    permits TransferInitiatedEvent, TransferRiskScoredEvent, TransferValidatedEvent,
            TransferDebitReservedEvent, TransferDebitedEvent, TransferCreditedEvent,
            TransferSettlingEvent, TransferCompletedEvent,
            TransferBlockedEvent, TransferValidationFailedEvent,
            TransferDebitFailedEvent, TransferCreditFailedEvent,
            TransferReversalInitiatedEvent, TransferReversedEvent {}

public record TransferInitiatedEvent(
    String      eventId,
    String      transferId,
    String      sourceAccountId,
    String      destinationAccountId,  // null for external
    String      destinationIban,       // null for internal
    String      destinationBic,        // SWIFT/BIC for external
    Money       amount,
    Money       fxAmount,             // null if no FX conversion
    PaymentRail rail,                  // INTERNAL / SEPA_CREDIT / SWIFT / RTP
    String      remittanceInfo,
    TransferType type,
    String      initiatedBy,
    String      correlationId,
    String      causationId,
    long        version,
    Instant     occurredAt,
    String      schemaVersion
) implements TransferEvent {}

public record TransferRiskScoredEvent(
    String         eventId,
    String         transferId,
    int            riskScore,          // 0–1000, long internally then cast after clamping
    RiskLevel      riskLevel,
    RiskDecision   decision,           // APPROVE / REVIEW / BLOCK
    List<String>   triggeredSignalIds,
    long           scoringDurationMs,
    String         correlationId,
    String         causationId,
    long           version,
    Instant        occurredAt,
    String         schemaVersion
) implements TransferEvent {}

public record TransferReversedEvent(
    String         eventId,
    String         transferId,
    String         originalDebitEntryId,  // ledger entry to reverse
    Money          reversedAmount,
    ReversalReason reason,
    String         correlationId,
    String         causationId,
    long           version,
    Instant        occurredAt,
    String         schemaVersion
) implements TransferEvent {}
```

### 8.4 Kafka Topic Registry (Canonical)

```java
// Never hardcode topic strings — always use this class
public final class KafkaTopics {
    public static final String ACCOUNT_EVENTS     = "banking.account.events.v1";
    public static final String TRANSFER_EVENTS    = "banking.transfer.events.v1";
    public static final String LEDGER_EVENTS      = "banking.ledger.events.v1";
    public static final String FRAUD_EVENTS       = "banking.fraud.events.v1";
    public static final String NOTIFICATION_CMDS  = "banking.notification.commands.v1";
    public static final String ENRICHED_TX        = "banking.transactions.enriched.v1";
    public static final String TRANSFER_DLQ       = "banking.transfer.dlq.v1";
    public static final String OUTBOX_DLQ         = "banking.outbox.dlq.v1";

    private KafkaTopics() {}
}

// Consumer group IDs — versioned so offset reset on schema changes is safe
public final class ConsumerGroups {
    public static final String ACCOUNT_PROJECTOR     = "account-service.projector.v1";
    public static final String LEDGER_PROJECTOR      = "ledger-service.projector.v1";
    public static final String TRANSFER_PROJECTOR    = "transfer-service.projector.v1";
    public static final String RISK_ENRICHER         = "risk-service.enricher.v1";
    public static final String NOTIFICATION_SENDER   = "notification-service.sender.v1";
    public static final String FRAUD_AUTO_FREEZE     = "account-service.fraud-freeze.v1";

    private ConsumerGroups() {}
}
```

---

## 9. Transfer Saga — Full State Machine & Compensations

### 9.1 Saga Design Principles

The v2 saga is **choreography-based**, not orchestrator-based. Every service publishes events about what it did; other services react. The `TransferAggregate` owns the saga's state machine but does **not** synchronously call other services.

Compensating commands must be defined before forward steps. This table is the implementation contract:

| Forward Step | Forward Event | Compensation Trigger | Compensation Command | Compensation Event |
|---|---|---|---|---|
| Initiate transfer | `TransferInitiatedEvent` | Risk BLOCK | `BlockTransferCommand` | `TransferBlockedEvent` |
| Score risk | `TransferRiskScoredEvent` | Score ≥ threshold | `BlockTransferCommand` | `TransferBlockedEvent` |
| Validate limits/status | `TransferValidatedEvent` | Validation fail | `FailTransferCommand` | `TransferValidationFailedEvent` |
| Reserve debit | `TransferDebitReservedEvent` | Reservation fail | `FailTransferCommand` | `TransferDebitFailedEvent` |
| Execute debit | `TransferDebitedEvent` | None — debit succeeded | N/A (credit may still fail) | — |
| Execute credit | `TransferCreditedEvent` | Credit fail | `ReverseDebitCommand` | `TransferReversalInitiatedEvent` → `TransferReversedEvent` |
| Submit to rail | `TransferSettlingEvent` | Rail rejection | `ReverseDebitCommand` | `TransferReversalInitiatedEvent` → `TransferReversedEvent` |
| Complete | `TransferCompletedEvent` | N/A — terminal | — | — |

### 9.2 Transfer Aggregate State Machine

```java
public class TransferAggregate extends AggregateRoot {

    // State
    private String      transferId;
    private TransferStatus status;
    private String      sourceAccountId;
    private String      destinationAccountId;
    private String      destinationIban;
    private Money       amount;
    private PaymentRail rail;
    private int         riskScore;
    private boolean     debitExecuted;   // guards compensation logic

    // ── Command methods ──────────────────────────────────────────────────────

    public static TransferAggregate initiate(InitiateTransferCommand cmd) {
        TransferAggregate t = new TransferAggregate();
        t.apply(new TransferInitiatedEvent(
            UUID.randomUUID().toString(),
            cmd.transferId().value(),
            cmd.sourceAccountId().value(),
            cmd.destinationAccountId() != null ? cmd.destinationAccountId().value() : null,
            cmd.destinationIban() != null ? cmd.destinationIban().value() : null,
            cmd.destinationBic(),
            cmd.amount(),
            null,            // fxAmount — resolved later
            cmd.rail(),
            cmd.remittanceInfo(),
            cmd.type(),
            cmd.initiatedBy(),
            cmd.metadata().correlationId(),
            cmd.metadata().causationId(),
            0L, Instant.now(), TransferInitiatedEvent.SCHEMA_VERSION
        ));
        return t;
    }

    public void applyRiskScore(int score, RiskLevel level, RiskDecision decision,
                                List<String> signals, long durationMs, EventMetadata meta) {
        assertStatus(TransferStatus.INITIATED);
        apply(new TransferRiskScoredEvent(UUID.randomUUID().toString(), transferId,
            score, level, decision, signals, durationMs,
            meta.correlationId(), meta.causationId(),
            version + 1, Instant.now(), TransferRiskScoredEvent.SCHEMA_VERSION));

        if (decision == RiskDecision.BLOCK) {
            apply(new TransferBlockedEvent(UUID.randomUUID().toString(), transferId,
                "Risk score " + score + " exceeded threshold",
                meta.correlationId(), meta.causationId(),
                version + 1, Instant.now(), TransferBlockedEvent.SCHEMA_VERSION));
        }
    }

    public void recordDebitExecuted(String ledgerEntryId, EventMetadata meta) {
        assertStatus(TransferStatus.DEBIT_RESERVED);
        apply(new TransferDebitedEvent(UUID.randomUUID().toString(), transferId,
            ledgerEntryId, amount, meta.correlationId(), meta.causationId(),
            version + 1, Instant.now(), TransferDebitedEvent.SCHEMA_VERSION));
    }

    public void initiateReversal(ReversalReason reason, String originalEntryId, EventMetadata meta) {
        if (!debitExecuted) throw new IllegalStateException(
            "Cannot reverse transfer " + transferId + " — debit was not executed");
        assertStatusIn(TransferStatus.DEBITED, TransferStatus.CREDIT_FAILED,
                       TransferStatus.SETTLING);
        apply(new TransferReversalInitiatedEvent(UUID.randomUUID().toString(), transferId,
            originalEntryId, amount, reason,
            meta.correlationId(), meta.causationId(),
            version + 1, Instant.now(), TransferReversalInitiatedEvent.SCHEMA_VERSION));
    }

    // ── Event handlers ───────────────────────────────────────────────────────
    // These methods only update state — NO side effects, NO external calls

    @Override
    protected void handle(DomainEvent event) {
        switch (event) {
            case TransferInitiatedEvent e -> {
                setAggregateId(e.transferId());
                this.transferId            = e.transferId();
                this.status               = TransferStatus.INITIATED;
                this.sourceAccountId      = e.sourceAccountId();
                this.destinationAccountId = e.destinationAccountId();
                this.destinationIban      = e.destinationIban();
                this.amount               = e.amount();
                this.rail                 = e.rail();
            }
            case TransferRiskScoredEvent e -> this.riskScore = e.riskScore();
            case TransferBlockedEvent e    -> this.status = TransferStatus.BLOCKED;
            case TransferValidatedEvent e  -> this.status = TransferStatus.VALIDATED;
            case TransferDebitReservedEvent e -> this.status = TransferStatus.DEBIT_RESERVED;
            case TransferDebitedEvent e    -> {
                this.status        = TransferStatus.DEBITED;
                this.debitExecuted = true;
            }
            case TransferCreditedEvent e   -> this.status = TransferStatus.CREDITED;
            case TransferSettlingEvent e   -> this.status = TransferStatus.SETTLING;
            case TransferCompletedEvent e  -> this.status = TransferStatus.COMPLETED;
            case TransferReversalInitiatedEvent e -> this.status = TransferStatus.REVERSING;
            case TransferReversedEvent e   -> this.status = TransferStatus.REVERSED;
            case TransferValidationFailedEvent e -> this.status = TransferStatus.VALIDATION_FAILED;
            case TransferDebitFailedEvent e      -> this.status = TransferStatus.DEBIT_FAILED;
            case TransferCreditFailedEvent e     -> this.status = TransferStatus.CREDIT_FAILED;
            default -> log.warn("Unhandled event type in TransferAggregate: {}",
                                event.getClass().getSimpleName());
        }
    }

    private void assertStatus(TransferStatus expected) {
        if (this.status != expected) throw new InvalidTransferStateException(
            transferId, expected, this.status);
    }
}
```

### 9.3 Choreography Event Handlers (per service)

```java
// In transfer-service: consumes ledger events to advance saga state
@KafkaListener(topics = KafkaTopics.LEDGER_EVENTS, groupId = ConsumerGroups.TRANSFER_PROJECTOR)
public void onLedgerEvent(ConsumerRecord<String, byte[]> record) {
    DomainEvent event = deserializer.deserialize(record.value());

    // Idempotency check — see Section 10
    if (idempotencyStore.isDuplicate(event.eventId())) return;

    if (event instanceof LedgerDebitedEvent e) {
        // Advance transfer saga: debit confirmed → trigger credit
        TransferAggregate transfer = transferRepository.load(e.transferId());
        transfer.recordDebitExecuted(e.entryId(), metadataFrom(e));
        transferRepository.save(transfer, transfer.getVersion() - 1);

        // The TransferDebitedEvent is now in the outbox → relay → Kafka
        // Account service consumes TransferDebitedEvent and executes credit
    }

    if (event instanceof LedgerCreditedEvent e) {
        TransferAggregate transfer = transferRepository.load(e.transferId());
        transfer.recordCreditExecuted(e.entryId(), metadataFrom(e));
        transferRepository.save(transfer, transfer.getVersion() - 1);
        // TransferCreditedEvent → Kafka → completes saga
    }

    idempotencyStore.markProcessed(event.eventId());
}
```

### 9.4 Dead-Letter Queue Handling

All Kafka consumers must configure a DLQ:

```java
@Bean
public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, byte[]> ops) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(ops,
        (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition()));

    FixedBackOff backOff = new FixedBackOff(1000L, 3L); // 3 retries, 1s apart

    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    // Non-retryable exceptions — go straight to DLQ
    handler.addNotRetryableExceptions(
        SerializationException.class,
        InvalidEventSchemaException.class,
        AggregateNotFoundException.class
    );
    return handler;
}
```

---

## 10. Idempotency — Two-Phase Protocol

### 10.1 Problem Statement

Kafka consumers receive messages at-least-once. The v1 spec did not define how consumers deduplicate. The v2 requires a two-phase idempotency protocol at every consumer:

**Phase 1 — Check:** Before processing, check Redis for the `eventId`. If present, skip and return immediately.
**Phase 2 — Mark:** After successful processing, write `eventId` to Redis with TTL = 25 hours.

Redis TTL must exceed the Kafka consumer retry window (3 retries × 1 second + broker `retention.ms` for the retry topic).

### 10.2 Idempotency Store

```java
@Component
public class KafkaIdempotencyStore {

    private static final Duration TTL = Duration.ofHours(25);
    private static final String   PREFIX = "kafka-idem:";

    private final RedisTemplate<String, String> redis;

    /**
     * Returns true if this eventId has already been processed.
     * Thread-safe: uses SET NX (atomic check-and-set).
     */
    public boolean checkAndMark(String eventId) {
        String key = PREFIX + eventId;
        // SET key "1" NX PX ttlMs  — returns null if key already exists
        Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", TTL);
        return Boolean.FALSE.equals(isNew); // true = duplicate
    }
}
```

```java
// Usage in every Kafka consumer — mandatory pattern
@KafkaListener(topics = KafkaTopics.TRANSFER_EVENTS, groupId = ConsumerGroups.LEDGER_PROJECTOR)
public void project(ConsumerRecord<String, byte[]> record) {
    DomainEvent event = deserializer.deserialize(record.value());

    if (idempotencyStore.checkAndMark(event.eventId())) {
        log.debug("Duplicate event {} skipped by idempotency store", event.eventId());
        return;
    }

    // Process event — exactly-once guarantee
    projectEvent(event);
}
```

### 10.3 HTTP Idempotency (Transfers)

```java
@Around("@annotation(Idempotent)")
public Object enforceIdempotency(ProceedingJoinPoint pjp,
                                  HttpServletRequest request) throws Throwable {
    String key = request.getHeader("X-Idempotency-Key");
    if (key == null || key.isBlank()) throw new MissingIdempotencyKeyException();

    // Validate format — must be UUID v4
    try { UUID.fromString(key); } catch (IllegalArgumentException e) {
        throw new InvalidIdempotencyKeyException(key);
    }

    String cacheKey = "http-idem:" + key;

    // Phase 1: check
    String cached = redis.opsForValue().get(cacheKey);
    if (cached != null) {
        IdempotencyCache entry = objectMapper.readValue(cached, IdempotencyCache.class);
        log.info("Idempotency replay for key {}", key);
        return entry.response();
    }

    // Phase 2: mark in-flight to prevent concurrent duplicate requests
    String inFlightKey = "http-idem-inflight:" + key;
    Boolean acquired = redis.opsForValue().setIfAbsent(inFlightKey, "1", Duration.ofSeconds(30));
    if (Boolean.FALSE.equals(acquired)) throw new DuplicateRequestInFlightException(key);

    try {
        Object result = pjp.proceed();
        // Store successful response
        redis.opsForValue().set(cacheKey,
            objectMapper.writeValueAsString(new IdempotencyCache(result)),
            Duration.ofHours(24));
        return result;
    } finally {
        redis.delete(inFlightKey);
    }
}
```

---

## 11. Concurrency Control & Conflict Resolution

### 11.1 Redis Balance Lock — Correct Approach

The v1 spec suggested a `transfer-lock:{accountId}` Redis key as an "optimistic debit lock." This is incorrect — Redis locks combined with EventStoreDB optimistic concurrency creates two competing concurrency mechanisms. The v2 uses **only** EventStoreDB expected revision for concurrency.

The Redis `balance:{accountId}` cache stores the last-known balance for fast reads. It does **not** participate in write-path concurrency control.

### 11.2 Balance Reservation Model

To prevent overdraft during concurrent transfers:

```java
// AccountAggregate — balance reservation
public void reserve(Money amount, String transferId, EventMetadata meta) {
    Money available = currentBalance.subtract(reservedBalance);
    if (available.isNegative() || available.isLessThan(amount)) {
        throw new InsufficientFundsException(accountId, available, amount);
    }
    apply(new BalanceReservedEvent(UUID.randomUUID().toString(), accountId,
        transferId, amount, meta.correlationId(), meta.causationId(),
        version + 1, Instant.now(), BalanceReservedEvent.SCHEMA_VERSION));
}

public void executeDebit(String transferId, EventMetadata meta) {
    BalanceReservation reservation = reservations.stream()
        .filter(r -> r.transferId().equals(transferId))
        .findFirst()
        .orElseThrow(() -> new ReservationNotFoundException(accountId, transferId));

    apply(new AccountDebitedEvent(UUID.randomUUID().toString(), accountId,
        transferId, reservation.amount(), meta.correlationId(), meta.causationId(),
        version + 1, Instant.now(), AccountDebitedEvent.SCHEMA_VERSION));
}

// Two-step debit: reserve (immediate) → execute (after risk + validation pass)
// If transfer fails after reserve: release reservation via ReleaseReservationCommand
```

---

## 12. Risk & Fraud Engine

### 12.1 Synchronous Scoring — Fail Closed

The risk service must be called synchronously within the transfer saga's risk-scoring step. If the call fails, the transfer is blocked — not allowed to proceed.

```java
@Service
@Slf4j
public class RiskScoringClient {

    private final RestClient        restClient;
    private final CircuitBreaker    circuitBreaker;
    private final MeterRegistry     meterRegistry;

    /**
     * Score a transaction synchronously.
     * Fails CLOSED: any exception or circuit breaker open → throw RiskServiceUnavailableException.
     * The caller (transfer saga) must block the transfer on this exception.
     */
    public RiskScoreResult score(RiskScoreRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return circuitBreaker.executeSupplier(() -> {
                return restClient.post()
                    .uri("/api/v1/risk/score")
                    .body(request)
                    .retrieve()
                    .body(RiskScoreResult.class);
            });
        } catch (CallNotPermittedException ex) {
            // Circuit breaker is OPEN — fail closed
            log.error("Risk service circuit breaker OPEN — blocking transfer {}",
                      request.transferId());
            meterRegistry.counter("risk.scoring.circuit_open").increment();
            throw new RiskServiceUnavailableException(
                "Risk service unavailable — transfer blocked for safety", request.transferId());
        } catch (Exception ex) {
            log.error("Risk scoring failed for transfer {}: {}", request.transferId(), ex.getMessage());
            throw new RiskServiceUnavailableException(
                "Risk scoring failed — transfer blocked", request.transferId());
        } finally {
            sample.stop(meterRegistry.timer("risk.scoring.duration",
                Tags.of("transferId", request.transferId())));
        }
    }
}
```

```yaml
# application.yml — risk service circuit breaker
resilience4j:
  circuitbreaker:
    instances:
      risk-service:
        registerHealthIndicator: true
        slidingWindowType: TIME_BASED
        slidingWindowSize: 10           # 10-second window
        failureRateThreshold: 30        # open after 30% failures
        slowCallRateThreshold: 50       # also open on slow calls
        slowCallDurationThreshold: 45ms # 45ms = within 50ms SLA with margin
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
  timelimiter:
    instances:
      risk-service:
        timeoutDuration: 48ms           # 2ms buffer before 50ms SLA
        cancelRunningFuture: true
```

### 12.2 Risk Score Computation — Fixed

The v1 score computation used `int` arithmetic that could silently overflow. v2 computes in `long` and clamps correctly.

```java
public RiskScoreResult score(TransactionContext context) {
    List<RiskSignal> signals = signalRegistry.getApplicableSignals(context);

    // Accumulate in long to prevent integer overflow
    long rawScore = signals.stream()
        .mapToLong(s -> (long) s.evaluate(context))
        .sum();

    // Normalise to 0–1000 range
    int score = (int) Math.min(rawScore * 10L, 1000L);

    // ML model override (only when confidence is very high)
    if (mlModelEnabled) {
        MlPrediction prediction = mlClient.predict(context);
        if (prediction.confidence() >= 0.90) {
            // Blend: 30% rule-based, 70% ML
            score = (int) Math.round(score * 0.3 + prediction.normalizedScore() * 0.7);
        }
    }

    RiskLevel    level    = RiskLevel.fromScore(score);
    RiskDecision decision = decisionPolicy.decide(level, context.customerId(), context);

    List<RiskSignal> fired = signals.stream()
        .filter(s -> s.evaluate(context) > 0)
        .toList();

    return new RiskScoreResult(score, level, decision,
        fired.stream().map(RiskSignal::getSignalId).toList(),
        System.currentTimeMillis() - context.startTime());
}
```

### 12.3 Velocity Signal — Redis Pipeline Batching

At 10,000 TPS, individual Redis commands per signal check collapse performance. Use pipelining:

```java
@Component
public class VelocityRiskSignal implements RiskSignal {

    @Override
    public int evaluate(TransactionContext ctx) {
        String key = "velocity:" + ctx.customerId() + ":10m";
        Instant windowStart = ctx.timestamp().minus(10, MINUTES);

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            // Add this transaction's timestamp
            conn.zAdd(key.getBytes(), ctx.timestamp().toEpochMilli(),
                      ctx.transferId().getBytes());
            // Remove entries outside window
            conn.zRemRangeByScore(key.getBytes(), 0, windowStart.toEpochMilli());
            // Count remaining
            conn.zCard(key.getBytes());
            // Set expiry
            conn.expire(key.getBytes(), 660); // 11 minutes
            return null;
        });

        long count = ((Long) results.get(2));
        return switch ((int) Math.min(count, Integer.MAX_VALUE)) {
            case 0, 1, 2 -> 0;
            case 3, 4    -> 10;
            case 5, 6, 7 -> 25;
            default      -> 60;
        };
    }
}
```

---

## 13. Security Architecture

### 13.1 Authentication & Authorization

**Customer-facing:** Keycloak OIDC, Authorization Code Flow + PKCE. MFA required for transfers above threshold.
**Service-to-service:** Client Credentials flow. Each service has a dedicated Keycloak client with minimum scopes.
**Operator access:** Authorization Code Flow with hardware MFA (FIDO2/WebAuthn).

### 13.2 Spring Security — Complete Configuration

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(bankingJwtConverter()))
                .bearerTokenResolver(cookieBearerTokenResolver()))  // also accept httpOnly cookie
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/liveness",
                                 "/actuator/health/readiness").permitAll()
                .requestMatchers("/actuator/**").hasRole("MONITORING")
                .requestMatchers(POST, "/api/v1/transfers").hasRole("CUSTOMER")
                .requestMatchers(GET,  "/api/v1/accounts/**").hasAnyRole("CUSTOMER", "OPERATOR")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/fraud/**").hasAnyRole("FRAUD_ANALYST", "ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .headers(h -> h
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'"))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true).maxAgeInSeconds(63072000)))
            .build();
    }

    @Bean
    public JwtAuthenticationConverter bankingJwtConverter() {
        JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName("roles");
        gac.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(gac);
        c.setPrincipalClaimName("customerId");
        return c;
    }
}
```

### 13.3 Account-Level Authorization

Every command and query handler that touches account data must validate account ownership:

```java
@Component
public class AccountOwnershipGuard {

    public void assertOwnership(JwtPrincipal principal, AccountId accountId) {
        if (principal.hasRole("OPERATOR") || principal.hasRole("ADMIN")) return;

        boolean owns = principal.getAccountIds().contains(accountId.value());
        if (!owns) {
            // Audit the attempt before throwing
            securityAuditService.logUnauthorizedAccountAccess(
                principal.getCustomerId(), accountId.value(),
                SecurityContextHolder.getContext().getAuthentication());
            throw new AccountAccessDeniedException(accountId.value());
        }
    }
}
```

### 13.4 Sensitive Field Masking

All domain events that carry IBAN or account numbers must mask them before writing to logs or non-EventStoreDB destinations:

```java
public class SensitiveDataMaskingSerializer {

    private static final Pattern IBAN_PATTERN = Pattern.compile(
        "\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");

    public String maskSensitiveFields(String json) {
        return IBAN_PATTERN.matcher(json).replaceAll(m -> {
            String iban = m.group();
            return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
        });
    }
}

// Logback appender filter — applied globally
@Component
public class SensitiveDataMaskingFilter extends TurboFilter {
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        // Mask params in-place before logback formats the message
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String s) params[i] = masker.maskSensitiveFields(s);
            }
        }
        return FilterReply.NEUTRAL;
    }
}
```

### 13.5 GDPR — Pseudonymisation Strategy

PII is stored in a separate `customer-pii-store` (PostgreSQL, separate schema with column-level encryption). The event log stores only `customerId` UUIDs. On GDPR erasure:

1. Delete row from `customer-pii-store` → customer's name, DOB, address are gone.
2. Event log is preserved intact — required for AML/KYC regulatory retention (10 years).
3. `customerId` in events is a random UUID with no inherent PII — pseudonymisation, not anonymisation.
4. Retain erasure request in an `erasure_log` table for compliance audit.

```java
@Transactional
public void processErasureRequest(String customerId, String requestedBy) {
    // 1. Soft-verify no active accounts (open accounts block erasure under MLD5)
    List<AccountReadModel> accounts = accountQueryService.getAccountsByCustomer(customerId);
    boolean hasActiveAccounts = accounts.stream()
        .anyMatch(a -> a.status() == AccountStatus.ACTIVE);
    if (hasActiveAccounts) throw new ErasureBlockedByActiveAccountsException(customerId);

    // 2. Delete PII
    customerPiiRepository.delete(customerId);

    // 3. Pseudonymise read model (replace name/email with hashed placeholder)
    accountReadModelRepository.pseudonymise(customerId);

    // 4. Record erasure for compliance
    erasureLogRepository.record(ErasureRecord.of(customerId, requestedBy, Instant.now()));

    log.info("GDPR erasure completed for customerId {} by {}", customerId, requestedBy);
}
```

### 13.6 Vault Integration

Secrets are injected via the Vault Agent Injector sidecar — never in environment variables or Kubernetes Secrets in plaintext.

```yaml
# Pod annotation — Vault Agent injects secrets as files
vault.hashicorp.com/agent-inject: "true"
vault.hashicorp.com/agent-inject-secret-db: "banking/data/transfer-service/db"
vault.hashicorp.com/agent-inject-template-db: |
  {{- with secret "banking/data/transfer-service/db" -}}
  spring.datasource.password={{ .Data.data.password }}
  {{- end }}
```

```java
// Spring reads from the injected file
@Value("${spring.datasource.password}")
private String dbPassword;
```

---

## 14. API Design Contracts

### 14.1 API Versioning

URL path versioning: `/api/v1/`. Breaking changes require `/api/v2/` with 12-month parallel support. Deprecation communicated via `Deprecation` and `Sunset` response headers.

### 14.2 Transfer API

#### Initiate Transfer

```
POST /api/v1/transfers
Content-Type: application/json
Authorization: Bearer {jwt}
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
X-Correlation-ID: req-9b3f12...

Request Body:
{
  "sourceAccountId": "ACC-000001",
  "destination": {
    "type": "INTERNAL",
    "accountId": "ACC-000002"
  },
  "amount": { "value": "250.00", "currency": "EUR" },
  "remittanceInfo": "Invoice INV-2024-042"
}

Response 202 Accepted:
{
  "transferId": "TRF-20240315-000123",
  "status": "INITIATED",
  "estimatedCompletionAt": "2024-03-15T14:32:10Z",
  "_links": {
    "self":   { "href": "/api/v1/transfers/TRF-20240315-000123" },
    "status": { "href": "/api/v1/transfers/TRF-20240315-000123/status" },
    "cancel": { "href": "/api/v1/transfers/TRF-20240315-000123/cancel",
                "methods": ["POST"] }
  }
}

Response 422 — Insufficient Funds (RFC 7807):
{
  "type":     "https://api.bank.com/errors/insufficient-funds",
  "title":    "Insufficient Funds",
  "status":   422,
  "detail":   "Available balance EUR 100.00 is less than transfer amount EUR 250.00",
  "instance": "/api/v1/transfers",
  "traceId":  "4bf92f3577b34da6",
  "timestamp":"2024-03-15T14:32:01Z"
}

Response 403 — Transfer Blocked by Risk Engine:
{
  "type":     "https://api.bank.com/errors/transfer-blocked",
  "title":    "Transfer Blocked",
  "status":   403,
  "detail":   "Transaction blocked by risk engine (score: 820)",
  "riskScore": 820,
  "caseId":   "CASE-20240315-000042",
  "traceId":  "4bf92f3577b34da6"
}

Response 503 — Risk Service Unavailable:
{
  "type":      "https://api.bank.com/errors/risk-service-unavailable",
  "title":     "Transfer Temporarily Unavailable",
  "status":    503,
  "detail":    "Transfer blocked pending risk assessment. Please retry in 10 seconds.",
  "retryAfter": 10,
  "traceId":   "4bf92f3577b34da6"
}
```

### 14.3 Account Balance API

```
GET /api/v1/accounts/{accountId}/balance

Response 200:
{
  "accountId":  "ACC-000001",
  "iban":       "DE89****3000",      <- always masked
  "currency":   "EUR",
  "balance": {
    "current":   { "value": "5420.50", "currency": "EUR" },
    "available": { "value": "5170.50", "currency": "EUR" },
    "reserved":  { "value": "250.00",  "currency": "EUR" }
  },
  "status":  "ACTIVE",
  "asOf":    "2024-03-15T14:35:00Z",
  "cached":  true,       <- indicates Redis cache was used
  "cacheAge": 1200       <- milliseconds since cache was populated
}
```

### 14.4 WebSocket Real-Time Notifications

```
WS /api/v1/ws/notifications
Authorization: Bearer {jwt}   <- sent as query param ?token=...

Server → Client (STOMP frames):
SUBSCRIBE /topic/accounts/{accountId}

{
  "type": "TRANSFER_COMPLETED",
  "data": {
    "transferId":    "TRF-20240315-000123",
    "direction":     "DEBIT",
    "amount":        "-250.00",
    "currency":      "EUR",
    "counterparty":  "DE89****4001",
    "balanceAfter":  "5170.50",
    "completedAt":   "2024-03-15T14:32:09Z",
    "remittanceInfo":"Invoice INV-2024-042"
  }
}
```

---

## 15. Data Models & Schema Definitions

### 15.1 EventStoreDB Stream Naming (Canonical)

| Stream Name Pattern | Aggregate | Notes |
|---|---|---|
| `account-{accountId}` | AccountAggregate | Prefix lowercase-hyphen |
| `customer-{customerId}` | CustomerAggregate | — |
| `transfer-{transferId}` | TransferAggregate | Short-lived |
| `transfer-limit-{customerId}` | TransferLimitAggregate | Daily/monthly limits |
| `ledger-{entryId}` | LedgerEntryAggregate | Immutable after creation |
| `risk-profile-{customerId}` | RiskProfileAggregate | Long-running |
| `fraud-case-{caseId}` | FraudCaseAggregate | Case lifecycle |
| `$ce-account` | System projection | Category stream for all account-* |

### 15.2 PostgreSQL Read-Side Schema

```sql
-- ─── Account Read Model ────────────────────────────────────────────────────
CREATE TABLE account_read_model (
    account_id        VARCHAR(50)   PRIMARY KEY,
    customer_id       VARCHAR(50)   NOT NULL,
    iban_masked       VARCHAR(34)   NOT NULL,          -- always stored masked
    iban_encrypted    BYTEA         NOT NULL,          -- column-level encrypted via pgcrypto
    product_code      VARCHAR(20)   NOT NULL,
    account_type      VARCHAR(20)   NOT NULL,
    currency          CHAR(3)       NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    balance_current   NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_available NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_reserved  NUMERIC(19,4) NOT NULL DEFAULT 0,
    opened_at         TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    projection_version BIGINT       NOT NULL,          -- event version that last updated this row
    CONSTRAINT chk_balance_non_negative CHECK (balance_available >= 0)
);

CREATE INDEX idx_acct_customer ON account_read_model (customer_id);
CREATE INDEX idx_acct_status   ON account_read_model (status) WHERE status != 'CLOSED';

-- ─── Transfer Read Model ────────────────────────────────────────────────────
CREATE TABLE transfer_read_model (
    transfer_id         VARCHAR(50)   PRIMARY KEY,
    source_account_id   VARCHAR(50)   NOT NULL,
    dest_account_id     VARCHAR(50),
    dest_iban_masked    VARCHAR(34),
    dest_name           VARCHAR(200),
    amount              NUMERIC(19,4) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    fx_amount           NUMERIC(19,4),
    fx_currency         CHAR(3),
    payment_rail        VARCHAR(20)   NOT NULL,
    status              VARCHAR(30)   NOT NULL,
    risk_score          SMALLINT,
    initiated_by        VARCHAR(100)  NOT NULL,
    initiated_at        TIMESTAMPTZ   NOT NULL,
    completed_at        TIMESTAMPTZ,
    failure_reason      VARCHAR(100),
    reversal_reason     VARCHAR(100),
    remittance_info     VARCHAR(500),
    correlation_id      VARCHAR(100)  NOT NULL
);

CREATE INDEX idx_transfer_source  ON transfer_read_model (source_account_id, initiated_at DESC);
CREATE INDEX idx_transfer_status  ON transfer_read_model (status) WHERE status IN ('INITIATED','DEBITED','SETTLING','REVERSING');

-- ─── Transaction (Ledger Entry) Read Model ─────────────────────────────────
CREATE TABLE transaction_read_model (
    entry_id          VARCHAR(50)   PRIMARY KEY,
    account_id        VARCHAR(50)   NOT NULL,
    transfer_id       VARCHAR(50),
    debit_credit      CHAR(1)       NOT NULL CHECK (debit_credit IN ('D','C')),
    amount            NUMERIC(19,4) NOT NULL,
    currency          CHAR(3)       NOT NULL,
    balance_after     NUMERIC(19,4) NOT NULL,
    description       VARCHAR(500),
    counterpart_name  VARCHAR(200),
    counterpart_iban  VARCHAR(34),    -- masked
    category          VARCHAR(50),   -- ML-enriched
    value_date        DATE          NOT NULL,
    booked_at         TIMESTAMPTZ   NOT NULL
) PARTITION BY RANGE (booked_at);

-- Monthly partitions for transaction history (improves query + archival performance)
CREATE TABLE transaction_read_model_2024_01 PARTITION OF transaction_read_model
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
-- ... create partitions monthly via pg_partman or Flyway migration

CREATE INDEX idx_txn_acct_date ON transaction_read_model (account_id, booked_at DESC);
CREATE INDEX idx_txn_transfer  ON transaction_read_model (transfer_id) WHERE transfer_id IS NOT NULL;

-- ─── Outbox Messages ────────────────────────────────────────────────────────
CREATE TABLE outbox_messages (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_id        VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(200) NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    partition_key   VARCHAR(100) NOT NULL,
    payload         BYTEA        NOT NULL,
    headers         JSONB        NOT NULL DEFAULT '{}',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     SMALLINT     NOT NULL DEFAULT 0,
    last_error      TEXT,
    dead_at         TIMESTAMPTZ,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_pending ON outbox_messages (created_at) WHERE status = 'PENDING';
```

### 15.3 Redis Key Patterns (Canonical)

| Key Pattern | Type | TTL | Purpose | Notes |
|---|---|---|---|---|
| `balance:{accountId}` | Hash | 5s | Hot balance cache | Invalidated on `LedgerEntryCreatedEvent` |
| `http-idem:{key}` | String | 24h | HTTP idempotency | UUID v4 from X-Idempotency-Key |
| `http-idem-inflight:{key}` | String | 30s | Concurrent duplicate guard | Deleted after handler completes |
| `kafka-idem:{eventId}` | String | 25h | Kafka consumer dedup | TTL > Kafka retry window |
| `velocity:{customerId}:{window}` | ZSet | window+60s | Transfer velocity for risk | Sorted set by timestamp |
| `risk-profile:{customerId}` | Hash | 1h | Risk scoring cache | Invalidated on profile update |
| `session:{sessionId}` | Hash | 30m | Cached JWT claims | Rolling TTL on activity |
| `transfer-limit:{customerId}:{date}` | Hash | 25h | Daily limit consumption | Key per calendar day |
| `account-lock:{accountId}` | String | 30s | Distributed lock for limit check | Only for TransferLimitAggregate |

---

## 16. Infrastructure & Deployment

### 16.1 Docker Compose (local dev — all services)

```yaml
version: '3.9'

services:
  eventstore:
    image: eventstore/eventstore:24.6-bookworm-slim
    environment:
      - EVENTSTORE_CLUSTER_SIZE=1
      - EVENTSTORE_RUN_PROJECTIONS=All
      - EVENTSTORE_START_STANDARD_PROJECTIONS=true
      - EVENTSTORE_INSECURE=true        # dev only — TLS in prod
    ports: ["2113:2113"]
    volumes: ["esdb-data:/var/lib/eventstore"]

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: banking
      POSTGRES_PASSWORD: banking_dev
      POSTGRES_DB: banking_read
    ports: ["5432:5432"]
    volumes: ["pg-data:/var/lib/postgresql/data"]
    command: postgres -c wal_level=logical  # required for Debezium CDC (optional)

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass redis_dev --appendonly yes
    ports: ["6379:6379"]
    volumes: ["redis-data:/data"]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment: { ZOOKEEPER_CLIENT_PORT: 2181 }

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_LOG_RETENTION_MS: 2592000000   # 30 days
      KAFKA_MIN_INSYNC_REPLICAS: 1         # dev: 1; prod: 2
    ports: ["9092:9092"]

  schema-registry:
    image: confluentinc/cp-schema-registry:7.6.0
    depends_on: [kafka]
    environment:
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:9092
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
    ports: ["8081:8081"]

  keycloak:
    image: quay.io/keycloak/keycloak:25.0
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin_dev
    volumes: ["./infra/keycloak/realm-banking.json:/opt/keycloak/data/import/realm.json"]
    ports: ["8080:8080"]

  jaeger:
    image: jaegertracing/all-in-one:1.57
    ports: ["16686:16686", "4317:4317"]

  prometheus:
    image: prom/prometheus:v2.52.0
    volumes: ["./infra/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml"]
    ports: ["9090:9090"]

  grafana:
    image: grafana/grafana:10.4.2
    volumes:
      - grafana-data:/var/lib/grafana
      - ./infra/monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    ports: ["3000:3000"]

volumes:
  esdb-data: pg-data: redis-data: grafana-data:
```

### 16.2 Kafka Topic Configuration

```bash
#!/usr/bin/env bash
# infra/kafka/create-topics.sh — idempotent topic creation

BROKER=${KAFKA_BROKER:-localhost:9092}
REPLICATION=${REPLICATION_FACTOR:-3}
MIN_ISR=${MIN_INSYNC_REPLICAS:-2}

create_topic() {
    local name=$1 partitions=$2 retention_ms=$3
    kafka-topics.sh --bootstrap-server $BROKER \
        --create --if-not-exists \
        --topic $name \
        --partitions $partitions \
        --replication-factor $REPLICATION \
        --config retention.ms=$retention_ms \
        --config min.insync.replicas=$MIN_ISR \
        --config compression.type=lz4 \
        --config acks=all
    echo "Topic $name: OK"
}

create_topic "banking.account.events.v1"      12  2592000000   # 30 days
create_topic "banking.transfer.events.v1"     24  2592000000   # 30 days — more partitions
create_topic "banking.ledger.events.v1"       12  2592000000
create_topic "banking.fraud.events.v1"        6   2592000000
create_topic "banking.notification.commands.v1" 6  86400000    # 1 day
create_topic "banking.transactions.enriched.v1" 12 2592000000
create_topic "banking.transfer.dlq.v1"        6   -1           # infinite retention
create_topic "banking.outbox.dlq.v1"          3   -1           # infinite retention
```

### 16.3 Spring Application Properties (production)

```yaml
# application-prod.yml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    producer:
      acks: all
      retries: 10
      properties:
        enable.idempotence: "true"
        max.in.flight.requests.per.connection: 5
        delivery.timeout.ms: 30000
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
      isolation-level: read_committed
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
        specific.avro.reader: "true"

  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 300000
      max-lifetime: 1200000
      leak-detection-threshold: 60000

resilience4j:
  circuitbreaker:
    instances:
      risk-service:
        registerHealthIndicator: true
        slidingWindowType: TIME_BASED
        slidingWindowSize: 10
        failureRateThreshold: 30
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 45ms
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      risk-service:
        timeoutDuration: 48ms

management:
  tracing:
    sampling:
      probability: 0.1
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT}
  metrics:
    export:
      prometheus:
        enabled: true
```

### 16.4 Kubernetes Deployment

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
    rollingUpdate: { maxSurge: 1, maxUnavailable: 0 }
  template:
    metadata:
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/agent-inject-secret-db: "banking/data/transfer-service/db"
    spec:
      serviceAccountName: transfer-service
      containers:
      - name: transfer-service
        image: bank/transfer-service:${VERSION}
        resources:
          requests: { memory: "512Mi", cpu: "500m" }
          limits:   { memory: "1Gi",  cpu: "2000m" }  # allow burst for virtual threads
        env:
        - name: JAVA_TOOL_OPTIONS
          value: >-
            -Xms256m -Xmx768m
            -XX:+UseZGC
            -Djdk.virtualThreadScheduler.parallelism=16
        readinessProbe:
          httpGet: { path: /actuator/health/readiness, port: 8082 }
          initialDelaySeconds: 20
          periodSeconds: 10
          failureThreshold: 3
        livenessProbe:
          httpGet: { path: /actuator/health/liveness, port: 8082 }
          initialDelaySeconds: 60
          periodSeconds: 30
        lifecycle:
          preStop:
            exec:
              command: ["sleep", "15"]  # drain in-flight requests before pod termination
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: transfer-service-pdb
  namespace: banking
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: transfer-service
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: transfer-service-hpa
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: transfer-service }
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource: { name: cpu, target: { type: Utilization, averageUtilization: 65 } }
  - type: Pods
    pods:
      metric: { name: kafka_consumer_group_lag }
      target: { type: AverageValue, averageValue: "500" }
```

---

## 17. Phase 1 — Shared Kernel & Account Foundation

**Goal:** Running skeleton with EventStoreDB, Kafka, outbox relay, and the Account Service writing events. At phase end, an account can be opened, frozen, and queried. All writes go through the Outbox.

### 17.1 Prerequisites (produce before service code)

1. `infra/docker-compose.yml` — all services as defined in Section 16.1.
2. `infra/kafka/create-topics.sh` — idempotent topic creation.
3. Flyway migration baseline for all PostgreSQL schemas (`V1__baseline.sql`).
4. `shared-kernel` module with `AggregateRoot`, `DomainEvent`, `Money`, `IBAN`, `AccountId`, `EventMetadata`, `KafkaTopics`, `ConsumerGroups`.
5. Avro schemas in `src/main/avro/` per domain event — registered in Schema Registry on startup.
6. `.env.example` with all required variables.

### 17.2 Implementation Order

```
1. shared-kernel module (AggregateRoot, value objects, DomainEvent interface)
2. account-service/domain (AccountAggregate + events + exceptions — zero Spring)
3. account-service/infrastructure/persistence (EventStoreDBAccountRepository)
4. account-service/infrastructure/persistence (outbox table + OutboxWriter + OutboxRelay)
5. account-service/application/command (OpenAccountCommandHandler — Outbox pattern)
6. account-service/infrastructure/messaging (AccountEventProjector — Kafka consumer)
7. account-service/api (REST controller + DTOs + mappers)
8. Account domain unit tests (100% state machine transitions)
9. Account integration test (TestContainers — EventStoreDB + Kafka + PostgreSQL)
```

### 17.3 Phase 1 Acceptance Criteria

- [ ] `POST /api/v1/accounts` creates an account. EventStoreDB stream `account-{id}` contains `AccountOpenedEvent`.
- [ ] Outbox record in PostgreSQL is published to Kafka within 200ms.
- [ ] Kafka consumer projects `AccountOpenedEvent` to `account_read_model`. `GET /api/v1/accounts/{id}` returns 200.
- [ ] Concurrent account opens with same idempotency key return identical responses.
- [ ] `PUT /api/v1/accounts/{id}/freeze` changes status to FROZEN. Event sourcing: reload from EventStoreDB produces FROZEN aggregate.
- [ ] Outbox relay survives Kafka restart: events buffered in PostgreSQL, published after Kafka recovers.
- [ ] `docker compose up` starts all infra healthy.
- [ ] `./mvnw test` passes all unit tests with > 80% coverage on domain classes.

---

## 18. Phase 2 — Transfer Core & Ledger

**Goal:** Internal transfers work end-to-end. Full saga with balance reservation, double-entry ledger, risk scoring (stubbed), and compensation on failure. WebSocket notifications delivered.

### 18.1 Implementation Order

```
1. transfer-service/domain (TransferAggregate + full state machine + all events)
2. transfer-service/domain (TransferLimitAggregate)
3. transfer-service/application (InitiateTransferCommandHandler — Outbox, reservation step)
4. risk-service stub (returns APPROVE for all requests, 5ms latency)
5. transfer-service/application (saga choreography consumers for ledger events)
6. ledger-service/domain (LedgerEntryAggregate + double-entry validation)
7. ledger-service/application (LedgerCommandHandler — creates debit + credit entries)
8. ledger-service/infrastructure (AccountBalanceProjector → Redis cache + PostgreSQL)
9. Compensation path: credit failure → reversal saga
10. WebSocket notification service (consumes TransferCompletedEvent, pushes to client)
11. Integration tests: full internal transfer end-to-end
```

### 18.2 Double-Entry Invariant

```java
@Service
public class DoubleEntryService {

    /**
     * A journal entry consists of exactly N debits and N credits that sum to zero.
     * This invariant MUST hold before any ledger events are persisted.
     */
    public void validateJournalEntry(List<LedgerEntryCommand> entries) {
        BigDecimal debitSum  = entries.stream()
            .filter(e -> e.debitCredit() == DebitCredit.DEBIT)
            .map(e -> e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditSum = entries.stream()
            .filter(e -> e.debitCredit() == DebitCredit.CREDIT)
            .map(e -> e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debitSum.compareTo(creditSum) != 0) {
            throw new UnbalancedJournalEntryException(
                "Debits " + debitSum + " ≠ Credits " + creditSum);
        }
    }
}
```

### 18.3 Phase 2 Acceptance Criteria

- [ ] `POST /api/v1/transfers` (internal, EUR 250): responds 202, transfer reaches `COMPLETED` within p99 350ms.
- [ ] Source balance decreases by EUR 250; destination increases by EUR 250.
- [ ] Ledger has exactly 2 entries (1 debit, 1 credit). Double-entry sum = 0.
- [ ] Concurrent transfer attempts on same account — only one succeeds when balance insufficient; `WrongExpectedVersionException` on second triggers reload-and-validate, returns 422.
- [ ] Compensation: artificially fail credit step → `TransferReversedEvent` appears in stream → source balance restored.
- [ ] WebSocket client receives `TRANSFER_COMPLETED` within 1 second of saga completion.
- [ ] Idempotency: same `X-Idempotency-Key` sent twice → identical 202 response, single transfer in event store.
- [ ] `GET /api/v1/accounts/{id}/balance` returns Redis-cached balance within 5ms (after first populate).

---

## 19. Phase 3 — Risk Engine & Fraud Cases

**Goal:** Real risk scoring within 50ms synchronous SLA. Circuit breaker configured fail-closed. Fraud case lifecycle.

### 19.1 Phase 3 Acceptance Criteria

- [ ] Risk scoring p99 < 45ms under 500 concurrent requests (Gatling).
- [ ] Transfer with synthesised high-risk signals returns 403 `TRANSFER_BLOCKED`.
- [ ] Risk service killed → circuit breaker opens → transfer returns 503 `RISK_SERVICE_UNAVAILABLE` (not allowed through).
- [ ] Circuit breaker half-open after 10 seconds → first probe allowed → auto-closes if successful.
- [ ] `FraudCaseAggregate` created for BLOCK decisions. `GET /api/v1/fraud/cases/{id}` returns case detail.
- [ ] Fraud analyst can `POST /api/v1/fraud/cases/{id}/review` and `POST /api/v1/fraud/cases/{id}/resolve`.
- [ ] APPROVE resolution: transfer saga resumes (if held). REJECT resolution: reversal triggered.
- [ ] Velocity signal uses Redis pipelining — no individual ZADD/ZRANGEBYSCORE per request.
- [ ] Risk scoring latency p99 alert fires when scoring exceeds 45ms for > 30s.

---

## 20. Phase 4 — External Rails & Enrichment

**Goal:** SEPA credit transfers submitted to external rail. FX conversion. Kafka Streams enrichment pipeline.

### 20.1 SEPA Adapter

```java
public interface PaymentRailAdapter {
    /**
     * Submit a transfer to the payment rail.
     * Returns immediately with a submission reference.
     * Settlement confirmation arrives asynchronously via webhook or polling.
     */
    PaymentSubmissionResult submit(TransferAggregate transfer);

    /**
     * Handle external settlement notification.
     * Called by webhook endpoint or polling job.
     */
    void handleSettlementNotification(SettlementNotification notification);
}
```

### 20.2 Phase 4 Acceptance Criteria

- [ ] SEPA transfer submitted to mock rail adapter. Transfer stays in `SETTLING` until mock ACK received.
- [ ] Mock ACK (webhook) arrives → transfer moves to `COMPLETED`.
- [ ] Mock REJECT → compensation reversal triggered.
- [ ] FX transfer: EUR → USD. `fxAmount` and `fxCurrency` populated in transfer read model.
- [ ] Kafka Streams enrichment pipeline: `TransferCompletedEvent` enriched with merchant category. `enriched-transactions` topic populated.
- [ ] Daily transfer limit enforced per customer tier and KYC level.

---

## 21. Phase 5 — Hardening, Observability & Load Testing

**Goal:** All SLOs verified under load. Security hardened. Runbooks written. Chaos tests pass.

### 21.1 Gatling Load Test Targets

```scala
// LoadTestSimulation.scala — must pass before release

val transferScenario = scenario("Internal Transfer")
  .exec(http("Initiate Transfer")
    .post("/api/v1/transfers")
    .header("X-Idempotency-Key", "#{idempotencyKey}")
    .body(StringBody("#{transferBody}"))
    .check(status.in(202, 422))
    .check(responseTimeInMillis.lte(350)))  // p99 target

setUp(
  transferScenario.inject(
    rampUsersPerSec(100).to(10000).during(60.seconds),
    constantUsersPerSec(10000).during(300.seconds)
  )
).assertions(
  global.successfulRequests.percent.gt(99.0),
  forAll.responseTime.percentile(99).lt(350),
  forAll.responseTime.percentile(999).lt(800)
)
```

### 21.2 Chaos Engineering

Using Chaos Monkey for Spring Boot (`chaos-monkey-spring-boot`):

```yaml
chaos:
  monkey:
    enabled: true
    watcher:
      service: true
      repository: true
    assaults:
      level: 3
      latencyActive: true
      latencyRangeStart: 100
      latencyRangeEnd: 500
      exceptionsActive: false  # only latency in smoke tests
```

Test scenarios (each must pass with circuit breakers absorbing failures):
1. 500ms latency injected on risk-service → circuit breaker opens → transfers blocked with 503 (correct).
2. EventStoreDB node 1 killed → cluster re-elects → writes resume within 15s.
3. Kafka broker 1 killed → producers use other brokers → consumers rebalance → Outbox relay catches up.
4. PostgreSQL primary killed → replica promoted → read model continues from replica.
5. Redis eviction simulated → balance recomputed from read model → no incorrect balances served.

### 21.3 Phase 5 Acceptance Criteria

- [ ] All Gatling SLOs from Section 1.2 pass at 10,000 TPS sustained.
- [ ] All 5 chaos scenarios pass without data loss or incorrect balances.
- [ ] `govulncheck` (OWASP Dependency Check for Java) reports zero CRITICAL CVEs.
- [ ] PCI-DSS scan: no PAN or raw IBAN in logs, traces, or Kafka messages.
- [ ] All 7 runbooks (Section 24) are present and contain step-by-step commands.
- [ ] `./mvnw verify` passes with ≥ 80% line coverage on all modules.
- [ ] Pact contract tests pass for all service pairs.

---

## 22. Testing Strategy

### 22.1 Test Pyramid

| Layer | Coverage Target | Tooling |
|-------|----------------|---------|
| Domain unit tests | 100% aggregate state transitions; 95% domain service branches | JUnit 5 + AssertJ |
| Application unit tests | All command handlers + query handlers (happy + error) | JUnit 5 + Mockito |
| Slice tests | All REST controllers (all endpoints + auth scenarios) | `@WebMvcTest` |
| Integration tests | Full Spring context + real containers per service | TestContainers |
| Contract tests | All consumer/provider pairs | Pact |
| End-to-end tests | Full transfer flow across all services | TestContainers + rest-assured |
| Load tests | All SLOs from Section 1.2 | Gatling |
| Chaos tests | All 5 scenarios from Section 21.2 | Chaos Monkey + custom |

### 22.2 Domain Unit Test Examples

```java
class TransferAggregateTest {

    @Test
    void should_block_transfer_when_risk_decision_is_block() {
        TransferAggregate transfer = initiatedTransfer();
        EventMetadata meta = testMetadata();

        transfer.applyRiskScore(850, RiskLevel.CRITICAL, RiskDecision.BLOCK,
                                List.of("VELOCITY_HIGH", "AMOUNT_UNUSUAL"), 30L, meta);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);
        assertThat(transfer.getUncommittedEvents())
            .extracting("class")
            .containsExactly(TransferRiskScoredEvent.class, TransferBlockedEvent.class);
    }

    @Test
    void should_reject_reversal_when_debit_was_not_executed() {
        TransferAggregate transfer = initiatedTransfer(); // status = INITIATED
        EventMetadata meta = testMetadata();

        assertThatThrownBy(() ->
            transfer.initiateReversal(ReversalReason.CREDIT_FAILED, "entry-1", meta))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("debit was not executed");
    }

    @Test
    void should_compute_risk_score_without_integer_overflow() {
        // 15 signals each scoring 100 = 1500 raw → scaled to 1000 (clamped)
        TransactionContext ctx = highVolumeContext();
        RiskScoreResult result = riskScoringService.score(ctx);

        assertThat(result.score()).isBetween(0, 1000);
        assertThat(result.score()).isEqualTo(1000); // clamped
    }

    @Test
    void should_not_allow_double_with_money_arithmetic() {
        // Regression: ensure Money never uses double
        Money a = Money.of(new BigDecimal("0.10"), Currency.EUR);
        Money b = Money.of(new BigDecimal("0.20"), Currency.EUR);
        Money sum = a.add(b);

        // 0.1 + 0.2 in double = 0.30000000000000004
        assertThat(sum.amount()).isEqualByComparingTo(new BigDecimal("0.30"));
    }
}
```

### 22.3 Integration Test with TestContainers

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
class InternalTransferIntegrationTest {

    @Container
    static EventStoreDBContainer esdb =
        new EventStoreDBContainer("eventstore/eventstore:24.6-bookworm-slim")
            .withoutAuthentication();

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("banking_read_test");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void complete_internal_transfer_respects_all_invariants() throws Exception {
        // Arrange
        String sourceId = createAccountWithBalance(Money.of("1000.00", EUR));
        String destId   = createAccountWithBalance(Money.zero(EUR));

        // Act
        String transferId = transferService.initiate(
            InitiateTransferCommand.builder()
                .sourceAccountId(new AccountId(sourceId))
                .destinationAccountId(new AccountId(destId))
                .amount(Money.of("250.00", EUR))
                .rail(PaymentRail.INTERNAL)
                .initiatedBy("CUST-001")
                .metadata(testMetadata())
                .build());

        // Wait for saga completion (eventual consistency)
        await().atMost(Duration.ofSeconds(10))
               .pollInterval(Duration.ofMillis(100))
               .until(() -> transferQueryService.getStatus(transferId) == TransferStatus.COMPLETED);

        // Assert balances
        AccountBalance sourceBal = accountQueryService.getBalance(sourceId);
        AccountBalance destBal   = accountQueryService.getBalance(destId);

        assertThat(sourceBal.current()).isEqualByComparingTo(Money.of("750.00", EUR));
        assertThat(destBal.current()).isEqualByComparingTo(Money.of("250.00", EUR));

        // Assert double-entry
        List<LedgerEntry> entries = ledgerQueryService.getEntriesForTransfer(transferId);
        assertThat(entries).hasSize(2);
        BigDecimal netLedger = entries.stream()
            .map(e -> e.debitCredit() == DEBIT
                ? e.amount().amount().negate()
                : e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netLedger).isEqualByComparingTo(BigDecimal.ZERO);

        // Assert audit trail in EventStoreDB
        List<DomainEvent> transferEvents = transferRepository
            .loadFrom(transferId, 0L);
        assertThat(transferEvents)
            .extracting("class")
            .containsExactly(
                TransferInitiatedEvent.class,
                TransferRiskScoredEvent.class,
                TransferValidatedEvent.class,
                TransferDebitReservedEvent.class,
                TransferDebitedEvent.class,
                TransferCreditedEvent.class,
                TransferCompletedEvent.class
            );
    }

    @Test
    void compensation_restores_balance_on_credit_failure() throws Exception {
        String sourceId = createAccountWithBalance(Money.of("500.00", EUR));
        // Arrange destination account to fail credit
        injectCreditFailureFor(sourceId);

        String transferId = transferService.initiate(buildTransferCommand(sourceId, "ACC-DEST"));

        await().atMost(Duration.ofSeconds(15))
               .until(() -> transferQueryService.getStatus(transferId) == TransferStatus.REVERSED);

        AccountBalance sourceBal = accountQueryService.getBalance(sourceId);
        assertThat(sourceBal.current()).isEqualByComparingTo(Money.of("500.00", EUR));
    }
}
```

### 22.4 Pact Contract Tests

```java
// Transfer service (consumer) ↔ Risk service (provider)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "risk-service")
class TransferServiceRiskContractTest {

    @Pact(consumer = "transfer-service")
    RequestResponsePact lowRiskScorePact(PactDslWithProvider builder) {
        return builder
            .given("risk service is healthy")
            .uponReceiving("score a low-risk internal transfer")
            .path("/api/v1/risk/score")
            .method("POST")
            .body(new PactDslJsonBody()
                .stringType("transferId")
                .stringType("customerId")
                .decimalType("amount")
                .stringMatcher("currency", "EUR|USD|GBP", "EUR")
                .stringMatcher("paymentRail", "INTERNAL|SEPA|SWIFT", "INTERNAL"))
            .willRespondWith()
            .status(200)
            .body(new PactDslJsonBody()
                .integerType("riskScore", 120)
                .stringMatcher("riskLevel", "LOW|MEDIUM|HIGH|CRITICAL", "LOW")
                .stringMatcher("decision", "APPROVE|REVIEW|BLOCK", "APPROVE")
                .longType("scoringDurationMs", 12L))
            .toPact();
    }

    @Pact(consumer = "transfer-service")
    RequestResponsePact riskServiceUnavailablePact(PactDslWithProvider builder) {
        return builder
            .given("risk service is overloaded")
            .uponReceiving("score a transfer when risk service is slow")
            .path("/api/v1/risk/score")
            .method("POST")
            .willRespondWith()
            .status(503)
            .toPact();
    }
}
```

---

## 23. Observability & Alerting

### 23.1 Custom Metrics

```java
// All services — register in @PostConstruct
public class TransferMetrics {

    public TransferMetrics(MeterRegistry registry) {
        // Counters
        this.transfersInitiated  = registry.counter("banking.transfers.initiated",
            "service", "transfer-service");
        this.transfersCompleted  = registry.counter("banking.transfers.completed");
        this.transfersFailed     = registry.counter("banking.transfers.failed");
        this.transfersBlocked    = registry.counter("banking.transfers.blocked",
            "reason", "risk_score");

        // Timers
        this.transferDuration    = registry.timer("banking.transfers.duration");
        this.riskScoringDuration = registry.timer("banking.risk.scoring.duration");

        // Gauges
        Gauge.builder("banking.transfers.saga.inflight",
            sagaRepository, SagaRepository::countActive)
            .register(registry);

        Gauge.builder("banking.outbox.pending",
            outboxRepository, OutboxRepository::countPending)
            .tag("service", "transfer-service")
            .register(registry);

        // Histograms
        this.riskScoreDistribution = DistributionSummary.builder("banking.risk.score")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .sla(200, 400, 600, 800)
            .register(registry);
    }
}
```

### 23.2 Alerting Rules

```yaml
# infra/monitoring/alerts/banking-platform.yml
groups:
- name: banking-critical
  rules:

  - alert: TransferSuccessRateCritical
    expr: |
      rate(banking_transfers_completed_total[5m])
      / rate(banking_transfers_initiated_total[5m]) < 0.95
    for: 2m
    labels: { severity: critical, team: payments }
    annotations:
      summary: "Transfer success rate {{ $value | humanizePercentage }} below 95%"
      runbook: "https://wiki.bank.com/runbooks/transfer-success-rate"

  - alert: RiskScoringLatencyBreached
    expr: |
      histogram_quantile(0.99,
        rate(banking_risk_scoring_duration_seconds_bucket[5m])) > 0.045
    for: 1m
    labels: { severity: warning }
    annotations:
      summary: "Risk scoring p99 {{ $value | humanizeDuration }} exceeds 45ms SLA"

  - alert: RiskCircuitBreakerOpen
    expr: resilience4j_circuitbreaker_state{name="risk-service",state="open"} == 1
    for: 30s
    labels: { severity: critical }
    annotations:
      summary: "Risk service circuit breaker OPEN — all transfers are being blocked"
      runbook: "https://wiki.bank.com/runbooks/risk-circuit-breaker"

  - alert: OutboxRelayLagging
    expr: banking_outbox_pending{service="transfer-service"} > 500
    for: 2m
    labels: { severity: warning }

  - alert: SagaDLQGrowing
    expr: increase(kafka_log_log_end_offset{topic="banking.transfer.dlq.v1"}[10m]) > 0
    for: 0m
    labels: { severity: critical }
    annotations:
      summary: "Transfer DLQ receiving messages — saga failures require investigation"

  - alert: InsufficientFundsRateSpike
    expr: |
      rate(banking_transfers_failed_total{reason="INSUFFICIENT_FUNDS"}[5m])
      / rate(banking_transfers_initiated_total[5m]) > 0.20
    for: 5m
    labels: { severity: warning }
    annotations:
      summary: "Unusually high insufficient funds rate — possible balance projection lag"

  - alert: KafkaConsumerLagHigh
    expr: kafka_consumer_group_lag{topic=~"banking\\..*"} > 10000
    for: 5m
    labels: { severity: warning }

  - alert: EventStoreDBWriteLatencyHigh
    expr: |
      histogram_quantile(0.99,
        rate(eventstore_write_duration_seconds_bucket[5m])) > 0.1
    for: 2m
    labels: { severity: warning }
```

### 23.3 Distributed Tracing Attributes

Every trace span in the transfer critical path must carry these attributes:

```java
Span.current()
    .setAttribute("banking.transfer.id",          transferId)
    .setAttribute("banking.transfer.rail",         rail.name())
    .setAttribute("banking.transfer.amount.eur",  amountInEur)  // never raw amount if FX
    .setAttribute("banking.account.source",       sourceAccountId)
    .setAttribute("banking.customer.tier",        customerTier)
    .setAttribute("banking.risk.score",           riskScore)
    .setAttribute("banking.risk.decision",        riskDecision.name())
    .setAttribute("banking.saga.step",            sagaStep.name());
```

---

## 24. Operational Runbooks

Every runbook must contain: 1) Alert context, 2) Immediate mitigation (< 5 minutes), 3) Investigation steps with exact commands, 4) Root cause analysis, 5) Resolution, 6) Post-incident actions.

### 24.1 Transfer Success Rate Low

**Alert:** `TransferSuccessRateCritical` — success rate < 95% for 2 minutes.

**Immediate mitigation:**
```bash
# Check which failure reason dominates
kubectl exec -n banking deploy/transfer-service -- \
  curl -s http://localhost:8082/actuator/metrics/banking.transfers.failed | jq '.measurements'

# Check saga DLQ size
kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKER \
  --describe --group transfer-service.projector.v1
```

**Investigation:**
```bash
# Check recent errors in transfer service
kubectl logs -n banking deploy/transfer-service --since=10m | grep ERROR | tail -50

# Check EventStoreDB connectivity
kubectl exec -n banking deploy/transfer-service -- \
  curl -s http://eventstore:2113/health/alive

# Check risk service circuit breaker state
curl -s http://transfer-service:8082/actuator/circuitbreakers | jq '.circuitBreakers["risk-service"].state'
```

### 24.2 Risk Circuit Breaker Open

**Alert:** `RiskCircuitBreakerOpen` — circuit breaker OPEN for > 30 seconds.

**Impact:** All new transfers are blocked with 503. Existing transfers already past risk scoring are unaffected.

**Immediate mitigation:**
```bash
# Check risk service pod status
kubectl get pods -n banking -l app=risk-service

# Check risk service logs
kubectl logs -n banking deploy/risk-service --since=5m | grep -E "ERROR|WARN" | tail -30

# Manually force circuit breaker half-open (allow one probe)
curl -X POST http://transfer-service:8082/actuator/circuitbreakers/risk-service/half-open
```

**Resolution:**
- If risk service pods are crashed: `kubectl rollout restart deploy/risk-service -n banking`
- If latency issue: check risk service database (PostgreSQL), Redis (profile cache), ML model endpoint

### 24.3 Outbox Relay Dead Letter

**Alert:** `SagaDLQGrowing` — messages appearing in `banking.transfer.dlq.v1`.

```bash
# Inspect DLQ messages
kafka-console-consumer.sh --bootstrap-server $KAFKA_BROKER \
  --topic banking.transfer.dlq.v1 --from-beginning --max-messages 10

# Check outbox dead messages
psql $DB_URL -c "SELECT event_type, last_error, created_at FROM outbox_messages
                 WHERE status = 'DEAD' ORDER BY created_at DESC LIMIT 20;"

# Replay dead outbox messages after fixing root cause
psql $DB_URL -c "UPDATE outbox_messages SET status = 'PENDING', retry_count = 0
                 WHERE status = 'DEAD' AND created_at > NOW() - INTERVAL '1 hour';"
```

### 24.4 EventStoreDB Node Failure

```bash
# Check cluster health
curl -s http://eventstore-node1:2113/gossip | jq '.members[] | {instanceId, state, isAlive}'

# Force leader election if needed
curl -X POST http://eventstore-node1:2113/admin/scavenge

# After node recovery, verify stream integrity for critical aggregates
kafka-console-consumer.sh --topic banking.transfer.events.v1 \
  --from-beginning | jq 'select(.transferId == "TRF-SUSPECT")'
```

### 24.5 Balance Inconsistency Detected

This is a P0 incident. Never auto-remediate — human review required.

```bash
# 1. Immediately suspend affected account
curl -X POST http://account-service:8081/api/v1/admin/accounts/{accountId}/suspend \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"reason": "BALANCE_INCONSISTENCY_INVESTIGATION"}'

# 2. Compare Redis cache vs PostgreSQL read model vs EventStoreDB replay
redis-cli GET balance:{accountId}
psql $DB_URL -c "SELECT balance_current, balance_available FROM account_read_model WHERE account_id = '{accountId}';"

# 3. Replay balance from EventStoreDB (authoritative)
curl -X POST http://account-service:8081/api/v1/admin/accounts/{accountId}/replay-balance \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 4. Compare replayed balance vs stored — if different, escalate to engineering lead
```

---

## 25. Full System Acceptance Criteria

When all 5 phases are complete, the following end-to-end scenario must execute without manual intervention as an automated CI pipeline job:

```
1.  docker compose up -d                              → all services healthy (EventStoreDB, Kafka, PostgreSQL, Redis, Keycloak)
2.  create-topics.sh                                  → all 8 topics created
3.  POST /auth/register (Keycloak)                    → customer JWT issued
4.  POST /api/v1/accounts                             → EUR checking account opened
5.  GET  /api/v1/accounts/{id}                        → 200, status=ACTIVE
6.  Seed EUR 10,000 balance (admin endpoint)          → balance_current = 10000.00
7.  POST /api/v1/accounts (second account)            → second account for destination
8.  POST /api/v1/transfers {amount: 250 EUR}          → 202 INITIATED
9.  Poll GET /api/v1/transfers/{id}                   → status=COMPLETED within 5 seconds
10. GET  /api/v1/accounts/{sourceId}/balance          → available = 9750.00
11. GET  /api/v1/accounts/{destId}/balance            → available = 250.00
12. Verify EventStoreDB stream transfer-{id}          → 7 events, correct order
13. Verify double-entry invariant via ledger API      → debit sum = credit sum
14. WebSocket client received TRANSFER_COMPLETED      → within 1 second of step 9
15. POST /api/v1/transfers (same idempotency key)     → 202, identical transferId — no new transfer created
16. Synthesise high-risk transfer (velocity = 20)     → 403 TRANSFER_BLOCKED
17. Kill risk-service pod                             → POST /api/v1/transfers returns 503 (fail-closed)
18. Restart risk-service                              → transfers accepted within 15 seconds
19. Kill Kafka broker                                 → POST /api/v1/transfers succeeds, outbox_messages.status='PENDING'
20. Restart Kafka                                     → outbox.status='PUBLISHED' within 30 seconds, event appears in topic
21. Trigger GDPR erasure for customer                 → PII deleted, event log intact
22. GET  /api/v1/accounts/{id} after erasure          → 200, displayName = "[REDACTED]", events preserved
23. Run Gatling load test                             → p99 internal transfer < 350ms at 10,000 TPS
24. Run Pact contract verification                    → all provider states pass
25. docker compose down                               → clean shutdown, no data loss
```

Every step must be implemented as an assertion in the CI pipeline. The release gate requires all 25 steps green.

---

*Banking Platform Enterprise Specification v2.0*
*Architecture: DDD · CQRS · Event Sourcing · Choreography Saga · Transactional Outbox*
*Stack: Java 21 · Spring Boot 3.3.x · EventStoreDB 24 · Apache Kafka 3.7 · PostgreSQL 16 · Redis 7 · Keycloak 25*
*Supersedes v1.0 — all canonical names, patterns, and acceptance criteria in this document take precedence.*