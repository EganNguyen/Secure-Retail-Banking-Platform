# Backend Test Strategy

This document outlines the testing strategy for the Secure Retail Banking Platform backend services, which use Spring Boot, PostgreSQL, EventStoreDB, Redis, and Kafka.

## Unit Tests

**Purpose:** Test individual classes (Domain Models, Services, Utility classes) in isolation without starting the Spring Application Context or external infrastructure.

- **Mocking Dependencies:** Use Mockito (`@Mock`, `@InjectMocks`) to mock repositories (`EventSourcedRepository`, `AccountReadModelRepository`), outward HTTP clients, Kafka producers, and file systems.
- **Focus on Behavior:** Assert domain logic, state transformations, and edge cases (e.g., throwing `AccountNotFoundException` or `InsufficientFundsException`).
- **No Database Interaction:** Do not verify SQL queries, JDBC details, or EventStore append operations here.
- **Tools:** JUnit 5, Mockito, AssertJ.

## Integration Tests

**Purpose:** Verify that the system's components (Controllers, Use Cases, Repositories) work together correctly and integrate successfully with the underlying infrastructure.

- **Infrastructure:** Use a dedicated test suite of local containers (as defined in `docker-compose.yml`), including PostgreSQL (`banking_read`), EventStoreDB, Kafka, Redis, and Keycloak. Connect using test properties pointing to these local instances (e.g., `esdb://localhost:2113?tls=false`).
- **Context Loading:** Use `@SpringBootTest` to load the full application context and `@AutoConfigureMockMvc` to test REST endpoints from the outside in.
- **Real Data Flow:** Write real data through the API (e.g., `POST /api/v1/accounts`), then read and assert it (e.g., `GET /api/v1/accounts/{id}`) to ensure the EventStore append and subsequent Projection to PostgreSQL read model work correctly.
- **State Management:** Reset state between tests when necessary (e.g., using transactions, truncating tables, or writing to fresh container setups).
- **Eventual Consistency:** When relying on asynchronous projections (e.g., from EventStore to PostgreSQL), handle delays properly (e.g., using `Thread.sleep` or Awaitility await) before making assertions on the read model.
- **Data Seeding:** Seed only the minimum data needed via the API or direct DB insertions.
