# Secure Retail Banking Platform — Enterprise Implementation Specification v2.0

> **For the implementing engineer:** This is the single authoritative document for the Banking Platform. It contains both the system architecture specification and the Java clean-code standards that govern every line of implementation. Read the entire document before writing any code. The code quality standards in **Section 0** apply to every section that follows — they are not optional and are enforced by CI (Checkstyle, SpotBugs, NullAway, ArchUnit, JaCoCo).
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
> - All collections returned from aggregate methods are unmodifiable. No caller can mutate aggregate state directly.
> - All domain exceptions are unchecked (extend `RuntimeException`), typed, and never swallowed silently.
> - `@Transactional` is only permitted in the `application/` layer — never in `domain/` or `infrastructure/` adapters called directly from domain.
> - No raw types. Generics are always bounded or explicit.
> - Loggers are `private static final`, declared via SLF4J `LoggerFactory`, never injected.

---

## Table of Contents

0. [Code Quality Standards](#0-code-quality-standards)
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

## 0. Code Quality Standards

> These standards are enforced by CI tooling — Checkstyle, SpotBugs, NullAway, ArchUnit, and JaCoCo — and by code review. Every code example in this specification is written to satisfy them. Where a rule has a narrow exception specific to this platform, the exception is stated explicitly and applies only where stated.

---

### 0.1 Philosophy

**Readability is a production concern.** Code is read ten times for every one time it is written. During an on-call incident with a payment rail down, unclear code costs real money. Optimize for the reader — the engineer who extends this in three months, the SRE debugging a concurrency issue, the auditor reviewing the transfer path.

**Boring code is good code.** Java has rich language features. Not all of them belong in production banking code. A plain `for` loop that every engineer can read immediately beats a clever `Stream.reduce` chain that requires a whiteboard session to understand. The most important question in code review is: *can a competent engineer understand this without asking me questions?*

**Domain purity is not negotiable.** The `domain/` package is the highest-value code in the system. It encodes regulatory rules, financial invariants, and business logic that auditors will inspect. It must be free of framework dependencies so it can be tested in milliseconds, reasoned about in isolation, and understood without knowing Spring.

**Consistency beats local optimality.** When the team has agreed on a pattern, use it — even if you personally know a marginally better approach. Propose the change to this document; do not silently diverge in code.

---

### 0.2 Package & Module Design

#### Hexagonal architecture — the enforcement boundary

Each service module enforces a strict dependency direction using **ArchUnit** in CI:

```
domain/      ←── no external deps allowed (verified by ArchUnit rule)
    │
    ▼
application/ ←── depends on domain/ only; Spring @Service/@Transactional allowed here
    │
    ▼
infrastructure/ ←── depends on application/ + domain/; Spring @Component/@Repository allowed here
    │
    ▼
api/         ←── depends on application/ only (DTOs, controllers, mappers)
```

```java
// src/test/java/com/bank/architecture/ArchitectureRulesTest.java
// This test runs in CI on every module. It must never be disabled.
@AnalyzeClasses(packages = "com.bank", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domainMustHaveNoDependencyOnSpring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate.."
            )
            .because("Domain classes must be framework-free for portability and testability");

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("Application layer depends on domain ports, not infrastructure adapters");

    @ArchTest
    static final ArchRule apiMustNotDependOnDomainDirectly =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain..")
            .because("API layer communicates through application use cases, not domain objects directly");

    @ArchTest
    static final ArchRule repositoryInterfacesMustLiveInDomain =
        classes().that().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .should().resideInAPackage("..domain.repository..")
            .because("Repository interfaces are domain ports — their implementations live in infrastructure");
}
```

#### Module boundaries are enforced by Maven, not just convention

Each Maven module declares only the dependencies it needs. The `domain` module's `pom.xml` has no Spring dependency. If a developer accidentally adds one, the `dependency:analyze` plugin fails the build.

```xml
<!-- account-service/domain/pom.xml — must never have Spring deps -->
<dependencies>
    <dependency>
        <groupId>com.bank</groupId>
        <artifactId>shared-kernel</artifactId>
    </dependency>
    <!-- No spring-*, no jakarta.persistence, no hibernate -->
</dependencies>
```

#### One aggregate per package under `domain/model/`

```
domain/model/
  account/
    AccountAggregate.java
    AccountSnapshot.java
    event/
      AccountEvent.java          ← sealed interface
      AccountOpenedEvent.java
      AccountFrozenEvent.java
    command/
      OpenAccountCommand.java
    exception/
      AccountFrozenException.java
      InsufficientFundsException.java
    repository/
      AccountRepository.java     ← port interface — no implementation here
```

No aggregate class may reference another aggregate class directly. Cross-aggregate communication is always via events and identifiers (value objects like `AccountId`), never via object references.

---

### 0.3 Naming Conventions

#### Classes, interfaces, and records

| Artifact | Convention | Example |
|---|---|---|
| Aggregate | `{Concept}Aggregate` | `AccountAggregate`, `TransferAggregate` |
| Domain event (record) | `{Concept}{PastTense}Event` | `AccountOpenedEvent`, `TransferBlockedEvent` |
| Command (record) | `{Verb}{Concept}Command` | `OpenAccountCommand`, `InitiateTransferCommand` |
| Command handler | `{Verb}{Concept}CommandHandler` | `OpenAccountCommandHandler` |
| Query handler | `Get{Concept}QueryHandler` | `GetAccountQueryHandler` |
| Repository interface (domain) | `{Concept}Repository` | `AccountRepository` |
| Repository implementation (infra) | `EventStoreDB{Concept}Repository` | `EventStoreDBAccountRepository` |
| Read model | `{Concept}ReadModel` | `AccountReadModel`, `TransferReadModel` |
| DTO | `{Concept}{Request\|Response}` | `InitiateTransferRequest`, `AccountBalanceResponse` |
| Exception | `{Concept}{Reason}Exception` | `InsufficientFundsException`, `AccountFrozenException` |
| Value object | noun only | `Money`, `IBAN`, `AccountId` |
| Kafka consumer | `{Concept}EventProjector` or `{Concept}EventHandler` | `AccountEventProjector` |
| Service (domain) | `{Concept}Service` | `DoubleEntryService`, `RiskScoringService` |
| Service (application) | `{Concept}CommandHandler` or `{Concept}QueryHandler` | explicit intent over generic "Service" |

#### Methods

Command methods on aggregates are **imperative verbs** that describe what they do: `open()`, `freeze()`, `debit()`, `reserve()`, `initiateReversal()`. They are never getters.

Event handler methods on aggregates are named `handle(DomainEvent)` — they have one job: update state. They contain no business logic, no conditions, no external calls.

```java
// Good — imperative, intent-clear
public void freeze(FreezeReason reason, String initiatedBy, EventMetadata meta) { ... }
public void executeDebit(String transferId, EventMetadata meta) { ... }

// Bad — vague, unclear direction
public void processFreezeRequest(FreezeReason reason) { ... }
public void updateBalance() { ... }
```

#### Constants and enumerations

All Kafka topic strings live in `KafkaTopics`. All consumer group IDs live in `ConsumerGroups`. All Redis key patterns are built by a `RedisKeyFactory` class. Never hardcode strings in consumers or producers — the compiler cannot catch a typo in a string literal.

```java
// Bad — unchecked at compile time
@KafkaListener(topics = "banking.transfer.events.v1")  // typo = silent failure

// Good — compile-time checked
@KafkaListener(topics = KafkaTopics.TRANSFER_EVENTS)
```

---

### 0.4 Immutability First

#### Domain events are records — always

Java records are immutable by construction. Every domain event is a record. No exceptions. Records cannot be subclassed, which enforces the sealed hierarchy pattern.

```java
// Good — immutable, compact, serialization-friendly
public record AccountOpenedEvent(
    String  eventId,
    String  accountId,
    String  customerId,
    String  iban,
    // ... other fields
    Instant occurredAt,
    String  schemaVersion
) implements AccountEvent {}

// Bad — mutable, allows inconsistent state
public class AccountOpenedEvent {
    private String eventId;
    public void setEventId(String id) { this.eventId = id; }  // never
}
```

#### Value objects are records

`Money`, `IBAN`, `AccountId`, `CustomerId`, `TransferId`, `CorrelationId` — all records. Validation in the compact constructor. No setters, no nulls after construction.

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.scale() > 4)
            throw new IllegalArgumentException(
                "Money scale exceeds 4 decimal places: " + amount.toPlainString());
        // Normalize scale on construction — 250 and 250.0000 are equal Money values
        amount = amount.setScale(4, HALF_EVEN);
    }
}
```

#### Collections returned from aggregates are always unmodifiable

```java
// Bad — caller can mutate aggregate state
public List<DomainEvent> getUncommittedEvents() {
    return uncommittedEvents;  // exposes mutable internal list
}

// Good — defensive copy or unmodifiable view
public List<DomainEvent> getUncommittedEvents() {
    return Collections.unmodifiableList(uncommittedEvents);
}
```

#### Fields in aggregates are `private` with no setters

Aggregate state changes only through `apply(DomainEvent)`. No field is ever public. No setter ever exists on an aggregate. The `handle(DomainEvent)` method in the base class is `protected final` — it is the only entry point.

---

### 0.5 Null Safety

#### `Objects.requireNonNull` at every constructor and factory method boundary

```java
// Every public-facing constructor and static factory method validates non-null inputs
public static TransferAggregate initiate(InitiateTransferCommand cmd) {
    Objects.requireNonNull(cmd, "command must not be null");
    Objects.requireNonNull(cmd.sourceAccountId(), "sourceAccountId must not be null");
    Objects.requireNonNull(cmd.amount(), "amount must not be null");
    Objects.requireNonNull(cmd.metadata(), "metadata must not be null");
    // ...
}
```

Use the two-argument form: `Objects.requireNonNull(value, "descriptive field name")`. The message names the field so the NPE stack trace is immediately actionable.

#### `Optional` for genuinely absent values — not as a universal null replacement

`Optional` is used when the absence of a value is a normal, expected business case: `loadOptional(String aggregateId)` returns `Optional<T>` because an aggregate might not exist yet.

`Optional` is **not** used as a method parameter type, not returned from domain methods that always produce a result, and not stored in fields.

```java
// Good — Optional communicates "may not exist"
Optional<AccountAggregate> loadOptional(String aggregateId);

// Bad — Optional as parameter is awkward to call and conveys nothing useful
void freeze(Optional<String> reason);  // use @Nullable or overloads instead

// Bad — Optional stored in a field
private Optional<String> lastError;  // just use @Nullable String lastError
```

#### NullAway enforces `@NonNull` by default in CI

The NullAway plugin (configured in the parent POM) treats all reference types as `@NonNull` unless explicitly annotated `@Nullable`. This catches null-dereference bugs at compile time.

```java
// NullAway catches this at compile time — no NPE at runtime
public void recordDebitExecuted(@NonNull String ledgerEntryId, @NonNull EventMetadata meta) {
    // Both params are guaranteed non-null — no manual checks needed here
    apply(new TransferDebitedEvent(...));
}
```

---

### 0.6 Error Handling

#### All domain exceptions are unchecked

Checked exceptions in domain code force infrastructure concerns (exception handling strategies) into the domain. All domain exceptions extend `RuntimeException`. They are typed, meaningful, and carry context.

```java
// Domain exception hierarchy — one per meaningful failure mode
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) { super(message); }
    protected DomainException(String message, Throwable cause) { super(message, cause); }
}

public class InsufficientFundsException extends DomainException {
    private final AccountId accountId;
    private final Money     available;
    private final Money     requested;

    public InsufficientFundsException(AccountId accountId, Money available, Money requested) {
        super("Insufficient funds on account %s: available %s, requested %s"
            .formatted(accountId.value(), available, requested));
        this.accountId = accountId;
        this.available = available;
        this.requested = requested;
    }
    // Getters for structured error responses — no setters
    public AccountId accountId() { return accountId; }
    public Money     available() { return available; }
    public Money     requested() { return requested; }
}

public class InvalidTransferStateException extends DomainException {
    public InvalidTransferStateException(String transferId,
                                          TransferStatus expected, TransferStatus actual) {
        super("Transfer %s expected status %s but was %s"
            .formatted(transferId, expected, actual));
    }
}
```

#### Never catch and swallow exceptions

```java
// Banned — hides failures completely
try {
    riskyOperation();
} catch (Exception e) {
    // do nothing
}

// Banned — loses the original cause
try {
    riskyOperation();
} catch (Exception e) {
    throw new RuntimeException("failed");  // no cause chained
}

// Correct — chain the cause, add context
try {
    client.appendToStream(streamName, options, eventData.iterator()).get();
} catch (ExecutionException ex) {
    if (ex.getCause() instanceof WrongExpectedVersionException wev) throw wev;
    throw new EventStoreException(
        "Failed to save aggregate %s".formatted(aggregate.getAggregateId()), ex);
}
```

#### Map exceptions to HTTP responses in one place

Exception-to-HTTP-status mapping lives in a single `@RestControllerAdvice` class per service. Command handlers and query handlers never return HTTP status codes — they throw typed domain exceptions. The advice translates them.

```java
@RestControllerAdvice
@Slf4j
public class BankingExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://api.bank.com/errors/insufficient-funds"));
        problem.setTitle("Insufficient Funds");
        problem.setProperty("accountId",  ex.accountId().value());
        problem.setProperty("available",  ex.available().toString());
        problem.setProperty("requested",  ex.requested().toString());
        problem.setProperty("traceId",    currentTraceId());
        problem.setProperty("timestamp",  Instant.now().toString());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(RiskServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleRiskUnavailable(
            RiskServiceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setType(URI.create("https://api.bank.com/errors/risk-service-unavailable"));
        problem.setProperty("retryAfter", 10);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    // Catch-all — log full detail, return sanitized response
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setDetail("An unexpected error occurred. Reference: " + currentTraceId());
        return ResponseEntity.internalServerError().body(problem);
    }
}
```

#### Log or throw — never both

Logging an exception and then re-throwing it causes double-logging. Infrastructure adapters log at the boundary where they have full context. Domain and application layers throw; they do not log.

```java
// Bad — double logging
} catch (EventStoreException ex) {
    log.error("EventStore save failed", ex);   // logs here
    throw ex;                                   // caller logs again
}

// Good — throw; the @RestControllerAdvice or the Kafka error handler logs once
} catch (ExecutionException ex) {
    throw new EventStoreException("Failed to save " + aggregateId, ex);
}
```

---

### 0.7 Spring Annotation Discipline

#### `@Transactional` belongs only in the application layer

The `@Transactional` annotation may only appear in classes in the `application/` package. Never in `domain/`, never in `infrastructure/` adapters that are called directly from domain logic, and never on repository implementations (they participate in an already-open transaction).

```java
// Bad — @Transactional in infrastructure adapter
@Repository
@Transactional  // wrong layer
public class EventStoreDBAccountRepository { ... }

// Bad — @Transactional propagates into domain
@Transactional
public class AccountAggregate { ... }

// Good — @Transactional in application layer command handler
@Service
public class OpenAccountCommandHandler {
    @Transactional  // correct: application layer, PostgreSQL TX scope
    protected void persistReadModelAndOutbox(AccountAggregate account) { ... }
}
```

#### Use the most specific propagation explicitly

Do not rely on the `@Transactional` default propagation (`REQUIRED`) silently joining an outer transaction when that is not intended. The `OutboxWriter` uses `MANDATORY` to explicitly require an existing transaction — this is intentional and documents the contract:

```java
@Transactional(propagation = Propagation.MANDATORY)
// "MANDATORY means: I must be called within an open transaction.
//  If no transaction is active, this fails immediately with a clear error.
//  This is intentional: the outbox write must be atomic with the business write."
public void write(DomainEvent event, String topic) { ... }
```

#### `@Component` vs `@Service` vs `@Repository` — use the most specific

- `@Repository` — data access adapters. Enables Spring's exception translation.
- `@Service` — application-layer use cases and infrastructure services.
- `@Component` — infrastructure beans that are neither repositories nor services (e.g., `OutboxWriter`, `SensitiveDataMaskingSerializer`).
- Never use `@Component` as a catch-all on application-layer classes — use `@Service`.

#### Inject via constructor, never via field injection

Field injection (`@Autowired` on a field) produces objects that cannot be instantiated without a Spring context, making unit tests require `@SpringBootTest` or `@ExtendWith(MockitoExtension.class)` hacks. Constructor injection produces plain objects testable with `new`.

```java
// Banned — field injection
@Service
public class RiskScoringClient {
    @Autowired private RestClient restClient;       // untestable without Spring
    @Autowired private CircuitBreaker circuitBreaker;
}

// Required — constructor injection (Lombok @RequiredArgsConstructor generates this)
@Service
@RequiredArgsConstructor
public class RiskScoringClient {
    private final RestClient     restClient;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry  meterRegistry;
}
```

---

### 0.8 Domain Purity Rules

These rules are enforced by the ArchUnit test in §0.2. Violations fail the build.

#### No Spring annotations in `domain/`

```java
// Banned anywhere under domain/
@Component class AccountAggregate { }       // Spring dependency in domain
@Transactional void debit(Money amount) { } // infrastructure concern in domain
@Value("${limit}") BigDecimal limit;        // config injection in domain
```

#### No infrastructure types in `domain/`

Domain classes must not reference: `javax.persistence.*`, `jakarta.persistence.*`, `org.hibernate.*`, `org.springframework.*`, `io.lettuce.*`, `com.eventstore.*`, `org.apache.kafka.*`.

#### Domain services are plain Java classes with domain-only dependencies

```java
// Good — domain service: only domain types, no Spring, no infra
public class DoubleEntryService {
    public void validateJournalEntry(List<LedgerEntryCommand> entries) {
        BigDecimal debitSum  = sumByType(entries, DebitCredit.DEBIT);
        BigDecimal creditSum = sumByType(entries, DebitCredit.CREDIT);
        if (debitSum.compareTo(creditSum) != 0)
            throw new UnbalancedJournalEntryException(debitSum, creditSum);
    }
}
```

#### Repository interfaces live in `domain/` — implementations live in `infrastructure/`

```java
// domain/repository/AccountRepository.java — interface only, no JPA annotations
public interface AccountRepository {
    void save(AccountAggregate account, long expectedVersion);
    AccountAggregate load(String accountId);
    Optional<AccountAggregate> loadOptional(String accountId);
}

// infrastructure/persistence/EventStoreDBAccountRepository.java — Spring @Repository here
@Repository
public class EventStoreDBAccountRepository implements AccountRepository { ... }
```

---

### 0.9 Concurrency

#### Virtual threads — use for I/O-bound blocking work

Java 21 virtual threads (Project Loom) are used for the outbox relay and any blocking I/O that would otherwise hold a platform thread. Virtual threads are cheap — tens of thousands can exist concurrently without pool exhaustion.

```java
// Good — virtual thread for the long-running LISTEN/NOTIFY loop
Thread.ofVirtual().name("outbox-relay-" + serviceName).start(this::relayLoop);

// Good — virtual thread executor for parallel blocking calls
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<RiskScoreResult> riskFuture = executor.submit(() -> riskClient.score(request));
    // ... other parallel work
    RiskScoreResult result = riskFuture.get(48, TimeUnit.MILLISECONDS);
}
```

#### Never use `Thread.sleep()` as a backoff mechanism without interrupt handling

`Thread.sleep()` in a retry loop must handle `InterruptedException` by restoring the interrupt flag and exiting, not by swallowing it. Swallowing `InterruptedException` makes the thread unshuttable.

```java
// Bad — swallows InterruptedException; JVM cannot shut down cleanly
try {
    Thread.sleep(20L * attempts);
} catch (InterruptedException ignored) { }

// Correct — restore interrupt flag; retry loop respects shutdown
private void backoff(int attemptNumber) {
    try {
        long delayMs = Math.min(20L * (1L << attemptNumber), 200L); // exponential, capped at 200ms
        Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt(); // restore flag
        throw new CommandExecutionInterruptedException(
            "Command handler interrupted during backoff", ex);
    }
}
```

#### No `synchronized` on aggregate methods

Aggregates are not thread-safe by design. They must not be shared across threads. Each command handler loads a fresh aggregate instance from the repository, processes it, and saves it. Concurrency is handled at the EventStoreDB level via `ExpectedRevision`, not via Java synchronization.

#### `CompletableFuture` — always specify the executor explicitly

Never call `CompletableFuture.supplyAsync(...)` without the second `Executor` argument. The default `ForkJoinPool.commonPool()` is shared JVM-wide and will be starved by blocking calls.

```java
// Bad — uses ForkJoinPool.commonPool() which can starve under load
CompletableFuture.supplyAsync(() -> eventStoreClient.appendToStream(...));

// Good — explicit executor, virtual thread per task
private static final ExecutorService VIRTUAL_EXECUTOR =
    Executors.newVirtualThreadPerTaskExecutor();

CompletableFuture.supplyAsync(() -> eventStoreClient.appendToStream(...), VIRTUAL_EXECUTOR);
```

---

### 0.10 Money & Precision

#### `BigDecimal` rules — absolute, no exceptions

```java
// Banned — floating point is not exact
double amount = 0.1 + 0.2;  // = 0.30000000000000004

// Banned — BigDecimal from double inherits the imprecision
new BigDecimal(0.1)  // = 0.1000000000000000055511151231257827021181583404541015625

// Required — BigDecimal from String is exact
new BigDecimal("0.10")  // = exactly 0.10
```

**Canonical `Money` construction from user input:**
```java
// API layer — parse from String, never from double
BigDecimal amount = new BigDecimal(request.getAmount()); // request.getAmount() is a String

// Arithmetic always uses HALF_EVEN (banker's rounding)
BigDecimal result = a.add(b);                                     // addition: exact
BigDecimal result = a.multiply(rate).setScale(4, HALF_EVEN);      // multiplication: round after
BigDecimal result = a.divide(b, 4, HALF_EVEN);                    // division: always specify scale
```

#### Risk scores are computed in `long`, clamped, then cast to `int`

```java
// Correct pattern — accumulate in long, clamp before cast
long rawScore = signals.stream()
    .mapToLong(s -> (long) s.evaluate(context))
    .sum();

int score = (int) Math.min(rawScore * 10L, 1000L); // long arithmetic throughout; safe cast after clamp
```

#### Database columns for monetary amounts use `NUMERIC(19,4)`

Never `FLOAT`, never `DOUBLE PRECISION` in PostgreSQL for financial amounts. Always `NUMERIC(19,4)`. The `4` decimal places matches the `Money` value object's scale.

---

### 0.11 Testing Standards

> The detailed testing patterns are defined in **§22 Testing Strategy**. This section records the mandatory rules enforced by CI.

#### Test structure: Given / When / Then — always

Every test method follows GWT, either as comments or as natural paragraph structure. This is enforced in code review.

```java
@Test
void should_block_transfer_when_risk_decision_is_block() {
    // Given
    TransferAggregate transfer = initiatedTransfer();
    EventMetadata meta = testMetadata();

    // When
    transfer.applyRiskScore(850, RiskLevel.CRITICAL, RiskDecision.BLOCK,
                            List.of("VELOCITY_HIGH", "AMOUNT_UNUSUAL"), 30L, meta);

    // Then
    assertThat(transfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);
    assertThat(transfer.getUncommittedEvents())
        .extracting(e -> e.getClass().getSimpleName())
        .containsExactly("TransferRiskScoredEvent", "TransferBlockedEvent");
}
```

#### Domain unit tests use no mocks — ever

The `domain/` package must be testable with zero mocking frameworks. If a domain test requires Mockito, the domain class has a Spring or infrastructure dependency it should not have.

```java
// Good — pure domain test: new up the aggregate, call methods, assert state
@Test
void should_throw_when_reversing_a_transfer_with_no_executed_debit() {
    TransferAggregate transfer = TransferAggregate.initiate(anInitiateTransferCommand());
    // transfer.debitExecuted == false at this point

    assertThatThrownBy(() ->
        transfer.initiateReversal(ReversalReason.CREDIT_FAILED, "entry-1", testMetadata()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("debit was not executed");
}
```

#### Application layer tests use mocks for ports (repository interfaces)

Command handler tests mock the repository interfaces defined in `domain/`. They never mock concrete infrastructure classes.

```java
// Good — mock the domain port interface
@ExtendWith(MockitoExtension.class)
class OpenAccountCommandHandlerTest {

    @Mock AccountRepository  accountRepository;   // domain interface — correct
    @Mock OutboxWriter       outboxWriter;
    @InjectMocks OpenAccountCommandHandler handler;

    @Test
    void should_persist_account_and_write_outbox_on_open() { ... }
}

// Bad — mock the infrastructure implementation
@Mock EventStoreDBAccountRepository accountRepository;  // couples test to infra
```

#### Integration tests use `@Testcontainers` with real dependencies

Every service has one integration test class that spins up real EventStoreDB, Kafka, PostgreSQL, and Redis containers. `@SpringBootTest` integration tests must not use `H2` or in-memory databases — the production database must be tested.

#### Coverage gates (enforced by JaCoCo in CI)

| Module | Line coverage | Branch coverage |
|---|---|---|
| `domain/` | ≥ 95% | ≥ 90% |
| `application/` | ≥ 85% | ≥ 80% |
| `infrastructure/` | ≥ 75% | — |
| `api/` | ≥ 80% | — |

---

### 0.12 Observability

#### Loggers are `private static final` SLF4J loggers — always

```java
// Required pattern — every class that logs
private static final Logger log = LoggerFactory.getLogger(AccountEventProjector.class);

// Banned — injected logger (Spring is not the logging lifecycle owner)
@Autowired Logger log;

// Banned — java.util.logging or System.out
System.out.println("Account created: " + accountId);
```

Lombok's `@Slf4j` generates the correct pattern. Use it or write the declaration manually. Both are acceptable.

#### MDC carries correlation context on every log line

The correlation ID from `X-Correlation-ID` and the transfer ID (when in the transfer critical path) must be in the MDC for every log statement in that request's thread.

```java
// Servlet filter — sets MDC at request boundary
@Component
public class MdcPopulatingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional
            .ofNullable(request.getHeader("X-Correlation-ID"))
            .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put("correlationId", correlationId);
        MDC.put("requestPath",   request.getRequestURI());
        MDC.put("httpMethod",    request.getMethod());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // always clear — thread pool reuse
        }
    }
}
```

#### Structured logging — no string concatenation in log statements

```java
// Banned — concatenation evaluates even when log level is disabled
log.debug("Processing transfer " + transferId + " for customer " + customerId);

// Required — SLF4J parameterized logging; arguments evaluated only if level is enabled
log.debug("Processing transfer {} for customer {}", transferId, customerId);

// For complex objects — use structured key-value (Logstash markers or structured-logging-logback)
log.info("Transfer completed",
    kv("transferId",  transferId),
    kv("customerId",  customerId),
    kv("amountEur",   amountInEur),
    kv("riskScore",   riskScore),
    kv("durationMs",  duration));
```

#### Sensitive data never in logs

PAN, IBAN (unmasked), account numbers, and customer PII must never appear in log statements. The `SensitiveDataMaskingFilter` (§13.4) is a safety net — not a license to write them carelessly.

```java
// Banned — IBAN in log
log.info("Transfer from IBAN {} to IBAN {}", sourceIban, destIban);

// Required — masked value
log.info("Transfer from {} to {}", sourceIban.masked(), destIban.masked());
```

---

### 0.13 Performance Discipline

#### Measure before optimizing

Never optimize code that has not been profiled under realistic load. The Gatling tests in Phase 5 are the source of truth. If a SLO is missed, profile first with async-profiler, then optimize the proven hot path.

#### Stream API — collect early when size is known

```java
// Bad — repeated stream evaluation
List<DomainEvent> events = aggregate.getUncommittedEvents();
if (events.stream().anyMatch(e -> e instanceof AccountOpenedEvent)) {
    events.stream()
          .filter(e -> e instanceof AccountOpenedEvent)
          .findFirst()...  // traverses stream twice
}

// Good — collect once, work with the list
List<DomainEvent> events = aggregate.getUncommittedEvents();
events.forEach(event -> {
    switch (event) {
        case AccountOpenedEvent e  -> readModelRepository.save(AccountReadModel.from(e));
        case AccountFrozenEvent e  -> readModelRepository.updateStatus(e.accountId(), FROZEN);
        default -> {}
    }
});
```

#### HikariCP pool sizing — one config, one place

Connection pool sizes are set in `application.yml` (Section 16.3). Never create additional `DataSource` beans with separate pool configs in feature code. Pool exhaustion is one of the most common causes of cascading failures.

#### Avoid `N+1` in read model projectors

Kafka projectors that receive batches of events must not execute one database query per event. Batch with `saveAll()` or use `ON CONFLICT DO UPDATE` for upsert patterns.

```java
// Bad — N queries for N events in batch
events.forEach(event -> readModelRepository.save(readModelFrom(event)));

// Good — single batched upsert
List<AccountReadModel> batch = events.stream()
    .map(this::toReadModel)
    .toList();
readModelRepository.saveAll(batch);  // Spring Data generates one batched INSERT
```

---

### 0.14 Forbidden Patterns

These patterns are banned. CI linters catch most of them. Code review catches the rest. Exceptions require a comment explaining the justification.

#### No raw types

```java
// Banned — raw type loses generic safety
List list = new ArrayList();
list.add("a string");
list.add(42);  // no compile error — runtime ClassCastException later

// Required — parameterized
List<DomainEvent> events = new ArrayList<>();
```

#### No `==` for String or object equality

```java
// Banned — reference equality on Strings
if (status == "ACTIVE") { ... }      // true only by accident (interning)
if (accountId == other.accountId) { ... }  // always false for heap objects

// Required
if ("ACTIVE".equals(status)) { ... }  // or use the enum: status == AccountStatus.ACTIVE
if (accountId.equals(other.accountId)) { ... }
```

#### No `var` where the type is not immediately obvious

`var` is useful when the type is verbose and obvious from the right-hand side. It is harmful when the type is not clear from reading the line.

```java
// Good — type is obvious from the right-hand side
var accountId = new AccountId("ACC-001");
var events    = new ArrayList<DomainEvent>();

// Bad — requires navigating to method signature to understand the type
var result = handler.handle(command);  // what type is result?
var data   = repository.findById(id);  // Optional<Account>? Account? List?
```

#### No `@SneakyThrows` in business logic

Lombok's `@SneakyThrows` silently converts checked exceptions to unchecked ones without declaring them. In domain and application code, this hides failure modes. Declare or translate the exception explicitly.

```java
// Banned in domain/application code
@SneakyThrows
public void save(AccountAggregate account, long expectedVersion) { ... }

// Required — handle or translate explicitly
public void save(AccountAggregate account, long expectedVersion) {
    try {
        client.appendToStream(streamName, options, data.iterator()).get();
    } catch (ExecutionException ex) {
        if (ex.getCause() instanceof WrongExpectedVersionException wev) throw wev;
        throw new EventStoreException("Save failed for " + account.getAggregateId(), ex);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new EventStoreException("Interrupted during save", ex);
    }
}
```

#### No `static` mutable state

```java
// Banned — mutable static state corrupts parallel tests and pods
private static int requestCount = 0;
private static List<String> processedIds = new ArrayList<>();

// Correct — inject stateful collaborators
@Service
@RequiredArgsConstructor
public class TransferCommandHandler {
    private final IdempotencyStore idempotencyStore;  // injected, not static
}
```

**Justified static fields:** `private static final Logger log`, `private static final Pattern IBAN_PATTERN`, `private static final String STREAM_PREFIX`, and `KafkaTopics`/`ConsumerGroups` constants. These are all immutable.

#### No `Optional.get()` without `isPresent()` check

```java
// Banned — throws NoSuchElementException if empty
Optional<AccountAggregate> account = repository.loadOptional(accountId);
return account.get();  // crashes if absent

// Required — express the absent case explicitly
return repository.loadOptional(accountId)
    .orElseThrow(() -> new AggregateNotFoundException("account", accountId));
```

#### No `printStackTrace()`

```java
// Banned
} catch (Exception e) {
    e.printStackTrace();  // goes to stdout, not the structured log pipeline
}

// Required
} catch (Exception e) {
    log.error("Failed to process event {}: {}", eventId, e.getMessage(), e);
    throw new EventProcessingException("Processing failed for " + eventId, e);
}
```

---

### 0.15 Code Review Checklist

Every PR must pass this checklist before merge. Items marked ★ are automated by CI.

**Domain integrity**
- [ ] ★ No Spring/JPA/Hibernate imports in `domain/` (ArchUnit)
- [ ] All aggregate state changes go through `apply(DomainEvent)` — no direct field mutation
- [ ] Every new domain exception extends `DomainException`, carries context fields, has a meaningful message
- [ ] Collections returned from aggregates are unmodifiable
- [ ] Value objects use records with validation in compact constructor

**Null safety & immutability**
- [ ] ★ No unannotated nullable references in public APIs (NullAway)
- [ ] `Objects.requireNonNull` with descriptive message at every public constructor and factory method
- [ ] No `Optional.get()` without guard
- [ ] No mutable static fields

**Money & precision**
- [ ] ★ No `double` or `float` for monetary values (SpotBugs rule)
- [ ] All `BigDecimal` arithmetic uses explicit scale and `HALF_EVEN`
- [ ] `Money` value object used for all monetary values — not raw `BigDecimal`

**Error handling**
- [ ] No swallowed exceptions (empty catch blocks)
- [ ] `InterruptedException` always restores interrupt flag: `Thread.currentThread().interrupt()`
- [ ] Exception-to-HTTP mapping only in `@RestControllerAdvice`
- [ ] No `e.printStackTrace()`

**Spring discipline**
- [ ] `@Transactional` only in `application/` layer
- [ ] Constructor injection only — no `@Autowired` on fields
- [ ] `@Component`/`@Service`/`@Repository` on correct layer types
- [ ] No `@SneakyThrows` in domain or application code

**Concurrency**
- [ ] `Thread.sleep()` handles `InterruptedException` correctly
- [ ] `CompletableFuture.supplyAsync()` specifies explicit executor
- [ ] No `synchronized` on aggregate methods

**Logging & observability**
- [ ] ★ No `System.out.println` (Checkstyle)
- [ ] Logger is `private static final` via SLF4J
- [ ] No string concatenation in log arguments — parameterized only
- [ ] No unmasked IBAN, PAN, or account numbers in log statements
- [ ] MDC correlation fields set before any log statement in request scope

**Testing**
- [ ] ★ Domain module coverage ≥ 95% line, ≥ 90% branch (JaCoCo)
- [ ] Domain tests use zero mocks
- [ ] Application tests mock domain port interfaces only — not infra implementations
- [ ] Every test follows Given / When / Then structure
- [ ] Integration tests use real containers (`@Testcontainers`) — no H2

**General**
- [ ] ★ No raw types (Checkstyle/SpotBugs)
- [ ] ★ `==` not used for String or object equality (SpotBugs)
- [ ] Kafka topic strings from `KafkaTopics` constants — no string literals
- [ ] Consumer group IDs from `ConsumerGroups` constants
- [ ] `var` used only where type is obvious from the right-hand side

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
`Math.min(totalScore * 10, 1000)` in the risk scorer uses `int` arithmetic which silently overflows for large signal scores. Risk score must be computed in `long` or `BigDecimal`. See §0.10 and §12.2.

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
// shared-kernel module — zero Spring, zero infrastructure deps
// All value objects are records: immutable, validated on construction (§0.4)
public record AccountId(String value) {
    public AccountId {
        Objects.requireNonNull(value, "accountId must not be null");
        if (!value.startsWith("ACC-"))
            throw new InvalidAccountIdException(value);
    }
}

public record CustomerId(String value) {
    public CustomerId { Objects.requireNonNull(value, "customerId must not be null"); }
}

public record TransferId(String value) {
    public TransferId { Objects.requireNonNull(value, "transferId must not be null"); }
}

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount,   "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.scale() > 4)
            throw new IllegalArgumentException(
                "Money scale exceeds 4 decimal places: " + amount.toPlainString());
        // Normalize scale on construction — ensures 250 and 250.0000 are equal (§0.10)
        amount = amount.setScale(4, HALF_EVEN);
    }

    // All arithmetic preserves HALF_EVEN rounding — never use double intermediates (§0.10)
    public Money add(Money other)      { assertSameCurrency(other); return new Money(amount.add(other.amount), currency); }
    public Money subtract(Money other) { assertSameCurrency(other); return new Money(amount.subtract(other.amount), currency); }
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(amount.multiply(factor).setScale(4, HALF_EVEN), currency);
    }
    public boolean isNegative()  { return amount.compareTo(BigDecimal.ZERO) < 0; }
    public boolean isLessThan(Money other)    { assertSameCurrency(other); return amount.compareTo(other.amount) < 0; }
    public boolean isGreaterThan(Money other) { assertSameCurrency(other); return amount.compareTo(other.amount) > 0; }
    public static Money zero(Currency currency) { return new Money(BigDecimal.ZERO.setScale(4), currency); }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency))
            throw new CurrencyMismatchException(this.currency, other.currency);
    }
}

public record IBAN(String value) {
    public IBAN { IBANValidator.validate(value); }   // Modulo-97 validation
    public String masked() { return value.substring(0, 4) + "****" + value.substring(value.length() - 4); }
}

public record CorrelationId(String value) {
    public CorrelationId { Objects.requireNonNull(value, "correlationId must not be null"); }
}

public record CausationId(String value) {
    public CausationId { Objects.requireNonNull(value, "causationId must not be null"); }
}
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
│   │   ├── domain/                   # Pure domain — no Spring (enforced by ArchUnit §0.2)
│   │   │   ├── model/                # AccountAggregate, CustomerAggregate
│   │   │   ├── event/                # Sealed AccountEvent hierarchy (records only)
│   │   │   ├── command/              # OpenAccountCommand, etc. (records)
│   │   │   ├── service/              # AccountOpeningService (domain service — plain Java)
│   │   │   ├── repository/           # Repository interfaces (ports)
│   │   │   └── exception/            # AccountFrozenException, etc.
│   │   ├── application/              # Use cases — Spring @Service/@Transactional allowed here
│   │   │   ├── command/              # OpenAccountCommandHandler
│   │   │   └── query/                # GetAccountQueryHandler
│   │   ├── infrastructure/           # Adapters — Spring @Component allowed here
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
    ├── docker-compose.dev.yml
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
    <archunit.version>1.3.0</archunit.version>
    <nullaway.version>0.10.24</nullaway.version>
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
    <!-- ArchUnit — architecture enforcement in tests (§0.2) -->
    <dependency>
        <groupId>com.tngtech.archunit</groupId>
        <artifactId>archunit-junit5</artifactId>
        <version>${archunit.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<!-- CI quality gates — all must pass before build succeeds -->
<build>
    <plugins>
        <!-- Checkstyle: no System.out, raw types, == on strings, missing @Override -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-checkstyle-plugin</artifactId>
            <configuration>
                <configLocation>banking-checkstyle.xml</configLocation>
                <failsOnError>true</failsOnError>
                <includeTestSourceDirectory>true</includeTestSourceDirectory>
            </configuration>
            <executions>
                <execution>
                    <phase>validate</phase>
                    <goals><goal>check</goal></goals>
                </execution>
            </executions>
        </plugin>
        <!-- SpotBugs: float/double for money, null dereference, unchecked cast -->
        <plugin>
            <groupId>com.github.spotbugs</groupId>
            <artifactId>spotbugs-maven-plugin</artifactId>
            <configuration>
                <effort>Max</effort>
                <threshold>Medium</threshold>
                <failOnError>true</failOnError>
            </configuration>
        </plugin>
        <!-- JaCoCo: coverage gates per module (§0.11) -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit><counter>LINE</counter><minimum>0.80</minimum></limit>
                            <limit><counter>BRANCH</counter><minimum>0.75</minimum></limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 5. Shared Kernel

### 5.1 AggregateRoot Base (v2 — corrected)

```java
// shared-kernel — zero Spring imports (enforced by ArchUnit §0.2)
// Fields are private; state changes only through apply(). No setters. (§0.4)
public abstract class AggregateRoot {

    // private — never exposed mutably; returned as unmodifiableList (§0.4)
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private long   version         = -1L;
    private long   snapshotVersion = -1L;
    private String aggregateId;

    /**
     * Apply a new event: update state AND record as uncommitted.
     * Only call from aggregate command methods (never from tests directly).
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
        Objects.requireNonNull(events, "events must not be null");
        events.forEach(e -> {
            handle(e);
            version++;
        });
    }

    /**
     * Restore from snapshot. Repository calls this BEFORE rehydrating tail events.
     */
    public final void restoreFromSnapshot(AggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
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

    // Returns unmodifiable view — caller cannot mutate aggregate state (§0.4)
    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() { uncommittedEvents.clear(); }

    public long   getVersion()         { return version; }
    public long   getSnapshotVersion() { return snapshotVersion; }
    public String getAggregateId()     { return aggregateId; }

    protected void setAggregateId(String id) {
        Objects.requireNonNull(id, "aggregateId must not be null");
        if (this.aggregateId != null)
            throw new IllegalStateException("AggregateId is already set to " + this.aggregateId);
        this.aggregateId = id;
    }
}
```

### 5.2 DomainEvent Base

```java
// All domain events are records implementing this sealed interface (§0.4).
// The sealed hierarchy per aggregate is defined in the domain module.
// Records are immutable by construction — no setters ever.
public interface DomainEvent {
    String  eventId();       // UUIDv4 — globally unique, used as idempotency key
    String  aggregateId();
    long    version();       // aggregate version AFTER this event
    Instant occurredAt();
    String  correlationId(); // original request trace ID, propagated through full chain
    String  causationId();   // eventId of the event that caused this one (or commandId)
    String  schemaVersion(); // "1.0" — increment on breaking changes
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
    public EventMetadata {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(serviceOrigin, "serviceOrigin must not be null");
        Objects.requireNonNull(initiatedBy,   "initiatedBy must not be null");
    }

    public static EventMetadata fromRequest(HttpServletRequest req, String initiatedBy) {
        Objects.requireNonNull(req,          "request must not be null");
        Objects.requireNonNull(initiatedBy,  "initiatedBy must not be null");
        return new EventMetadata(
            req.getHeader("X-Correlation-ID"),
            null,
            Span.current().getSpanContext().getTraceId(),
            Span.current().getSpanContext().getSpanId(),
            initiatedBy,
            System.getenv("SERVICE_NAME")
        );
    }

    public EventMetadata causedBy(String parentEventId) {
        Objects.requireNonNull(parentEventId, "parentEventId must not be null");
        return new EventMetadata(correlationId, parentEventId, traceId, spanId,
                                 initiatedBy, serviceOrigin);
    }
}
```

---

## 6. Event Sourcing & CQRS Infrastructure

### 6.1 EventSourcedRepository — v2 Canonical

```java
// Repository interface lives in domain/ — it is a domain port (§0.8)
// Implementation lives in infrastructure/ (§0.2)
public interface EventSourcedRepository<T extends AggregateRoot> {
    /**
     * Persist uncommitted events. Uses expectedRevision for optimistic concurrency.
     * @param aggregate       the aggregate with uncommitted events
     * @param expectedVersion version before any new events; -1 for new stream
     * @throws WrongExpectedVersionException if concurrent modification detected
     */
    void save(T aggregate, long expectedVersion) throws WrongExpectedVersionException;

    T                load(String aggregateId);
    Optional<T>      loadOptional(String aggregateId);
    List<DomainEvent> loadFrom(String aggregateId, long fromVersion);
}
```

```java
// @Repository annotation allowed here — infrastructure adapter (§0.7)
@Repository
public class EventStoreDBAccountRepository implements EventSourcedRepository<AccountAggregate> {

    // Constants are private static final — named, not magic strings (§0.3)
    private static final String STREAM_PREFIX  = "account-";
    private static final int    MAX_READ_COUNT = 4096;

    // Logger: private static final SLF4J — never @Autowired (§0.12)
    private static final Logger log =
        LoggerFactory.getLogger(EventStoreDBAccountRepository.class);

    // Constructor injection — no @Autowired on fields (§0.7)
    private final EventStoreDBClient    client;
    private final DomainEventSerializer serializer;
    private final SnapshotService       snapshotService;

    public EventStoreDBAccountRepository(EventStoreDBClient client,
                                          DomainEventSerializer serializer,
                                          SnapshotService snapshotService) {
        this.client          = Objects.requireNonNull(client);
        this.serializer      = Objects.requireNonNull(serializer);
        this.snapshotService = Objects.requireNonNull(snapshotService);
    }

    @Override
    public void save(AccountAggregate aggregate, long expectedVersion) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
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
            // Unwrap — WrongExpectedVersionException is the domain-meaningful case (§0.6)
            if (ex.getCause() instanceof WrongExpectedVersionException wev) throw wev;
            throw new EventStoreException(
                "Failed to save aggregate %s".formatted(aggregate.getAggregateId()), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // restore interrupt flag (§0.9)
            throw new EventStoreException("Interrupted during save of " + aggregate.getAggregateId(), ex);
        }

        snapshotService.saveIfNeeded(aggregate);
        aggregate.markEventsAsCommitted();
    }

    @Override
    public AccountAggregate load(String accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return loadOptional(accountId)
            .orElseThrow(() -> new AggregateNotFoundException("account", accountId));
    }

    @Override
    public Optional<AccountAggregate> loadOptional(String accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Optional<AccountSnapshot> snapshot = snapshotService.findLatest(accountId);
        AccountAggregate account = new AccountAggregate();

        long startFromVersion = 0L;
        if (snapshot.isPresent()) {
            account.restoreFromSnapshot(snapshot.get());
            startFromVersion = snapshot.get().version() + 1;
        }

        List<DomainEvent> tailEvents = loadFrom(accountId, startFromVersion);
        if (tailEvents.isEmpty() && snapshot.isEmpty()) return Optional.empty();

        account.rehydrate(tailEvents);
        return Optional.of(account);
    }

    @Override
    public List<DomainEvent> loadFrom(String aggregateId, long fromVersion) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
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
            if (ex.getCause() instanceof StreamNotFoundException)
                return Collections.emptyList();
            throw new EventStoreException("Failed to load stream " + streamName, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // restore interrupt flag (§0.9)
            throw new EventStoreException("Interrupted while loading " + streamName, ex);
        }
    }
}
```

### 6.2 Snapshot Service

```java
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    // Thresholds are immutable static final — justified exception to no-mutable-statics rule (§0.14)
    private static final Map<Class<?>, Integer> THRESHOLDS = Map.of(
        AccountAggregate.class,       500,
        RiskProfileAggregate.class,   500,
        CustomerAggregate.class,      200,
        TransferLimitAggregate.class, 100
    );

    private final SnapshotRepository snapshotRepository;

    public SnapshotService(SnapshotRepository snapshotRepository) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository);
    }

    public <T extends AggregateRoot> void saveIfNeeded(T aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        int  threshold             = THRESHOLDS.getOrDefault(aggregate.getClass(), Integer.MAX_VALUE);
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

**Do NOT use `@Retryable` on command handlers blindly.** The v1 approach retried `DebitAccountCommand` automatically, which risks double-debit. The correct pattern reloads the aggregate, re-validates domain invariants, and only proceeds if the business case is still valid.

Note the backoff helper below: `Thread.sleep()` must handle `InterruptedException` by restoring the interrupt flag — not by swallowing it (§0.9).

```java
@Service
public class DebitAccountCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(DebitAccountCommandHandler.class);
    private static final int    MAX_ATTEMPTS = 3;

    private final AccountRepository accountRepository;

    public DebitAccountCommandHandler(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    @Transactional  // application layer — correct (§0.7)
    public TransferResult handle(DebitAccountCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            AccountAggregate account = accountRepository.load(command.accountId().value());

            // Re-validate with fresh state — if funds are still insufficient, fail immediately
            account.validateCanDebit(command.amount());

            try {
                account.debit(command.amount(), command.transferId(), command.metadata());
                long versionBeforeEvents = account.getVersion()
                    - account.getUncommittedEvents().size();
                accountRepository.save(account, versionBeforeEvents);
                return TransferResult.debited(account.getVersion());

            } catch (WrongExpectedVersionException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ConcurrentModificationException(
                        "Account %s modified concurrently after %d attempts"
                            .formatted(command.accountId(), MAX_ATTEMPTS), ex);
                }
                log.debug("WrongExpectedVersion on attempt {} for account {} — reloading",
                          attempt, command.accountId().value());
                backoff(attempt); // exponential backoff with interrupt safety
            }
        }
        throw new IllegalStateException("Unreachable: loop exits via return or exception");
    }

    /**
     * Exponential backoff. Restores interrupt flag on InterruptedException — never swallows it.
     * Swallowing InterruptedException prevents clean JVM shutdown (§0.9).
     */
    private void backoff(int attempt) {
        try {
            long delayMs = Math.min(20L * (1L << attempt), 200L); // 40ms, 80ms, capped at 200ms
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionInterruptedException(
                "Command handler interrupted during backoff", ex);
        }
    }
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

CREATE INDEX idx_outbox_pending ON outbox_messages (created_at ASC) WHERE status = 'PENDING';
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
// @Component — infrastructure bean, not a domain service (§0.7)
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);

    private final OutboxRepository       outboxRepository;
    private final EventEnvelopeSerializer serializer;

    /**
     * Write an integration event to the outbox within the caller's active transaction.
     *
     * Propagation.MANDATORY means: this MUST be called from within an open @Transactional
     * context. If no transaction is active, Spring throws immediately with a clear error.
     * This is intentional — the outbox write must be atomic with the business write (§0.7).
     *
     * Never call kafkaTemplate.send() directly from command handlers.
     * The relay handles publishing asynchronously.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(DomainEvent event, String topic) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(topic, "topic must not be null");

        byte[] payload = serializer.toAvro(EventEnvelope.wrap(event));

        OutboxMessage message = OutboxMessage.builder()
            .id(UUID.randomUUID())
            .aggregateId(event.aggregateId())
            .aggregateType(extractAggregateType(event))
            .eventId(event.eventId())           // UNIQUE — prevents duplicate outbox entries
            .eventType(event.getClass().getName())
            .topic(topic)
            .partitionKey(event.aggregateId())  // guarantees ordering per aggregate
            .payload(payload)
            .headers(buildKafkaHeaders(event))
            .status(OutboxStatus.PENDING)
            .build();

        outboxRepository.save(message);
        // The AFTER INSERT trigger fires pg_notify automatically — no manual notification needed
    }

    private Map<String, String> buildKafkaHeaders(DomainEvent event) {
        // Use null-safe toString — causationId is legitimately nullable on initial commands
        return Map.of(
            "eventId",       event.eventId(),
            "correlationId", event.correlationId(),
            "causationId",   Objects.toString(event.causationId(), ""),
            "schemaVersion", event.schemaVersion(),
            "eventType",     event.getClass().getSimpleName()
        );
    }
}
```

### 7.3 Outbox Relay — LISTEN/NOTIFY + Fallback Poll

```java
// @Service — infrastructure service bean (§0.7)
@Service
@Slf4j
public class OutboxRelay {

    // Named constants — never magic numbers (§0.3)
    private static final int      BATCH_SIZE    = 200;
    private static final int      MAX_RETRIES   = 5;
    private static final Duration FALLBACK_POLL = Duration.ofMillis(500);
    // 100ms fallback implemented via pgConn.getNotifications(timeout) — not Thread.sleep (§0.9)

    private final DataSource                      dataSource;
    private final OutboxRepository                outboxRepository;
    private final KafkaTemplate<String, byte[]>   kafkaTemplate;

    public OutboxRelay(DataSource dataSource,
                       OutboxRepository outboxRepository,
                       KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.dataSource      = Objects.requireNonNull(dataSource);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.kafkaTemplate   = Objects.requireNonNull(kafkaTemplate);
    }

    /**
     * Start LISTEN/NOTIFY connection on a virtual thread (§0.9).
     * Virtual threads are cheap — one blocking LISTEN connection does not waste a platform thread.
     * Falls back to polling every 500ms if a NOTIFY is missed.
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
                // getNotifications() blocks up to FALLBACK_POLL waiting for a NOTIFY.
                // Whether notified or timed out, process the batch.
                // This is the correct alternative to Thread.sleep() for this blocking pattern (§0.9).
                pgConn.getNotifications((int) FALLBACK_POLL.toMillis());
                processBatch();
            }
        } catch (SQLException ex) {
            log.error("Outbox relay LISTEN connection failed — restarting in 5s", ex);
            restartAfterDelay();
        }
    }

    /**
     * Restart after connection failure.
     * Uses Thread.sleep() here — the only place in this class — with correct
     * interrupt handling: restores flag and exits (§0.9).
     */
    private void restartAfterDelay() {
        try {
            Thread.sleep(5_000);
            start();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // restore flag — do not swallow
            log.warn("Outbox relay restart interrupted — shutting down");
        }
    }

    @Transactional
    public void processBatch() {
        // FOR UPDATE SKIP LOCKED — safe for concurrent relay instances (§0.9)
        List<OutboxMessage> batch = outboxRepository.lockPendingBatch(BATCH_SIZE);
        if (batch.isEmpty()) return;

        List<UUID> successIds = new ArrayList<>();
        List<UUID> failIds    = new ArrayList<>();

        for (OutboxMessage msg : batch) {
            try {
                kafkaTemplate.send(msg.topic(), msg.partitionKey(), msg.payload())
                    .get(5, TimeUnit.SECONDS);
                successIds.add(msg.id());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // restore flag (§0.9)
                log.warn("Relay interrupted during publish for event {}", msg.eventId());
                failIds.add(msg.id());
                break; // exit batch — thread is being shut down
            } catch (Exception ex) {
                log.warn("Outbox publish failed for event {} (attempt {}): {}",
                         msg.eventId(), msg.retryCount() + 1, ex.getMessage());
                failIds.add(msg.id());
            }
        }

        if (!successIds.isEmpty()) outboxRepository.markPublished(successIds);
        if (!failIds.isEmpty())    outboxRepository.incrementRetryCount(failIds);
        outboxRepository.markDeadIfExceeded(MAX_RETRIES);
    }
}
```

```sql
-- lockPendingBatch — used by relay; FOR UPDATE SKIP LOCKED prevents double-publish
SELECT * FROM outbox_messages
WHERE  status = 'PENDING'
ORDER  BY created_at ASC
LIMIT  :batchSize
FOR UPDATE SKIP LOCKED;
```

### 7.4 Canonical Command Handler Pattern (with Outbox)

```java
// @Service in application layer — @Transactional allowed here (§0.7)
// Constructor injection — no @Autowired on fields (§0.7)
@Service
@RequiredArgsConstructor
public class OpenAccountCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenAccountCommandHandler.class);

    private final EventStoreDBAccountRepository accountRepository;
    private final OutboxWriter                  outboxWriter;
    private final AccountReadModelRepository    readModelRepository;
    private final CustomerQueryService          customerQueryService;

    /**
     * Write order — chosen to minimize data loss on partial failure:
     *
     * 1) EventStoreDB append — the authoritative event log
     * 2) PostgreSQL TX (read model + outbox) — atomic with each other
     *
     * Failure modes:
     * - EventStoreDB succeeds, PostgreSQL TX fails → reconciliation job re-derives
     *   read model from EventStoreDB on next startup (acceptable: RPO < 1 min)
     * - PostgreSQL TX succeeds, EventStoreDB fails → retry EventStoreDB append
     *   with same expectedRevision (idempotent — safe to repeat)
     * - Both succeed → normal path
     */
    public String handle(OpenAccountCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Step 1: validate pre-conditions (outside transaction — read-only)
        CustomerReadModel customer = customerQueryService.getCustomer(command.customerId());
        if (!customer.isKycApproved()) {
            throw new KycNotApprovedException(command.customerId());
        }

        // Step 2: execute domain logic — no transaction yet
        AccountAggregate account = AccountAggregate.open(command);

        // Step 3: persist to EventStoreDB — NOT in PostgreSQL transaction
        accountRepository.save(account, -1L); // -1 = NO_STREAM (new aggregate)

        // Step 4: PostgreSQL transaction — read model + outbox atomically
        persistReadModelAndOutbox(account);

        return account.getAggregateId();
    }

    @Transactional  // PostgreSQL only — EventStoreDB is already persisted above
    protected void persistReadModelAndOutbox(AccountAggregate account) {
        // Project events to read model
        account.getUncommittedEvents().forEach(event -> {
            switch (event) {
                case AccountOpenedEvent e ->
                    readModelRepository.save(AccountReadModel.from(e));
                default ->
                    log.debug("Skipping read model update for event type {}",
                              event.getClass().getSimpleName());
            }
        });

        // Write to outbox — same transaction as read model update
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

- Events are **past-tense immutable facts** encoded as Java `record` types (§0.4)
- All events carry: `eventId`, `aggregateId`, `version`, `occurredAt`, `correlationId`, `causationId`, `schemaVersion`
- Events are serialized as **Avro** for Kafka (schema evolution enforcement via Confluent Schema Registry)
- Events are serialized as **JSON** for EventStoreDB (human-readable stream browsing)
- Sensitive fields (`iban`, `accountNumber`, `pan`) are **never logged in plaintext** — masked or encrypted before publication (§0.12)
- Schema changes follow semantic versioning; breaking changes increment the major schema version and require a migration topic

### 8.2 Account Events

```java
// Sealed interface: compiler enforces exhaustive pattern matching in switch expressions
public sealed interface AccountEvent extends DomainEvent
    permits AccountOpenedEvent, AccountActivatedEvent, AccountFrozenEvent,
            AccountUnfrozenEvent, AccountClosingInitiatedEvent, AccountClosedEvent,
            AccountBlockedEvent, AccountLimitChangedEvent {}

public record AccountOpenedEvent(
    String      eventId,
    String      accountId,
    String      customerId,
    String      iban,           // masked before publication to Kafka (§13.4)
    String      productCode,
    AccountType type,
    Currency    currency,
    String      correlationId,
    String      causationId,
    long        version,
    Instant     occurredAt,
    String      schemaVersion
) implements AccountEvent {
    public static final String SCHEMA_VERSION = "1.0";
}

public record AccountFrozenEvent(
    String      eventId,
    String      accountId,
    FreezeReason reason,        // enum: FRAUD_DETECTED, AML_HOLD, CUSTOMER_REQUEST, REGULATORY_ORDER
    String      initiatedBy,
    String      notes,
    String      correlationId,
    String      causationId,
    long        version,
    Instant     occurredAt,
    String      schemaVersion
) implements AccountEvent {
    public static final String SCHEMA_VERSION = "1.0";
}
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
    String      destinationIban,       // null for internal; masked if logged (§0.12)
    String      destinationBic,
    Money       amount,
    Money       fxAmount,
    PaymentRail rail,
    String      remittanceInfo,
    TransferType type,
    String      initiatedBy,
    String      correlationId,
    String      causationId,
    long        version,
    Instant     occurredAt,
    String      schemaVersion
) implements TransferEvent {
    public static final String SCHEMA_VERSION = "1.0";
}

public record TransferRiskScoredEvent(
    String         eventId,
    String         transferId,
    int            riskScore,           // 0–1000; computed in long, clamped, then cast (§0.10 / §12.2)
    RiskLevel      riskLevel,
    RiskDecision   decision,            // APPROVE / REVIEW / BLOCK
    List<String>   triggeredSignalIds,
    long           scoringDurationMs,
    String         correlationId,
    String         causationId,
    long           version,
    Instant        occurredAt,
    String         schemaVersion
) implements TransferEvent {
    public static final String SCHEMA_VERSION = "1.0";
}

public record TransferReversedEvent(
    String         eventId,
    String         transferId,
    String         originalDebitEntryId,
    Money          reversedAmount,
    ReversalReason reason,
    String         correlationId,
    String         causationId,
    long           version,
    Instant        occurredAt,
    String         schemaVersion
) implements TransferEvent {
    public static final String SCHEMA_VERSION = "1.0";
}
```

### 8.4 Kafka Topic Registry (Canonical)

```java
// All topic strings are constants here — never hardcode in @KafkaListener or producers (§0.3)
public final class KafkaTopics {
    public static final String ACCOUNT_EVENTS     = "banking.account.events.v1";
    public static final String TRANSFER_EVENTS    = "banking.transfer.events.v1";
    public static final String LEDGER_EVENTS      = "banking.ledger.events.v1";
    public static final String FRAUD_EVENTS       = "banking.fraud.events.v1";
    public static final String NOTIFICATION_CMDS  = "banking.notification.commands.v1";
    public static final String ENRICHED_TX        = "banking.transactions.enriched.v1";
    public static final String TRANSFER_DLQ       = "banking.transfer.dlq.v1";
    public static final String OUTBOX_DLQ         = "banking.outbox.dlq.v1";

    private KafkaTopics() {} // utility class — not instantiable
}

public final class ConsumerGroups {
    public static final String ACCOUNT_PROJECTOR   = "account-service.projector.v1";
    public static final String LEDGER_PROJECTOR    = "ledger-service.projector.v1";
    public static final String TRANSFER_PROJECTOR  = "transfer-service.projector.v1";
    public static final String RISK_ENRICHER       = "risk-service.enricher.v1";
    public static final String NOTIFICATION_SENDER = "notification-service.sender.v1";
    public static final String FRAUD_AUTO_FREEZE   = "account-service.fraud-freeze.v1";

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
| Execute debit | `TransferDebitedEvent` | None | N/A | — |
| Execute credit | `TransferCreditedEvent` | Credit fail | `ReverseDebitCommand` | `TransferReversalInitiatedEvent` → `TransferReversedEvent` |
| Submit to rail | `TransferSettlingEvent` | Rail rejection | `ReverseDebitCommand` | `TransferReversalInitiatedEvent` → `TransferReversedEvent` |
| Complete | `TransferCompletedEvent` | N/A — terminal | — | — |

### 9.2 Transfer Aggregate State Machine

```java
public class TransferAggregate extends AggregateRoot {

    // Logger: private static final SLF4J — required field declaration (§0.12)
    private static final Logger log = LoggerFactory.getLogger(TransferAggregate.class);

    // All state fields are private — no setters (§0.4)
    private String         transferId;
    private TransferStatus status;
    private String         sourceAccountId;
    private String         destinationAccountId;
    private String         destinationIban;
    private Money          amount;
    private PaymentRail    rail;
    private int            riskScore;
    private boolean        debitExecuted;  // guards compensation logic

    // ── Command methods ──────────────────────────────────────────────────────
    // All are imperative verbs; null-checked at entry; apply() is the only state mutator (§0.3, §0.5)

    public static TransferAggregate initiate(InitiateTransferCommand cmd) {
        Objects.requireNonNull(cmd,                "command must not be null");
        Objects.requireNonNull(cmd.transferId(),   "transferId must not be null");
        Objects.requireNonNull(cmd.amount(),       "amount must not be null");
        Objects.requireNonNull(cmd.metadata(),     "metadata must not be null");

        TransferAggregate t = new TransferAggregate();
        t.apply(new TransferInitiatedEvent(
            UUID.randomUUID().toString(),
            cmd.transferId().value(),
            cmd.sourceAccountId().value(),
            cmd.destinationAccountId() != null ? cmd.destinationAccountId().value() : null,
            cmd.destinationIban()       != null ? cmd.destinationIban().value()      : null,
            cmd.destinationBic(),
            cmd.amount(),
            null,
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
        Objects.requireNonNull(level,    "riskLevel must not be null");
        Objects.requireNonNull(decision, "riskDecision must not be null");
        Objects.requireNonNull(meta,     "metadata must not be null");
        assertStatus(TransferStatus.INITIATED);

        apply(new TransferRiskScoredEvent(UUID.randomUUID().toString(), transferId,
            score, level, decision,
            Collections.unmodifiableList(signals), // defensive copy (§0.4)
            durationMs,
            meta.correlationId(), meta.causationId(),
            version + 1, Instant.now(), TransferRiskScoredEvent.SCHEMA_VERSION));

        if (decision == RiskDecision.BLOCK) {
            apply(new TransferBlockedEvent(UUID.randomUUID().toString(), transferId,
                "Risk score %d exceeded threshold".formatted(score),
                meta.correlationId(), meta.causationId(),
                version + 1, Instant.now(), TransferBlockedEvent.SCHEMA_VERSION));
        }
    }

    public void recordDebitExecuted(String ledgerEntryId, EventMetadata meta) {
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId must not be null");
        Objects.requireNonNull(meta,          "metadata must not be null");
        assertStatus(TransferStatus.DEBIT_RESERVED);

        apply(new TransferDebitedEvent(UUID.randomUUID().toString(), transferId,
            ledgerEntryId, amount, meta.correlationId(), meta.causationId(),
            version + 1, Instant.now(), TransferDebitedEvent.SCHEMA_VERSION));
    }

    public void initiateReversal(ReversalReason reason, String originalEntryId, EventMetadata meta) {
        Objects.requireNonNull(reason,         "reason must not be null");
        Objects.requireNonNull(originalEntryId,"originalEntryId must not be null");
        Objects.requireNonNull(meta,           "metadata must not be null");

        if (!debitExecuted)
            throw new IllegalStateException(
                "Cannot reverse transfer %s — debit was not executed".formatted(transferId));

        assertStatusIn(TransferStatus.DEBITED, TransferStatus.CREDIT_FAILED,
                       TransferStatus.SETTLING);

        apply(new TransferReversalInitiatedEvent(UUID.randomUUID().toString(), transferId,
            originalEntryId, amount, reason,
            meta.correlationId(), meta.causationId(),
            version + 1, Instant.now(), TransferReversalInitiatedEvent.SCHEMA_VERSION));
    }

    // ── Event handlers ───────────────────────────────────────────────────────
    // These methods ONLY update state — NO side effects, NO external calls, NO business logic (§0.8)

    @Override
    protected void handle(DomainEvent event) {
        // Sealed interface + pattern matching: compiler warns on missing cases (§0.3)
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
            case TransferRiskScoredEvent e         -> this.riskScore = e.riskScore();
            case TransferBlockedEvent e            -> this.status = TransferStatus.BLOCKED;
            case TransferValidatedEvent e          -> this.status = TransferStatus.VALIDATED;
            case TransferDebitReservedEvent e      -> this.status = TransferStatus.DEBIT_RESERVED;
            case TransferDebitedEvent e            -> { this.status = TransferStatus.DEBITED; this.debitExecuted = true; }
            case TransferCreditedEvent e           -> this.status = TransferStatus.CREDITED;
            case TransferSettlingEvent e           -> this.status = TransferStatus.SETTLING;
            case TransferCompletedEvent e          -> this.status = TransferStatus.COMPLETED;
            case TransferReversalInitiatedEvent e  -> this.status = TransferStatus.REVERSING;
            case TransferReversedEvent e           -> this.status = TransferStatus.REVERSED;
            case TransferValidationFailedEvent e   -> this.status = TransferStatus.VALIDATION_FAILED;
            case TransferDebitFailedEvent e        -> this.status = TransferStatus.DEBIT_FAILED;
            case TransferCreditFailedEvent e       -> this.status = TransferStatus.CREDIT_FAILED;
            default -> log.warn("Unhandled event type in TransferAggregate: {}",
                                event.getClass().getSimpleName());
        }
    }

    private void assertStatus(TransferStatus expected) {
        if (this.status != expected)
            throw new InvalidTransferStateException(transferId, expected, this.status);
    }

    private void assertStatusIn(TransferStatus... allowed) {
        for (TransferStatus s : allowed) {
            if (this.status == s) return;
        }
        throw new InvalidTransferStateException(
            "Transfer %s status %s not in allowed set".formatted(transferId, this.status));
    }
}
```

### 9.3 Choreography Event Handlers (per service)

```java
// Kafka topic string from constant — never hardcoded (§0.3)
@KafkaListener(topics = KafkaTopics.LEDGER_EVENTS, groupId = ConsumerGroups.TRANSFER_PROJECTOR)
public void onLedgerEvent(ConsumerRecord<String, byte[]> record) {
    DomainEvent event = deserializer.deserialize(record.value());

    // Idempotency check first — see §10 for full two-phase protocol
    if (idempotencyStore.checkAndMark(event.eventId())) {
        log.debug("Duplicate event {} skipped", event.eventId());
        return;
    }

    switch (event) {
        case LedgerDebitedEvent e -> {
            TransferAggregate transfer = transferRepository.load(e.transferId());
            transfer.recordDebitExecuted(e.entryId(), metadataFrom(e));
            long versionBeforeEvents = transfer.getVersion() - transfer.getUncommittedEvents().size();
            transferRepository.save(transfer, versionBeforeEvents);
        }
        case LedgerCreditedEvent e -> {
            TransferAggregate transfer = transferRepository.load(e.transferId());
            transfer.recordCreditExecuted(e.entryId(), metadataFrom(e));
            long versionBeforeEvents = transfer.getVersion() - transfer.getUncommittedEvents().size();
            transferRepository.save(transfer, versionBeforeEvents);
        }
        default -> log.debug("Ignoring event type {} in transfer projector",
                             event.getClass().getSimpleName());
    }
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

Kafka consumers receive messages at-least-once. Every consumer implements a two-phase protocol:

**Phase 1 — Check:** Before processing, atomically check-and-set `eventId` in Redis. If already present, skip.
**Phase 2 — Mark:** The check-and-set is atomic (Redis `SET NX`) — Phase 1 and Phase 2 are a single operation.

Redis TTL must exceed the Kafka consumer retry window (3 retries × 1 second + `retention.ms` for retry topic). TTL = 25 hours is the canonical value.

### 10.2 Idempotency Store

```java
@Component
@RequiredArgsConstructor
public class KafkaIdempotencyStore {

    private static final Logger   log    = LoggerFactory.getLogger(KafkaIdempotencyStore.class);
    private static final Duration TTL    = Duration.ofHours(25);
    private static final String   PREFIX = "kafka-idem:";

    private final RedisTemplate<String, String> redis;

    /**
     * Atomically checks whether this eventId has been processed, and marks it if not.
     * Uses Redis SET NX (set-if-not-exists) — thread-safe across multiple pod instances.
     *
     * Returns true if this is a DUPLICATE (already processed — skip it).
     * Returns false if this is NEW (proceed with processing).
     */
    public boolean checkAndMark(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        String  key   = PREFIX + eventId;
        Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", TTL);
        boolean isDuplicate = Boolean.FALSE.equals(isNew);
        if (isDuplicate) {
            log.debug("Duplicate eventId {} detected and skipped", eventId);
        }
        return isDuplicate;
    }
}
```

```java
// Mandatory pattern in every Kafka consumer — no exceptions
@KafkaListener(topics = KafkaTopics.TRANSFER_EVENTS, groupId = ConsumerGroups.LEDGER_PROJECTOR)
public void project(ConsumerRecord<String, byte[]> record) {
    DomainEvent event = deserializer.deserialize(record.value());

    if (idempotencyStore.checkAndMark(event.eventId())) {
        return; // duplicate — idempotency store already logged it
    }

    projectEvent(event);
}
```

### 10.3 HTTP Idempotency (Transfers)

```java
@Around("@annotation(Idempotent)")
public Object enforceIdempotency(ProceedingJoinPoint pjp,
                                  HttpServletRequest request) throws Throwable {
    String key = request.getHeader("X-Idempotency-Key");
    if (key == null || key.isBlank())
        throw new MissingIdempotencyKeyException();

    // Validate format — must be UUID v4 (§0.14 — no raw strings for validation)
    try {
        UUID.fromString(key);
    } catch (IllegalArgumentException e) {
        throw new InvalidIdempotencyKeyException(key);
    }

    String cacheKey     = "http-idem:"          + key;
    String inFlightKey  = "http-idem-inflight:" + key;

    // Phase 1: check for cached response
    String cached = redis.opsForValue().get(cacheKey);
    if (cached != null) {
        IdempotencyCache entry = objectMapper.readValue(cached, IdempotencyCache.class);
        log.info("Idempotency replay for key {}", key);
        return entry.response();
    }

    // Phase 2: guard against concurrent in-flight duplicates
    Boolean acquired = redis.opsForValue().setIfAbsent(inFlightKey, "1", Duration.ofSeconds(30));
    if (Boolean.FALSE.equals(acquired))
        throw new DuplicateRequestInFlightException(key);

    try {
        Object result = pjp.proceed();
        redis.opsForValue().set(cacheKey,
            objectMapper.writeValueAsString(new IdempotencyCache(result)),
            Duration.ofHours(24));
        return result;
    } finally {
        redis.delete(inFlightKey); // always release — even on exception
    }
}
```
## 11. Concurrency Control & Conflict Resolution

### 11.1 Redis Balance Lock — Correct Approach

The v1 spec suggested a `transfer-lock:{accountId}` Redis key as an "optimistic debit lock." This is incorrect — Redis locks combined with EventStoreDB optimistic concurrency creates two competing concurrency mechanisms. The v2 uses **only** EventStoreDB expected revision for concurrency.

The Redis `balance:{accountId}` cache stores the last-known balance for fast reads. It does **not** participate in write-path concurrency control.

### 11.2 Balance Reservation Model

```java
// AccountAggregate — two-step debit: reserve → execute
// All command methods are null-checked; all state changes through apply() (§0.4, §0.5)
public void reserve(Money amount, String transferId, EventMetadata meta) {
    Objects.requireNonNull(amount,     "amount must not be null");
    Objects.requireNonNull(transferId, "transferId must not be null");
    Objects.requireNonNull(meta,       "metadata must not be null");

    Money available = currentBalance.subtract(reservedBalance);
    if (available.isNegative() || available.isLessThan(amount)) {
        throw new InsufficientFundsException(
            new AccountId(accountId), available, amount);
    }

    apply(new BalanceReservedEvent(UUID.randomUUID().toString(), accountId,
        transferId, amount, meta.correlationId(), meta.causationId(),
        version + 1, Instant.now(), BalanceReservedEvent.SCHEMA_VERSION));
}

public void executeDebit(String transferId, EventMetadata meta) {
    Objects.requireNonNull(transferId, "transferId must not be null");
    Objects.requireNonNull(meta,       "metadata must not be null");

    // Use Optional properly — never .get() without guard (§0.5)
    BalanceReservation reservation = reservations.stream()
        .filter(r -> r.transferId().equals(transferId))
        .findFirst()
        .orElseThrow(() -> new ReservationNotFoundException(accountId, transferId));

    apply(new AccountDebitedEvent(UUID.randomUUID().toString(), accountId,
        transferId, reservation.amount(), meta.correlationId(), meta.causationId(),
        version + 1, Instant.now(), AccountDebitedEvent.SCHEMA_VERSION));
}
```

---

## 12. Risk & Fraud Engine

### 12.1 Synchronous Scoring — Fail Closed

The risk service is called synchronously. Any failure blocks the transfer.

```java
// @Service in application layer (§0.7). Constructor injection (§0.7).
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskScoringClient {

    private final RestClient     restClient;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry  meterRegistry;

    /**
     * Score synchronously. Fail CLOSED on any exception or open circuit.
     * The caller (transfer saga) MUST block the transfer on RiskServiceUnavailableException.
     */
    public RiskScoreResult score(RiskScoreRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return circuitBreaker.executeSupplier(() ->
                restClient.post()
                    .uri("/api/v1/risk/score")
                    .body(request)
                    .retrieve()
                    .body(RiskScoreResult.class)
            );
        } catch (CallNotPermittedException ex) {
            // Circuit OPEN — fail closed (never allow transfer through unscored)
            log.error("Risk service circuit breaker OPEN — blocking transfer {}",
                      request.transferId());
            meterRegistry.counter("risk.scoring.circuit_open").increment();
            throw new RiskServiceUnavailableException(
                "Risk service unavailable — transfer blocked for safety", request.transferId());
        } catch (Exception ex) {
            // Log here — this is the infrastructure boundary (§0.6)
            log.error("Risk scoring failed for transfer {}: {}",
                      request.transferId(), ex.getMessage());
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
        automaticTransitionFromOpenToHalfOpenEnabled: true
  timelimiter:
    instances:
      risk-service:
        timeoutDuration: 48ms
        cancelRunningFuture: true
```

### 12.2 Risk Score Computation — Fixed

Integer overflow fix: accumulate in `long`, clamp in `long`, cast to `int` only after safe clamp (§0.10).

```java
public RiskScoreResult score(TransactionContext context) {
    Objects.requireNonNull(context, "context must not be null");

    List<RiskSignal> signals = signalRegistry.getApplicableSignals(context);

    // Accumulate in long — prevents integer overflow with many high-scoring signals (§0.10)
    long rawScore = signals.stream()
        .mapToLong(s -> (long) s.evaluate(context))
        .sum();

    // Clamp in long arithmetic before casting — safe for any input (§0.10)
    int score = (int) Math.min(rawScore * 10L, 1000L);

    // ML override — only when model confidence is very high
    if (mlModelEnabled) {
        MlPrediction prediction = mlClient.predict(context);
        if (prediction.confidence() >= 0.90) {
            // Blend: 30% rule-based, 70% ML
            score = (int) Math.round(score * 0.3 + prediction.normalizedScore() * 0.7);
        }
    }

    RiskLevel    level    = RiskLevel.fromScore(score);
    RiskDecision decision = decisionPolicy.decide(level, context.customerId(), context);

    List<String> firedSignalIds = signals.stream()
        .filter(s -> s.evaluate(context) > 0)
        .map(RiskSignal::getSignalId)
        .toList();

    return new RiskScoreResult(score, level, decision, firedSignalIds,
        System.currentTimeMillis() - context.startTime());
}
```

### 12.3 Velocity Signal — Redis Pipeline Batching

At 10,000 TPS, individual Redis commands collapse performance. Pipeline all operations:

```java
@Component
public class VelocityRiskSignal implements RiskSignal {

    private static final Logger log = LoggerFactory.getLogger(VelocityRiskSignal.class);

    private final RedisTemplate<String, String> redisTemplate;

    public VelocityRiskSignal(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
    }

    @Override
    public int evaluate(TransactionContext ctx) {
        Objects.requireNonNull(ctx, "context must not be null");

        String  key         = "velocity:" + ctx.customerId() + ":10m";
        Instant windowStart = ctx.timestamp().minus(10, MINUTES);

        // Pipeline all Redis commands — single round-trip at 10k TPS (§0.13)
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            conn.zAdd(key.getBytes(), ctx.timestamp().toEpochMilli(),
                      ctx.transferId().getBytes());
            conn.zRemRangeByScore(key.getBytes(), 0, windowStart.toEpochMilli());
            conn.zCard(key.getBytes());
            conn.expire(key.getBytes(), 660); // 11 minutes
            return null;
        });

        long count = (Long) results.get(2);

        // Use switch expression — compiler enforces coverage, no fall-through bugs (§0.14)
        if (count <= 2)       return 0;
        else if (count <= 4)  return 10;
        else if (count <= 7)  return 25;
        else                  return 60;
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
                .bearerTokenResolver(cookieBearerTokenResolver()))
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

```java
@Component
@RequiredArgsConstructor
public class AccountOwnershipGuard {

    private final SecurityAuditService securityAuditService;

    public void assertOwnership(JwtPrincipal principal, AccountId accountId) {
        Objects.requireNonNull(principal,  "principal must not be null");
        Objects.requireNonNull(accountId,  "accountId must not be null");

        if (principal.hasRole("OPERATOR") || principal.hasRole("ADMIN")) return;

        boolean owns = principal.getAccountIds().contains(accountId.value());
        if (!owns) {
            securityAuditService.logUnauthorizedAccountAccess(
                principal.getCustomerId(), accountId.value(),
                SecurityContextHolder.getContext().getAuthentication());
            throw new AccountAccessDeniedException(accountId.value());
        }
    }
}
```

### 13.4 Sensitive Field Masking

All domain events carrying IBAN or account numbers must be masked before logging or publication to non-EventStoreDB destinations (§0.12):

```java
public class SensitiveDataMaskingSerializer {

    // Private static final Pattern — justified constant (§0.14)
    private static final Pattern IBAN_PATTERN = Pattern.compile(
        "\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");

    public String maskSensitiveFields(String json) {
        Objects.requireNonNull(json, "json must not be null");
        return IBAN_PATTERN.matcher(json).replaceAll(m -> {
            String iban = m.group();
            return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
        });
    }
}

// Logback TurboFilter — applied globally before any appender writes
// NOTE: This filter mutates the params array in-place. This is intentional — Logback
// owns the params array within the filter chain. Do not copy the array unnecessarily
// at 10,000 TPS; the in-place mutation is a deliberate performance decision.
@Component
public class SensitiveDataMaskingFilter extends TurboFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMaskingFilter.class);
    private final SensitiveDataMaskingSerializer masker;

    public SensitiveDataMaskingFilter(SensitiveDataMaskingSerializer masker) {
        this.masker = Objects.requireNonNull(masker);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String s) {
                    params[i] = masker.maskSensitiveFields(s);
                }
            }
        }
        return FilterReply.NEUTRAL;
    }
}
```

### 13.5 GDPR — Pseudonymisation Strategy

PII is stored in a separate `customer-pii-store`. The event log stores only `customerId` UUIDs.

```java
@Transactional  // application layer — correct placement (§0.7)
public void processErasureRequest(String customerId, String requestedBy) {
    Objects.requireNonNull(customerId,   "customerId must not be null");
    Objects.requireNonNull(requestedBy,  "requestedBy must not be null");

    // MLD5: cannot erase customer with active accounts
    List<AccountReadModel> accounts = accountQueryService.getAccountsByCustomer(customerId);
    boolean hasActiveAccounts = accounts.stream()
        .anyMatch(a -> a.status() == AccountStatus.ACTIVE);
    if (hasActiveAccounts)
        throw new ErasureBlockedByActiveAccountsException(customerId);

    customerPiiRepository.delete(customerId);
    accountReadModelRepository.pseudonymise(customerId);
    erasureLogRepository.record(ErasureRecord.of(customerId, requestedBy, Instant.now()));

    // §0.12 — no PII in log message (customerId is a random UUID, not PII)
    log.info("GDPR erasure completed for customerId {} by {}", customerId, requestedBy);
}
```

### 13.6 Vault Integration

Secrets are injected via the Vault Agent Injector sidecar — never in environment variables or Kubernetes Secrets in plaintext.

```yaml
vault.hashicorp.com/agent-inject: "true"
vault.hashicorp.com/agent-inject-secret-db: "banking/data/transfer-service/db"
vault.hashicorp.com/agent-inject-template-db: |
  {{- with secret "banking/data/transfer-service/db" -}}
  spring.datasource.password={{ .Data.data.password }}
  {{- end }}
```

---

## 14. API Design Contracts

### 14.1 API Versioning

URL path versioning: `/api/v1/`. Breaking changes require `/api/v2/` with 12-month parallel support. Deprecation communicated via `Deprecation` and `Sunset` response headers.

### 14.2 Transfer API

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
    "cancel": { "href": "/api/v1/transfers/TRF-20240315-000123/cancel", "methods": ["POST"] }
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
  "type":      "https://api.bank.com/errors/transfer-blocked",
  "title":     "Transfer Blocked",
  "status":    403,
  "detail":    "Transaction blocked by risk engine (score: 820)",
  "riskScore":  820,
  "caseId":    "CASE-20240315-000042",
  "traceId":   "4bf92f3577b34da6"
}

Response 503 — Risk Service Unavailable:
{
  "type":       "https://api.bank.com/errors/risk-service-unavailable",
  "title":      "Transfer Temporarily Unavailable",
  "status":     503,
  "detail":     "Transfer blocked pending risk assessment. Please retry in 10 seconds.",
  "retryAfter": 10,
  "traceId":    "4bf92f3577b34da6"
}
```

### 14.3 Account Balance API

```
GET /api/v1/accounts/{accountId}/balance

Response 200:
{
  "accountId":  "ACC-000001",
  "iban":       "DE89****3000",      <- always masked (§0.12)
  "currency":   "EUR",
  "balance": {
    "current":   { "value": "5420.50", "currency": "EUR" },
    "available": { "value": "5170.50", "currency": "EUR" },
    "reserved":  { "value": "250.00",  "currency": "EUR" }
  },
  "status":    "ACTIVE",
  "asOf":      "2024-03-15T14:35:00Z",
  "cached":    true,
  "cacheAge":  1200
}
```

### 14.4 WebSocket Real-Time Notifications

```
WS /api/v1/ws/notifications
Authorization: Bearer {jwt}

Server → Client (STOMP frames):
SUBSCRIBE /topic/accounts/{accountId}

{
  "type": "TRANSFER_COMPLETED",
  "data": {
    "transferId":    "TRF-20240315-000123",
    "direction":     "DEBIT",
    "amount":        "-250.00",
    "currency":      "EUR",
    "counterparty":  "DE89****4001",   <- always masked
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
    balance_current   NUMERIC(19,4) NOT NULL DEFAULT 0, -- NUMERIC, never FLOAT (§0.10)
    balance_available NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_reserved  NUMERIC(19,4) NOT NULL DEFAULT 0,
    opened_at         TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    projection_version BIGINT       NOT NULL,
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
    amount              NUMERIC(19,4) NOT NULL, -- NUMERIC, never FLOAT (§0.10)
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

CREATE INDEX idx_transfer_source ON transfer_read_model (source_account_id, initiated_at DESC);
CREATE INDEX idx_transfer_status ON transfer_read_model (status)
    WHERE status IN ('INITIATED','DEBITED','SETTLING','REVERSING');

-- ─── Transaction (Ledger Entry) Read Model — partitioned by month ──────────
CREATE TABLE transaction_read_model (
    entry_id         VARCHAR(50)   PRIMARY KEY,
    account_id       VARCHAR(50)   NOT NULL,
    transfer_id      VARCHAR(50),
    debit_credit     CHAR(1)       NOT NULL CHECK (debit_credit IN ('D','C')),
    amount           NUMERIC(19,4) NOT NULL, -- NUMERIC, never FLOAT (§0.10)
    currency         CHAR(3)       NOT NULL,
    balance_after    NUMERIC(19,4) NOT NULL,
    description      VARCHAR(500),
    counterpart_name VARCHAR(200),
    counterpart_iban VARCHAR(34),            -- masked
    category         VARCHAR(50),
    value_date       DATE          NOT NULL,
    booked_at        TIMESTAMPTZ   NOT NULL
) PARTITION BY RANGE (booked_at);

CREATE INDEX idx_txn_acct_date ON transaction_read_model (account_id, booked_at DESC);
CREATE INDEX idx_txn_transfer  ON transaction_read_model (transfer_id) WHERE transfer_id IS NOT NULL;
```

### 15.3 Redis Key Patterns (Canonical)

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `balance:{accountId}` | Hash | 5s | Hot balance cache |
| `http-idem:{key}` | String | 24h | HTTP idempotency |
| `http-idem-inflight:{key}` | String | 30s | Concurrent duplicate guard |
| `kafka-idem:{eventId}` | String | 25h | Kafka consumer dedup |
| `velocity:{customerId}:{window}` | ZSet | window+60s | Risk velocity signal |
| `risk-profile:{customerId}` | Hash | 1h | Risk scoring cache |
| `session:{sessionId}` | Hash | 30m | Cached JWT claims |
| `transfer-limit:{customerId}:{date}` | Hash | 25h | Daily limit consumption |
| `account-lock:{accountId}` | String | 30s | Distributed lock (limit check only) |

---

## 16. Infrastructure & Deployment

### 16.1 Docker Compose (local dev)

```yaml
version: '3.9'

services:
  eventstore:
    image: eventstore/eventstore:24.6-bookworm-slim
    environment:
      - EVENTSTORE_CLUSTER_SIZE=1
      - EVENTSTORE_RUN_PROJECTIONS=All
      - EVENTSTORE_START_STANDARD_PROJECTIONS=true
      - EVENTSTORE_INSECURE=true
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
    command: postgres -c wal_level=logical

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
      KAFKA_LOG_RETENTION_MS: 2592000000
      KAFKA_MIN_INSYNC_REPLICAS: 1
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
}

create_topic "banking.account.events.v1"        12  2592000000
create_topic "banking.transfer.events.v1"       24  2592000000
create_topic "banking.ledger.events.v1"         12  2592000000
create_topic "banking.fraud.events.v1"          6   2592000000
create_topic "banking.notification.commands.v1" 6   86400000
create_topic "banking.transactions.enriched.v1" 12  2592000000
create_topic "banking.transfer.dlq.v1"          6   -1
create_topic "banking.outbox.dlq.v1"            3   -1
```

### 16.3 Spring Application Properties (production)

```yaml
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
          limits:   { memory: "1Gi",  cpu: "2000m" }
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
        livenessProbe:
          httpGet: { path: /actuator/health/liveness, port: 8082 }
          initialDelaySeconds: 60
          periodSeconds: 30
        lifecycle:
          preStop:
            exec:
              command: ["sleep", "15"]
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: transfer-service-pdb
  namespace: banking
spec:
  minAvailable: 2
  selector:
    matchLabels: { app: transfer-service }
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

1. `infra/docker-compose.yml` — all services as defined in §16.1.
2. `infra/kafka/create-topics.sh` — idempotent topic creation.
3. Flyway migration baseline for all PostgreSQL schemas (`V1__baseline.sql`).
4. `shared-kernel` module: `AggregateRoot`, `DomainEvent`, `Money`, `IBAN`, `AccountId`, `EventMetadata`, `KafkaTopics`, `ConsumerGroups`, `DomainException` hierarchy.
5. Avro schemas in `src/main/avro/` per domain event — registered in Schema Registry on startup.
6. `.env.example` with all required variables.
7. ArchUnit test class (§0.2) — must pass before any service code is written.

### 17.2 Implementation Order

```
1. shared-kernel module (AggregateRoot, value objects, DomainEvent, DomainException)
2. account-service/domain (AccountAggregate + events as records + exceptions — zero Spring)
3. ArchUnit test passes: no Spring in domain/, no infra in application/
4. account-service/infrastructure/persistence (EventStoreDBAccountRepository)
5. account-service/infrastructure/persistence (outbox table + OutboxWriter + OutboxRelay)
6. account-service/application/command (OpenAccountCommandHandler — Outbox pattern)
7. account-service/infrastructure/messaging (AccountEventProjector — Kafka consumer)
8. account-service/api (REST controller + DTOs + BankingExceptionHandler)
9. Account domain unit tests (100% state machine — zero mocks)
10. Account integration test (TestContainers — EventStoreDB + Kafka + PostgreSQL)
```

### 17.3 Phase 1 Acceptance Criteria

- [ ] `POST /api/v1/accounts` creates an account. EventStoreDB stream `account-{id}` contains `AccountOpenedEvent`.
- [ ] Outbox record in PostgreSQL is published to Kafka within 200ms.
- [ ] Kafka consumer projects `AccountOpenedEvent` to `account_read_model`. `GET /api/v1/accounts/{id}` returns 200.
- [ ] Concurrent account opens with same idempotency key return identical responses.
- [ ] `PUT /api/v1/accounts/{id}/freeze` changes status to FROZEN. Reload from EventStoreDB produces FROZEN aggregate.
- [ ] Outbox relay survives Kafka restart: events buffered in PostgreSQL, published after Kafka recovers.
- [ ] `docker compose up` starts all infra healthy.
- [ ] `./mvnw test` passes all unit tests with > 80% line coverage on domain classes.
- [ ] ArchUnit test passes: zero Spring imports in `domain/`.

---

## 18. Phase 2 — Transfer Core & Ledger

**Goal:** Internal transfers work end-to-end. Full saga with balance reservation, double-entry ledger, risk scoring (stubbed), and compensation on failure. WebSocket notifications delivered.

### 18.1 Implementation Order

```
1. transfer-service/domain (TransferAggregate + full state machine + all events as records)
2. transfer-service/domain (TransferLimitAggregate)
3. transfer-service/application (InitiateTransferCommandHandler — Outbox, reservation step)
4. risk-service stub (returns APPROVE for all requests, 5ms latency)
5. transfer-service/application (saga choreography consumers for ledger events)
6. ledger-service/domain (LedgerEntryAggregate + double-entry validation)
7. ledger-service/application (LedgerCommandHandler — creates debit + credit entries)
8. ledger-service/infrastructure (AccountBalanceProjector → Redis cache + PostgreSQL)
9. Compensation path: credit failure → reversal saga
10. WebSocket notification service
11. Integration tests: full internal transfer end-to-end
```

### 18.2 Double-Entry Invariant

```java
// Domain service — plain Java, no Spring (§0.8)
public class DoubleEntryService {

    /**
     * A journal entry: exactly N debits and N credits summing to zero.
     * This invariant must hold before any ledger events are persisted.
     * Uses BigDecimal — never double or float (§0.10).
     */
    public void validateJournalEntry(List<LedgerEntryCommand> entries) {
        Objects.requireNonNull(entries, "entries must not be null");

        BigDecimal debitSum  = entries.stream()
            .filter(e -> e.debitCredit() == DebitCredit.DEBIT)
            .map(e -> e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditSum = entries.stream()
            .filter(e -> e.debitCredit() == DebitCredit.CREDIT)
            .map(e -> e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // compareTo for BigDecimal equality — never .equals() which considers scale (§0.14)
        if (debitSum.compareTo(creditSum) != 0) {
            throw new UnbalancedJournalEntryException(debitSum, creditSum);
        }
    }
}
```

### 18.3 Phase 2 Acceptance Criteria

- [ ] `POST /api/v1/transfers` (internal, EUR 250): responds 202, transfer reaches `COMPLETED` within p99 350ms.
- [ ] Source balance decreases by EUR 250; destination increases by EUR 250.
- [ ] Ledger has exactly 2 entries (1 debit, 1 credit). Double-entry sum = 0.
- [ ] Concurrent transfers on same account with insufficient balance — exactly one succeeds; second returns 422 after reload-and-validate.
- [ ] Compensation: artificially fail credit step → `TransferReversedEvent` appears → source balance restored.
- [ ] WebSocket client receives `TRANSFER_COMPLETED` within 1 second of saga completion.
- [ ] Idempotency: same `X-Idempotency-Key` sent twice → identical 202 response, single transfer in event store.
- [ ] `GET /api/v1/accounts/{id}/balance` returns Redis-cached balance within 5ms (after first populate).

---

## 19. Phase 3 — Risk Engine & Fraud Cases

**Goal:** Real risk scoring within 50ms synchronous SLA. Circuit breaker configured fail-closed. Fraud case lifecycle.

### 19.1 Phase 3 Acceptance Criteria

- [ ] Risk scoring p99 < 45ms under 500 concurrent requests (Gatling).
- [ ] Transfer with synthesised high-risk signals returns 403 `TRANSFER_BLOCKED`.
- [ ] Risk service killed → circuit breaker opens → transfer returns 503 `RISK_SERVICE_UNAVAILABLE` (not allowed through — fail closed).
- [ ] Circuit breaker half-open after 10 seconds → first probe allowed → auto-closes if successful.
- [ ] `FraudCaseAggregate` created for BLOCK decisions. `GET /api/v1/fraud/cases/{id}` returns case detail.
- [ ] Fraud analyst can `POST /api/v1/fraud/cases/{id}/review` and `POST /api/v1/fraud/cases/{id}/resolve`.
- [ ] Velocity signal uses Redis pipelining — no individual ZADD per request.
- [ ] Risk scoring latency p99 alert fires when scoring exceeds 45ms for > 30s.

---

## 20. Phase 4 — External Rails & Enrichment

**Goal:** SEPA credit transfers submitted to external rail. FX conversion. Kafka Streams enrichment pipeline.

### 20.1 SEPA Adapter

```java
// Domain port interface — lives in domain/ (§0.8), implementation in infrastructure/
public interface PaymentRailAdapter {
    /**
     * Submit a transfer to the payment rail.
     * Returns immediately with a submission reference.
     * Settlement confirmation arrives asynchronously via webhook or polling.
     */
    PaymentSubmissionResult submit(TransferAggregate transfer);

    void handleSettlementNotification(SettlementNotification notification);
}
```

### 20.2 Phase 4 Acceptance Criteria

- [ ] SEPA transfer submitted to mock rail adapter. Transfer stays in `SETTLING` until mock ACK.
- [ ] Mock ACK (webhook) → transfer moves to `COMPLETED`.
- [ ] Mock REJECT → compensation reversal triggered.
- [ ] FX transfer: EUR → USD. `fxAmount` and `fxCurrency` populated in transfer read model.
- [ ] Kafka Streams enrichment: `TransferCompletedEvent` enriched with merchant category. `enriched-transactions` topic populated.
- [ ] Daily transfer limit enforced per customer tier and KYC level.

---

## 21. Phase 5 — Hardening, Observability & Load Testing

**Goal:** All SLOs verified under load. Security hardened. Runbooks written. Chaos tests pass.

### 21.1 Gatling Load Test Targets

```scala
val transferScenario = scenario("Internal Transfer")
  .exec(http("Initiate Transfer")
    .post("/api/v1/transfers")
    .header("X-Idempotency-Key", "#{idempotencyKey}")
    .body(StringBody("#{transferBody}"))
    .check(status.in(202, 422))
    .check(responseTimeInMillis.lte(350)))

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
      exceptionsActive: false
```

Test scenarios (each must pass with circuit breakers absorbing failures):
1. 500ms latency on risk-service → circuit breaker opens → transfers blocked with 503 (correct).
2. EventStoreDB node 1 killed → cluster re-elects → writes resume within 15s.
3. Kafka broker 1 killed → producers use other brokers → Outbox relay catches up.
4. PostgreSQL primary killed → replica promoted → read model continues.
5. Redis eviction → balance recomputed from read model → no incorrect balances served.

### 21.3 Phase 5 Acceptance Criteria

- [ ] All Gatling SLOs from §1.2 pass at 10,000 TPS sustained.
- [ ] All 5 chaos scenarios pass without data loss or incorrect balances.
- [ ] OWASP Dependency Check reports zero CRITICAL CVEs.
- [ ] PCI-DSS scan: no PAN or raw IBAN in logs, traces, or Kafka messages.
- [ ] All 7 runbooks (§24) present and contain step-by-step commands.
- [ ] `./mvnw verify` passes with ≥ 80% line coverage on all modules.
- [ ] Pact contract tests pass for all service pairs.

---

## 22. Testing Strategy

### 22.1 Test Pyramid

> The mandatory testing rules — GWT structure, no mocks in domain tests, TestContainers only (no H2), and coverage thresholds — are defined in **§0.11**. This section records the required test layers.

| Layer | Coverage Target | Tooling |
|---|---|---|
| Domain unit tests | 100% aggregate transitions; 95% domain service branches | JUnit 5 + AssertJ (zero Mockito) |
| Application unit tests | All command/query handlers (happy + error) | JUnit 5 + Mockito (domain ports only) |
| Slice tests | All REST controllers (all endpoints + auth scenarios) | `@WebMvcTest` |
| Integration tests | Full Spring context + real containers per service | TestContainers (real DBs — no H2) |
| Contract tests | All consumer/provider pairs | Pact |
| End-to-end tests | Full transfer flow across all services | TestContainers + rest-assured |
| Load tests | All SLOs from §1.2 | Gatling |
| Chaos tests | All 5 scenarios from §21.2 | Chaos Monkey + custom |

### 22.2 Domain Unit Test Examples

```java
// Zero mocks — pure domain objects (§0.11)
class TransferAggregateTest {

    @Test
    void should_block_transfer_when_risk_decision_is_block() {
        // Given
        TransferAggregate transfer = initiatedTransfer();
        EventMetadata meta = testMetadata();

        // When
        transfer.applyRiskScore(850, RiskLevel.CRITICAL, RiskDecision.BLOCK,
                                List.of("VELOCITY_HIGH", "AMOUNT_UNUSUAL"), 30L, meta);

        // Then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);
        assertThat(transfer.getUncommittedEvents())
            .extracting(e -> e.getClass().getSimpleName())
            .containsExactly("TransferRiskScoredEvent", "TransferBlockedEvent");
    }

    @Test
    void should_reject_reversal_when_debit_was_not_executed() {
        // Given
        TransferAggregate transfer = initiatedTransfer(); // debitExecuted = false

        // When / Then
        assertThatThrownBy(() ->
            transfer.initiateReversal(ReversalReason.CREDIT_FAILED, "entry-1", testMetadata()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("debit was not executed");
    }

    @Test
    void should_clamp_risk_score_without_integer_overflow() {
        // Given — 15 signals each scoring 100 raw = 1500 → scaled to 1000 (clamped)
        TransactionContext ctx = highVolumeContext();

        // When
        RiskScoreResult result = riskScoringService.score(ctx);

        // Then
        assertThat(result.score()).isBetween(0, 1000);
        assertThat(result.score()).isEqualTo(1000); // clamped at maximum
    }

    @Test
    void should_compute_money_arithmetic_without_floating_point_error() {
        // Given — regression test: 0.1 + 0.2 in double = 0.30000000000000004
        Money a = new Money(new BigDecimal("0.10"), EUR);
        Money b = new Money(new BigDecimal("0.20"), EUR);

        // When
        Money sum = a.add(b);

        // Then — BigDecimal arithmetic is exact (§0.10)
        assertThat(sum.amount()).isEqualByComparingTo(new BigDecimal("0.30"));
        // Never use .equals() for BigDecimal scale-sensitive comparison (§0.14)
    }

    @Test
    void should_return_unmodifiable_uncommitted_events_list() {
        // Given
        TransferAggregate transfer = initiatedTransfer();

        // When
        List<DomainEvent> events = transfer.getUncommittedEvents();

        // Then — caller cannot mutate aggregate state (§0.4)
        assertThatThrownBy(() -> events.add(new TransferBlockedEvent()))
            .isInstanceOf(UnsupportedOperationException.class);
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
        // Given
        String sourceId = createAccountWithBalance(new Money(new BigDecimal("1000.00"), EUR));
        String destId   = createAccountWithBalance(Money.zero(EUR));

        // When
        String transferId = transferService.initiate(
            InitiateTransferCommand.builder()
                .sourceAccountId(new AccountId(sourceId))
                .destinationAccountId(new AccountId(destId))
                .amount(new Money(new BigDecimal("250.00"), EUR))
                .rail(PaymentRail.INTERNAL)
                .initiatedBy("CUST-001")
                .metadata(testMetadata())
                .build());

        // Then — wait for eventual consistency (Kafka consumer projection)
        await().atMost(Duration.ofSeconds(10))
               .pollInterval(Duration.ofMillis(100))
               .until(() -> transferQueryService.getStatus(transferId) == TransferStatus.COMPLETED);

        // Assert balances — use compareTo, not equals (§0.14 / §0.10)
        Money sourceBal = accountQueryService.getBalance(sourceId).current();
        Money destBal   = accountQueryService.getBalance(destId).current();
        assertThat(sourceBal.amount()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(destBal.amount()).isEqualByComparingTo(new BigDecimal("250.00"));

        // Assert double-entry invariant
        List<LedgerEntry> entries = ledgerQueryService.getEntriesForTransfer(transferId);
        assertThat(entries).hasSize(2);
        BigDecimal netLedger = entries.stream()
            .map(e -> e.debitCredit() == DEBIT
                ? e.amount().amount().negate()
                : e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netLedger).isEqualByComparingTo(BigDecimal.ZERO);

        // Assert event sequence in EventStoreDB
        List<DomainEvent> events = transferRepository.loadFrom(transferId, 0L);
        assertThat(events)
            .extracting(e -> e.getClass().getSimpleName())
            .containsExactly(
                "TransferInitiatedEvent",
                "TransferRiskScoredEvent",
                "TransferValidatedEvent",
                "TransferDebitReservedEvent",
                "TransferDebitedEvent",
                "TransferCreditedEvent",
                "TransferCompletedEvent"
            );
    }

    @Test
    void compensation_restores_balance_on_credit_failure() throws Exception {
        // Given
        String sourceId = createAccountWithBalance(new Money(new BigDecimal("500.00"), EUR));
        injectCreditFailureFor(sourceId);

        // When
        String transferId = transferService.initiate(buildTransferCommand(sourceId, "ACC-DEST"));

        // Then — saga compensates
        await().atMost(Duration.ofSeconds(15))
               .until(() -> transferQueryService.getStatus(transferId) == TransferStatus.REVERSED);

        Money sourceBal = accountQueryService.getBalance(sourceId).current();
        assertThat(sourceBal.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }
}
```

### 22.4 Pact Contract Tests

```java
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
}
```

---

## 23. Observability & Alerting

### 23.1 Custom Metrics

> See §0.12 for the structured logging rules that complement these metrics.

```java
// Constructor injection of MeterRegistry (§0.7)
public class TransferMetrics {

    private final Counter    transfersInitiated;
    private final Counter    transfersCompleted;
    private final Counter    transfersFailed;
    private final Counter    transfersBlocked;
    private final Timer      transferDuration;
    private final Timer      riskScoringDuration;

    public TransferMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        this.transfersInitiated  = registry.counter("banking.transfers.initiated");
        this.transfersCompleted  = registry.counter("banking.transfers.completed");
        this.transfersFailed     = registry.counter("banking.transfers.failed");
        this.transfersBlocked    = registry.counter("banking.transfers.blocked",
                                       Tags.of("reason", "risk_score"));
        this.transferDuration    = registry.timer("banking.transfers.duration");
        this.riskScoringDuration = registry.timer("banking.risk.scoring.duration");

        Gauge.builder("banking.transfers.saga.inflight",
                sagaRepository, SagaRepository::countActive)
             .register(registry);

        Gauge.builder("banking.outbox.pending",
                outboxRepository, OutboxRepository::countPending)
             .tag("service", "transfer-service")
             .register(registry);

        DistributionSummary.builder("banking.risk.score")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .serviceLevelObjectives(200, 400, 600, 800)
            .register(registry);
    }
}
```

### 23.2 Alerting Rules

```yaml
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

  - alert: RiskCircuitBreakerOpen
    expr: resilience4j_circuitbreaker_state{name="risk-service",state="open"} == 1
    for: 30s
    labels: { severity: critical }
    annotations:
      summary: "Risk circuit breaker OPEN — all transfers are being blocked"
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

  - alert: KafkaConsumerLagHigh
    expr: kafka_consumer_group_lag{topic=~"banking\\..*"} > 10000
    for: 5m
    labels: { severity: warning }
```

### 23.3 Distributed Tracing Attributes

Every span in the transfer critical path must carry these attributes (§0.12):

```java
Span.current()
    .setAttribute("banking.transfer.id",         transferId)
    .setAttribute("banking.transfer.rail",        rail.name())
    .setAttribute("banking.transfer.amount.eur",  amountInEur)
    .setAttribute("banking.account.source",       sourceAccountId)
    .setAttribute("banking.customer.tier",        customerTier)
    .setAttribute("banking.risk.score",           riskScore)
    .setAttribute("banking.risk.decision",        riskDecision.name())
    .setAttribute("banking.saga.step",            sagaStep.name());
// Note: never set raw IBAN or PAN as span attributes (§0.12)
```

---

## 24. Operational Runbooks

Every runbook contains: 1) Alert context, 2) Immediate mitigation (< 5 minutes), 3) Investigation steps with exact commands, 4) Root cause analysis, 5) Resolution, 6) Post-incident actions.

### 24.1 Transfer Success Rate Low

```bash
# Check which failure reason dominates
kubectl exec -n banking deploy/transfer-service -- \
  curl -s http://localhost:8082/actuator/metrics/banking.transfers.failed | jq '.measurements'

# Check circuit breaker state
curl -s http://transfer-service:8082/actuator/circuitbreakers | \
  jq '.circuitBreakers["risk-service"].state'

# Check recent errors
kubectl logs -n banking deploy/transfer-service --since=10m | grep ERROR | tail -50
```

### 24.2 Risk Circuit Breaker Open

**Impact:** All new transfers blocked with 503. Existing transfers already past risk scoring are unaffected.

```bash
# Check risk service pod status
kubectl get pods -n banking -l app=risk-service

# Manually transition to half-open (allow one probe)
curl -X POST http://transfer-service:8082/actuator/circuitbreakers/risk-service/half-open

# If risk pods are crashed
kubectl rollout restart deploy/risk-service -n banking
```

### 24.3 Outbox Relay Dead Letter

```bash
# Inspect DLQ messages
kafka-console-consumer.sh --bootstrap-server $KAFKA_BROKER \
  --topic banking.transfer.dlq.v1 --from-beginning --max-messages 10

# Inspect dead outbox records
psql $DB_URL -c "SELECT event_type, last_error, created_at FROM outbox_messages
                 WHERE status = 'DEAD' ORDER BY created_at DESC LIMIT 20;"

# Replay dead outbox messages after fixing root cause
psql $DB_URL -c "UPDATE outbox_messages SET status = 'PENDING', retry_count = 0
                 WHERE status = 'DEAD' AND created_at > NOW() - INTERVAL '1 hour';"
```

### 24.4 EventStoreDB Node Failure

```bash
# Check cluster gossip
curl -s http://eventstore-node1:2113/gossip | \
  jq '.members[] | {instanceId, state, isAlive}'

# Verify stream integrity after recovery
kafka-console-consumer.sh --topic banking.transfer.events.v1 \
  --from-beginning | jq 'select(.transferId == "TRF-SUSPECT")'
```

### 24.5 Balance Inconsistency — P0 Incident

**Never auto-remediate. Human review required.**

```bash
# 1. Immediately suspend the affected account
curl -X POST http://account-service:8081/api/v1/admin/accounts/{accountId}/suspend \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"reason": "BALANCE_INCONSISTENCY_INVESTIGATION"}'

# 2. Compare Redis vs PostgreSQL vs EventStoreDB authoritative replay
redis-cli GET balance:{accountId}
psql $DB_URL -c "SELECT balance_current, balance_available
                 FROM account_read_model WHERE account_id = '{accountId}';"

# 3. Replay balance from EventStoreDB (authoritative source)
curl -X POST http://account-service:8081/api/v1/admin/accounts/{accountId}/replay-balance \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 4. If replayed balance differs from stored — escalate to engineering lead immediately
```

---

## 25. Full System Acceptance Criteria

When all 5 phases are complete, the following end-to-end scenario executes as an automated CI pipeline job. Every step is a CI assertion. The release gate requires all 25 steps green.

```
1.  docker compose up -d                              → all services healthy
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
15. POST /api/v1/transfers (same idempotency key)     → 202, identical transferId — no duplicate
16. Synthesise high-risk transfer (velocity = 20)     → 403 TRANSFER_BLOCKED
17. Kill risk-service pod                             → POST /api/v1/transfers returns 503 (fail-closed)
18. Restart risk-service                              → transfers accepted within 15 seconds
19. Kill Kafka broker                                 → POST /api/v1/transfers succeeds, outbox_messages.status='PENDING'
20. Restart Kafka                                     → outbox.status='PUBLISHED' within 30 seconds
21. Trigger GDPR erasure for customer                 → PII deleted, event log intact
22. GET  /api/v1/accounts/{id} after erasure          → 200, displayName = "[REDACTED]", events preserved
23. Run Gatling load test                             → p99 internal transfer < 350ms at 10,000 TPS
24. Run Pact contract verification                    → all provider states pass
25. docker compose down                               → clean shutdown, no data loss
```

---

*Banking Platform Enterprise Specification v2.0 (with integrated Java Clean Code Standards)*
*Architecture: DDD · CQRS · Event Sourcing · Choreography Saga · Transactional Outbox*
*Stack: Java 21 · Spring Boot 3.3.x · EventStoreDB 24 · Apache Kafka 3.7 · PostgreSQL 16 · Redis 7 · Keycloak 25*
*Code standards enforced by: ArchUnit · Checkstyle · SpotBugs · NullAway · JaCoCo*
*Supersedes v1.0 — all canonical names, patterns, and acceptance criteria in this document take precedence.*