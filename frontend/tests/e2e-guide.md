# End-to-End (E2E) Testing Guide

This guide explains how to set up the environment and run the full end-to-end test suite for the Secure Retail Banking Platform.

## 🏗️ Prerequisites

Ensure you have the following installed and running:
*   **Docker & Docker Compose**: For infrastructure (Postgres, Kafka, Keycloak, EventStoreDB).
*   **Node.js (v20+)**: To run the Next.js frontend and Playwright.
*   **Java 21**: To run the Spring Boot backend service.

---

## 🚀 Setup Steps

### 1. Start Infrastructure
Navigate to the `backend/banking-platform` directory and spin up the required services:
```bash
docker-compose up -d
```

### 2. Provision Keycloak
Keycloak needs to be configured with the `retail` realm and `retail-app` client. Run the provisioning script/commands (typically found in your CI or setup scripts):
*   Ensure the `retail` realm exists.
*   Create a user (e.g., `egan` / `password123`).

### 3. Start the Backend (Account Service)
In the `backend/banking-platform` directory:
```bash
mvn spring-boot:run
```
Wait for the logs to show `"Started AccountServiceApplication in ... seconds"`.

### 4. Start the Frontend
In the `frontend` directory:
```bash
npm install
npm run dev
```
The frontend should be accessible at `http://localhost:3000`.

---

## 🧪 Running Tests

We use **Playwright** for E2E testing. 

### To run the tests:
```bash
cd frontend
npx playwright test tests/flow.spec.ts
npx playwright test tests/flow.spec.ts --headed
npx playwright test tests/flow.spec.ts --debug
```

### To view the report:
```bash
npx playwright show-report
```

---

## 🛠️ Troubleshooting & Best Practices

### 1. Initializing Security Context
The app uses an async Keycloak initialization. If the test reloads the page too quickly, it might stall on the loading screen. 
*   **Fix**: The tests include a hydration guard: `await expect(page.locator('h1')).toContainText(/Welcome back!/i)`.

### 2. Eventual Consistency (Projections)
The system uses an Event Sourcing + CQRS architecture. After creating an account, it might take a few seconds for Kafka events to reach the read model.
*   **Fix**: Do NOT use `page.reload()`. Instead, the test uses `page.waitForFunction()` to poll the live DOM for the new account ID.

### 3. CORS and JWT
If API calls fail with 403 or CORS errors, verify:
*   `SecurityConfig.java` explicitly allows `http://localhost:3000`.
*   `setAllowCredentials(true)` is enabled in the CORS configuration.
```java
configuration.setAllowedOrigins(List.of("http://localhost:3000"));
configuration.setAllowCredentials(true);
```
