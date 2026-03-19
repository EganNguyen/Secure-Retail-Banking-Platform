# Frontend Plan & Specification (Phase 1 & 2 Integration)

## 1. Overview
The backend has completed Phase 1 and 2, exposing core retail banking capabilities. The frontend must now align to provide customer-facing UI for these features.

**Key Backend Capabilities Exposed:**
- **Account Management**: Open accounts, check account details, view all accounts for a customer, and manage account lifecycle (freeze, unfreeze, close).
- **Core Transfers**: Execute internal transfers between accounts securely, verify balances, and query transaction statuses.
- **Security**: The backend heavily utilizes Keycloak for Authentication and Authorization, meaning the frontend must securely integrate with Keycloak (OIDC/OAuth2) to handle user sessions and inject `Bearer` tokens into API requests.

---

## 2. Frontend Architecture Plan

### Recommended Tech Stack
Given the need for a highly secure, maintainable, and dynamic enterprise banking application:
- **Core Framework**: React (Next.js) with TypeScript for strong type safety.
- **Styling**: Vanilla CSS or CSS Modules (for maximum control, encapsulated styling, and premium aesthetic without over-reliance on utility frameworks unless specified).
- **State Management**: React Query (Server state integration, caching) + Zustand or React Context (Client/Auth state).
- **Authentication**: `keycloak-js` or `react-oidc-context` to manage OIDC flows with the Keycloak server.
- **HTTP Client**: Axios or native `fetch` with request interceptors for token injection and automatic 401 handling.

### Proposed Folder Structure
```text
src/
├── api/                  # API clients, Axios instances, interceptors
├── assets/               # Branding, icons, global visuals
├── components/           # Reusable UI elements (Button, Card, Modal, etc.)
├── features/             # Feature-based modules (Domain-driven)
│   ├── auth/             # Keycloak integration, Login, AuthContext
│   ├── accounts/         # Account list, creation, status management
│   └── transfers/        # Transfer forms, status pages
├── hooks/                # Custom hooks (e.g., useAuth, useAccounts)
├── pages/                # Route definitions and compose feature views
├── styles/               # Global Vanilla CSS tokens, themes, animations
├── types/                # Shared TypeScript interfaces (mirrored from backend DTOs)
└── utils/                # Formatters (currency, dates), validators
```

---

## 3. Feature Mapping

| Backend Feature (Phase 1 & 2) | Frontend Feature | Required API Endpoints | Key UI Components |
| :--- | :--- | :--- | :--- |
| **Authentication** | User Login & Session | Keycloak OIDC Endpoints | `LoginRedirect`, `AuthGuard` |
| **Customer Accounts** | Dashboard / Account List | `GET /api/v1/customers/{customerId}/accounts` | `AccountList`, `AccountCard` |
| **Account Details** | View Account Info & Balances| `GET /api/v1/accounts/{accountId}`<br>`GET /api/v1/accounts/{accountId}/balance` | `AccountDetailView`, `BalanceDisplay` |
| **Account Lifecycle** | Freeze/Unfreeze/Close Action | `POST /api/v1/accounts/{id}/[freeze/unfreeze/close]`| `AccountActionMenu`, `ConfirmModal` |
| **Open Account** | New Account Onboarding | `POST /api/v1/accounts` | `OpenAccountForm`, `SuccessScreen` |
| **Internal Transfer** | Transfer Money Form | `POST /api/v1/transfers` | `TransferForm`, `ReviewTransferModal` |
| **Transfer Status**| View Transfer Receipt | `GET /api/v1/transfers/{transferId}` | `TransferReceipt`, `StatusBadge` |

---

## 4. Page & Component Specifications

### Pages / Screens
1. **`LoginRoute / Landing`**: Handles Keycloak redirect and unauthenticated state.
2. **`DashboardPage`**:
   - Shows total wealth.
   - Lists all user accounts via `AccountList`.
3. **`AccountDetailsPage`**:
   - Detailed view of specific account.
   - Action buttons for changing status (Freeze/Close).
   - Real-time balance fetch.
4. **`TransferPage`**:
   - Account selection (Source & Destination).
   - Amount input with real-time balance validation.
5. **`TransferSuccessPage`**:
   - Displays receipt and status of initiated transfer.

### Key Components & Props
- **`AccountCard`**
  - *Props*: `accountId`, `type`, `balance`, `currency`, `status`
  - *Interactions*: Click to navigate to details.
- **`TransferForm`**
  - *State*: `sourceAccountId`, `destinationAccountId`, `amount`, `beneficiaryName`, `reference`
  - *Validation*: Amount > 0, Source != Destination, Amount <= Available Balance.
- **`AuthGuard`**
  - *Behavior*: Wraps protected routes. If no valid Keycloak session, redirects to login.

---

## 5. API Contracts (Frontend Perspective)

We will mirror the exact shapes expected by the Phase 1 & 2 backend controllers.

### Headers required for all API calls:
- `Authorization: Bearer <keycloak_token>`
- `Content-Type: application/json`
- *(Optional)* `X-Correlation-ID: <uuid>` for frontend observability.

### A. Accounts API
- **Open Account** (`POST /api/v1/accounts`)
  ```typescript
  // Request
  interface OpenAccountReq {
    customerId: string; // Fetched from Keycloak user profile
    type: "CHECKING" | "SAVINGS"; // Enums mapped from backend
    currency: "USD" | "EUR" | "GBP";
    productCode: string;
  }
  // Response
  interface AccountCreatedRes { accountId: string; }
  ```
- **Fetch Account & Balance** (`GET /api/v1/accounts/{id}`, `GET .../balance`)
  ```typescript
  // The frontend should combine these into one domain object if needed
  interface AccountRes { accountId, customerId, type, currency, productCode, status, openedAt }
  interface BalanceRes { accountId, availableBalance, currency, updatedAt }
  ```

### B. Transfers API
- **Initiate Transfer** (`POST /api/v1/transfers`)
  ```typescript
  // Request
  interface CreateTransferReq {
    sourceAccountId: string;
    destinationAccountId: string;
    beneficiaryName: string;
    amount: number; // Decimal mapped, ensure > 0.0001
    currency: string;
    reference: string;
  }
  // Response
  interface TransferRes { transferId, status, failureReason, failureDetail /* ... */  }
  ```

### Error Handling Strategy
- **401 Unauthorized**: Interceptor catches this, triggers Keycloak token refresh. If refresh fails, log out and redirect.
- **400 Bad Request**: Validation errors. Display inline below form fields.
- **404 Not Found**: generic "Data not found" boundary.
- **500 Server Error**: Global Error Boundary displaying a friendly "We are experiencing technical difficulties" message.

---

## 6. Data Flow & State Management

**Flow:**
1. App Initializes -> Check Keycloak session -> Extract `customerId` from token claim.
2. React Query fetches `/customers/{customerId}/accounts` -> caches list.
3. User selects transfer -> Context holds "selected source account".
4. User submits transfer -> Mutation runs via React Query -> On success, invalidates Account Balance caches immediately.

**State Divisions:**
- **Local Form State** (e.g., typing amount): React `useState` / `react-hook-form`.
- **Global App State** (e.g., Theme, Auth User): React Context or Zustand.
- **Server State** (e.g., Accounts, Balances, Transfers): React Query (`useQuery`, `useMutation`).

---

## 7. Edge Cases & Validation

- **Loading States**: Use CSS skeleton loaders matching the exact shape of `AccountCard` or details view while React Query is in `isPending` state. Avoid layout shift.
- **Empty States**: If a customer has no accounts, display a compelling "Open Your First Account" premium banner rather than a blank table.
- **Action Validation**:
  - Prevent transfers from inherently blocked accounts (status = `FROZEN` or `CLOSED`).
  - Disable submit button if requested `amount` > `availableBalance`.
- **Idempotency**: Use `X-Correlation-ID` or disable the "Transfer" button immediately on click to prevent double submission.

---

## 8. Milestones / Implementation Plan

### Phase 1: Foundation & Auth (Weeks 1-2)
- Initialize Vite/Next.js repository.
- Setup Vanilla CSS global custom properties (colors, typography for premium feel).
- Implement Keycloak integration (`AuthGuard`, Login/Logout flows).
- Establish Axios interceptors and typed API layer.

### Phase 2: Account Dashboard & Management (Weeks 2-3)
- Build `DashboardPage` and `AccountList`.
- Connect `GET /customers/{customerId}/accounts` API.
- Create `AccountDetailsPage` and wire up `Balance` fetching.
- Implement forms for Account Creation (`POST /accounts`).
- Integrate account lifecycle endpoints (freeze, unfreeze, close).

### Phase 3: Core Transfers & Polish (Weeks 4-5)
- Build robust internal `TransferForm` with client-side balance validation.
- Connect `POST /api/v1/transfers` and handle success/failure flows via sagas feedback.
- Add `TransferReceipt` page for transaction query.
- Final UI/UX polish (micro-animations, skeleton loaders, error boundaries).
