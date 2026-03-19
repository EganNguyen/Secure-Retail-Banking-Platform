# Secure Retail Banking Platform — Frontend LLM Implementation Specification

> **Target Stack:** Next.js 15 (App Router) · TypeScript 5.x · TailwindCSS 4 · TanStack Query v5 · Zustand · Zod · Storybook  
> **Backend Contract:** Pairs with `retail-banking-platform-spec.md` (Java / Event-Driven / DDD / CQRS)  
> **Audience:** LLM agent / developer implementing the full banking frontend end-to-end

---

## Table of Contents

1. [Frontend Vision & Design System](#1-frontend-vision--design-system)
2. [Project Structure & Module Architecture](#2-project-structure--module-architecture)
3. [Technology Stack Reference](#3-technology-stack-reference)
4. [Authentication & Security Layer](#4-authentication--security-layer)
5. [API Client Layer & Backend Integration](#5-api-client-layer--backend-integration)
6. [Real-Time WebSocket Integration](#6-real-time-websocket-integration)
7. [State Management Architecture](#7-state-management-architecture)
8. [Page & Feature Blueprints](#8-page--feature-blueprints)
9. [Transfer Flow UI](#9-transfer-flow-ui)
10. [Component Library Specification](#10-component-library-specification)
11. [Form Architecture & Validation](#11-form-architecture--validation)
12. [Data Fetching & Caching Strategy](#12-data-fetching--caching-strategy)
13. [Error Handling & Resilience UI](#13-error-handling--resilience-ui)
14. [Accessibility & Internationalisation](#14-accessibility--internationalisation)
15. [Performance Architecture](#15-performance-architecture)
16. [Testing Strategy](#16-testing-strategy)
17. [Infrastructure & Deployment](#17-infrastructure--deployment)
18. [LLM Implementation Instructions](#18-llm-implementation-instructions)

---

## 1. Frontend Vision & Design System

### 1.1 Design Philosophy

The UI reflects the backend's qualities: **precision, immutability, trust**. The aesthetic is **refined financial-grade** — not a consumer fintech toy, not a legacy bank monolith. Think: private bank terminal meets modern SaaS dashboard.

**Tone:** Luxury-utilitarian. Dense with information but architecturally clean. Every pixel earns its space.

**Design Pillars:**

| Pillar | Expression |
|---|---|
| **Trust** | Restrained palette, consistent spacing grid, predictable interactions |
| **Precision** | Monospaced numbers, tight typographic hierarchy, exact alignment |
| **Immediacy** | Real-time balance updates, live transfer status, skeleton-free perceived speed |
| **Clarity** | Progressive disclosure, never show data the user didn't ask for |

### 1.2 Design Tokens

```css
/* tokens.css — global CSS custom properties */
:root {
  /* Palette */
  --color-bg-base:        #0A0C0F;   /* near-black canvas */
  --color-bg-surface:     #111318;   /* card surfaces */
  --color-bg-elevated:    #181C24;   /* modals, dropdowns */
  --color-bg-subtle:      #1E232D;   /* hover states, inputs */
  --color-border:         #262C38;
  --color-border-strong:  #3A4255;

  /* Brand accent — sharp electric indigo */
  --color-accent:         #4F6EF7;
  --color-accent-hover:   #6B85FF;
  --color-accent-muted:   rgba(79, 110, 247, 0.12);

  /* Semantic */
  --color-success:        #22C55E;
  --color-success-muted:  rgba(34, 197, 94, 0.10);
  --color-warning:        #F59E0B;
  --color-warning-muted:  rgba(245, 158, 11, 0.10);
  --color-danger:         #EF4444;
  --color-danger-muted:   rgba(239, 68, 68, 0.10);
  --color-frozen:         #6366F1;

  /* Text */
  --color-text-primary:   #F0F2F7;
  --color-text-secondary: #8A92A6;
  --color-text-tertiary:  #555E72;
  --color-text-inverse:   #0A0C0F;

  /* Typography */
  --font-display:  'DM Serif Display', Georgia, serif;     /* headings */
  --font-body:     'IBM Plex Sans', system-ui, sans-serif; /* UI text */
  --font-mono:     'IBM Plex Mono', 'Fira Code', monospace;/* amounts, IBANs, IDs */

  /* Spacing scale (4px base) */
  --space-1: 4px;   --space-2: 8px;   --space-3: 12px;  --space-4: 16px;
  --space-5: 20px;  --space-6: 24px;  --space-8: 32px;  --space-10: 40px;
  --space-12: 48px; --space-16: 64px; --space-20: 80px;

  /* Radii */
  --radius-sm:  4px;
  --radius-md:  8px;
  --radius-lg:  12px;
  --radius-xl:  16px;
  --radius-full: 9999px;

  /* Shadows */
  --shadow-sm:  0 1px 2px rgba(0,0,0,0.4);
  --shadow-md:  0 4px 16px rgba(0,0,0,0.5);
  --shadow-lg:  0 8px 40px rgba(0,0,0,0.6);
  --shadow-accent: 0 0 24px rgba(79, 110, 247, 0.25);

  /* Motion */
  --duration-fast:    120ms;
  --duration-base:    200ms;
  --duration-slow:    350ms;
  --ease-out:         cubic-bezier(0.0, 0.0, 0.2, 1);
  --ease-in-out:      cubic-bezier(0.4, 0.0, 0.2, 1);
  --ease-spring:      cubic-bezier(0.34, 1.56, 0.64, 1);
}
```

### 1.3 Typography Scale

```css
/* Applied via Tailwind plugin or direct CSS */
.text-display-xl { font-family: var(--font-display); font-size: 3rem;   line-height: 1.1; letter-spacing: -0.02em; }
.text-display-lg { font-family: var(--font-display); font-size: 2.25rem; line-height: 1.15; letter-spacing: -0.015em; }
.text-heading-xl { font-family: var(--font-body); font-size: 1.5rem;  font-weight: 600; line-height: 1.3; }
.text-heading-lg { font-family: var(--font-body); font-size: 1.25rem; font-weight: 600; line-height: 1.35; }
.text-heading-md { font-family: var(--font-body); font-size: 1rem;   font-weight: 600; line-height: 1.4; }
.text-body-lg    { font-family: var(--font-body); font-size: 1rem;   font-weight: 400; line-height: 1.6; }
.text-body-md    { font-family: var(--font-body); font-size: 0.875rem; font-weight: 400; line-height: 1.5; }
.text-body-sm    { font-family: var(--font-body); font-size: 0.75rem;  font-weight: 400; line-height: 1.5; }
.text-label      { font-family: var(--font-body); font-size: 0.75rem;  font-weight: 500; letter-spacing: 0.06em; text-transform: uppercase; }
.text-mono-lg    { font-family: var(--font-mono); font-size: 1.25rem; font-weight: 500; }
.text-mono-md    { font-family: var(--font-mono); font-size: 0.875rem; }
.text-mono-sm    { font-family: var(--font-mono); font-size: 0.75rem; }
```

### 1.4 Motion System

```typescript
// lib/motion/variants.ts — reusable Framer Motion variants
export const fadeUp = {
  hidden:  { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.25, ease: [0, 0, 0.2, 1] } },
};

export const staggerContainer = {
  hidden:  {},
  visible: { transition: { staggerChildren: 0.06, delayChildren: 0.1 } },
};

export const slideIn = {
  hidden:  { opacity: 0, x: -8 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.2 } },
};

export const scaleIn = {
  hidden:  { opacity: 0, scale: 0.96 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.18, ease: [0.34, 1.56, 0.64, 1] } },
};

// Page transition wrapper
export const pageTransition = {
  initial:  { opacity: 0, y: 6 },
  animate:  { opacity: 1, y: 0 },
  exit:     { opacity: 0, y: -4 },
  transition: { duration: 0.22, ease: [0, 0, 0.2, 1] },
};
```

---

## 2. Project Structure & Module Architecture

### 2.1 Directory Layout

```
retail-banking-frontend/
├── app/                                  # Next.js 15 App Router
│   ├── (auth)/                           # Auth route group (no sidebar)
│   │   ├── login/
│   │   │   └── page.tsx
│   │   ├── callback/
│   │   │   └── page.tsx                  # OIDC callback handler
│   │   └── layout.tsx
│   ├── (banking)/                        # Authenticated route group
│   │   ├── dashboard/
│   │   │   └── page.tsx
│   │   ├── accounts/
│   │   │   ├── page.tsx                  # Account list
│   │   │   └── [accountId]/
│   │   │       ├── page.tsx              # Account detail + transactions
│   │   │       ├── statements/
│   │   │       │   └── page.tsx
│   │   │       └── loading.tsx
│   │   ├── transfers/
│   │   │   ├── page.tsx                  # Transfer history
│   │   │   ├── new/
│   │   │   │   └── page.tsx              # New transfer wizard
│   │   │   └── [transferId]/
│   │   │       └── page.tsx              # Transfer detail / tracking
│   │   ├── notifications/
│   │   │   └── page.tsx
│   │   └── layout.tsx                    # Shell with sidebar + topbar
│   ├── api/                              # Next.js Route Handlers
│   │   ├── auth/
│   │   │   └── [...nextauth]/route.ts    # Auth.js OIDC routes
│   │   └── health/route.ts
│   ├── globals.css
│   ├── layout.tsx                        # Root layout (fonts, providers)
│   └── not-found.tsx
│
├── components/                           # Shared UI components
│   ├── ui/                               # Primitives (Button, Input, Badge…)
│   ├── banking/                          # Domain-aware components
│   │   ├── AccountCard/
│   │   ├── BalanceDisplay/
│   │   ├── TransactionRow/
│   │   ├── TransferStatusTracker/
│   │   ├── RiskBadge/
│   │   └── AmountInput/
│   ├── layout/                           # Shell chrome
│   │   ├── Sidebar/
│   │   ├── TopBar/
│   │   └── MobileNav/
│   └── feedback/                         # Toasts, Alerts, Skeletons
│
├── features/                             # Feature slices (co-located logic)
│   ├── accounts/
│   │   ├── api.ts                        # TanStack Query hooks
│   │   ├── store.ts                      # Zustand slice
│   │   ├── types.ts                      # TypeScript interfaces
│   │   └── utils.ts
│   ├── transfers/
│   │   ├── api.ts
│   │   ├── store.ts
│   │   ├── types.ts
│   │   ├── validation.ts                 # Zod schemas
│   │   └── transfer-wizard/
│   │       ├── Step1Accounts.tsx
│   │       ├── Step2Amount.tsx
│   │       ├── Step3Confirm.tsx
│   │       └── Step4Status.tsx
│   ├── ledger/
│   │   ├── api.ts
│   │   └── types.ts
│   └── notifications/
│       ├── websocket.ts                  # WS manager
│       ├── store.ts
│       └── types.ts
│
├── lib/                                  # Infrastructure utilities
│   ├── api/
│   │   ├── client.ts                     # Axios/fetch base client
│   │   ├── interceptors.ts               # Auth + correlation ID injection
│   │   └── errors.ts                     # RFC 7807 error handling
│   ├── auth/
│   │   ├── config.ts                     # Auth.js config
│   │   └── guards.ts                     # Route protection utilities
│   ├── formatting/
│   │   ├── currency.ts                   # Intl.NumberFormat money
│   │   ├── iban.ts                       # IBAN masking + formatting
│   │   └── date.ts                       # Date/time formatting
│   ├── motion/
│   │   └── variants.ts
│   └── utils.ts
│
├── hooks/                                # Shared custom hooks
│   ├── useRealTimeBalance.ts
│   ├── useTransferStatus.ts
│   ├── useIntersectionObserver.ts
│   └── useDebounce.ts
│
├── stores/                               # Global Zustand stores
│   ├── session.store.ts
│   ├── notifications.store.ts
│   └── ui.store.ts
│
├── types/                                # Global TypeScript types
│   ├── api.ts                            # Backend API response types
│   ├── domain.ts                         # Domain model types (mirrors Java)
│   └── next-auth.d.ts                    # Auth.js type augmentation
│
├── middleware.ts                         # Next.js middleware (auth guard)
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── .env.local.example
```

### 2.2 Module Boundaries

Each `features/{domain}/` module is self-contained:

```
features/transfers/
├── api.ts          ← ONLY place that calls backend transfer endpoints
├── store.ts        ← ONLY place that holds transfer UI state
├── types.ts        ← TypeScript types for this feature only
├── validation.ts   ← Zod schemas for transfer forms
└── transfer-wizard/← UI components specific to this feature
```

**Rules:**
- Features import from `lib/` and `components/ui/` — never from other features' internals
- Cross-feature communication via Zustand stores or React context only
- No backend URLs outside of `features/{domain}/api.ts` and `lib/api/client.ts`

---

## 3. Technology Stack Reference

### 3.1 Core Dependencies

```json
{
  "dependencies": {
    "next": "^15.0.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "typescript": "^5.5.0",

    "@tanstack/react-query": "^5.56.0",
    "@tanstack/react-query-devtools": "^5.56.0",
    "zustand": "^5.0.0",
    "immer": "^10.1.1",

    "zod": "^3.23.0",
    "react-hook-form": "^7.53.0",
    "@hookform/resolvers": "^3.9.0",

    "framer-motion": "^11.5.0",
    "next-auth": "^5.0.0-beta",

    "axios": "^1.7.0",
    "date-fns": "^4.1.0",
    "clsx": "^2.1.1",
    "tailwind-merge": "^2.5.0",

    "@radix-ui/react-dialog":        "^1.1.0",
    "@radix-ui/react-dropdown-menu": "^2.1.0",
    "@radix-ui/react-popover":       "^1.1.0",
    "@radix-ui/react-select":        "^2.1.0",
    "@radix-ui/react-tooltip":       "^1.1.0",
    "@radix-ui/react-toast":         "^1.2.0",
    "@radix-ui/react-tabs":          "^1.1.0",
    "@radix-ui/react-progress":      "^1.1.0",

    "recharts": "^2.13.0",
    "react-virtuoso": "^4.10.0",
    "sonner": "^1.5.0"
  },
  "devDependencies": {
    "tailwindcss": "^4.0.0",
    "@testing-library/react": "^16.0.0",
    "@testing-library/user-event": "^14.5.0",
    "vitest": "^2.1.0",
    "@vitejs/plugin-react": "^4.3.0",
    "playwright": "^1.47.0",
    "msw": "^2.4.0",
    "storybook": "^8.3.0",
    "@storybook/nextjs": "^8.3.0"
  }
}
```

### 3.2 Next.js 15 Configuration

```typescript
// next.config.ts
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  experimental: {
    ppr: true,             // Partial Pre-rendering
    reactCompiler: true,   // React Compiler (auto-memo)
    typedRoutes: true,     // Type-safe navigation
  },
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options',           value: 'DENY' },
          { key: 'X-Content-Type-Options',    value: 'nosniff' },
          { key: 'Referrer-Policy',           value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy',        value: 'camera=(), microphone=(), geolocation=()' },
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              "script-src 'self' 'unsafe-inline'",   // remove unsafe-inline in prod with nonce
              "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
              "font-src 'self' https://fonts.gstatic.com",
              `connect-src 'self' ${process.env.NEXT_PUBLIC_API_URL} wss://${process.env.NEXT_PUBLIC_WS_HOST}`,
              "img-src 'self' data: blob:",
              "frame-ancestors 'none'",
            ].join('; '),
          },
        ],
      },
    ];
  },
  images: {
    formats: ['image/avif', 'image/webp'],
  },
};

export default nextConfig;
```

### 3.3 Tailwind 4 Configuration

```typescript
// tailwind.config.ts
import type { Config } from 'tailwindcss';

export default {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}', './features/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        display: ['var(--font-display)'],
        body:    ['var(--font-body)'],
        mono:    ['var(--font-mono)'],
      },
      colors: {
        bg: {
          base:     'var(--color-bg-base)',
          surface:  'var(--color-bg-surface)',
          elevated: 'var(--color-bg-elevated)',
          subtle:   'var(--color-bg-subtle)',
        },
        border:  { DEFAULT: 'var(--color-border)', strong: 'var(--color-border-strong)' },
        accent:  { DEFAULT: 'var(--color-accent)', hover: 'var(--color-accent-hover)', muted: 'var(--color-accent-muted)' },
        text:    { primary: 'var(--color-text-primary)', secondary: 'var(--color-text-secondary)', tertiary: 'var(--color-text-tertiary)' },
        success: { DEFAULT: 'var(--color-success)', muted: 'var(--color-success-muted)' },
        warning: { DEFAULT: 'var(--color-warning)', muted: 'var(--color-warning-muted)' },
        danger:  { DEFAULT: 'var(--color-danger)',  muted: 'var(--color-danger-muted)' },
      },
      keyframes: {
        'pulse-ring': {
          '0%':   { transform: 'scale(0.95)', opacity: '1' },
          '70%':  { transform: 'scale(1.1)',  opacity: '0' },
          '100%': { transform: 'scale(1.1)',  opacity: '0' },
        },
        'shimmer': {
          '0%':   { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        'slide-up': {
          from: { transform: 'translateY(8px)', opacity: '0' },
          to:   { transform: 'translateY(0)',   opacity: '1' },
        },
      },
      animation: {
        'pulse-ring': 'pulse-ring 1.5s cubic-bezier(0.215, 0.61, 0.355, 1) infinite',
        'shimmer':    'shimmer 2s linear infinite',
        'slide-up':   'slide-up 0.2s ease-out',
      },
    },
  },
} satisfies Config;
```

---

## 4. Authentication & Security Layer

### 4.1 Auth.js (v5) with Keycloak OIDC

The backend uses Keycloak (see backend spec §10.1). Auth.js v5 handles the PKCE flow.

```typescript
// lib/auth/config.ts
import NextAuth from 'next-auth';
import Keycloak from 'next-auth/providers/keycloak';

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Keycloak({
      clientId:     process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
      issuer:       process.env.KEYCLOAK_ISSUER!,   // https://auth.bank.com/realms/retail
    }),
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      // On initial sign-in, persist backend-specific claims
      if (account) {
        token.accessToken  = account.access_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt    = account.expires_at;
        // Map backend JWT claims (see backend spec §10.1)
        token.customerId   = (profile as any)?.customerId;
        token.accountIds   = (profile as any)?.accountIds ?? [];
        token.tier         = (profile as any)?.tier;
        token.kycLevel     = (profile as any)?.kycLevel;
      }
      // Token refresh when expiring
      if (Date.now() < (token.expiresAt as number) * 1000 - 30_000) return token;
      return refreshAccessToken(token);
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken as string;
      session.user.customerId  = token.customerId as string;
      session.user.accountIds  = token.accountIds as string[];
      session.user.tier        = token.tier as string;
      session.user.kycLevel    = token.kycLevel as string;
      return session;
    },
  },
  pages: {
    signIn:  '/login',
    error:   '/login',
  },
});

async function refreshAccessToken(token: any) {
  try {
    const res = await fetch(`${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type:    'refresh_token',
        client_id:     process.env.KEYCLOAK_CLIENT_ID!,
        client_secret: process.env.KEYCLOAK_CLIENT_SECRET!,
        refresh_token: token.refreshToken,
      }),
    });
    const refreshed = await res.json();
    if (!res.ok) throw refreshed;
    return { ...token, accessToken: refreshed.access_token, expiresAt: Math.floor(Date.now() / 1000) + refreshed.expires_in };
  } catch {
    return { ...token, error: 'RefreshAccessTokenError' };
  }
}
```

### 4.2 Type Augmentation for Auth.js

```typescript
// types/next-auth.d.ts
import { DefaultSession } from 'next-auth';

declare module 'next-auth' {
  interface Session {
    accessToken: string;
    user: DefaultSession['user'] & {
      customerId:  string;
      accountIds:  string[];
      tier:        'STANDARD' | 'PREMIUM' | 'PRIVATE';
      kycLevel:    'BASIC' | 'ENHANCED' | 'FULL';
    };
  }
}
```

### 4.3 Middleware — Route Protection

```typescript
// middleware.ts
import { auth } from '@/lib/auth/config';
import { NextResponse } from 'next/server';

export default auth((req) => {
  const { pathname } = req.nextUrl;
  const isAuthenticated = !!req.auth;
  const isAuthRoute = pathname.startsWith('/login') || pathname.startsWith('/callback');

  if (!isAuthenticated && !isAuthRoute) {
    const loginUrl = new URL('/login', req.url);
    loginUrl.searchParams.set('callbackUrl', pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (isAuthenticated && isAuthRoute) {
    return NextResponse.redirect(new URL('/dashboard', req.url));
  }

  // Enforce account ownership — middleware-level pre-check
  if (pathname.startsWith('/accounts/') && isAuthenticated) {
    const accountId = pathname.split('/')[2];
    const allowedAccounts = req.auth?.user?.accountIds ?? [];
    if (accountId && !allowedAccounts.includes(accountId)) {
      return NextResponse.redirect(new URL('/dashboard?error=access_denied', req.url));
    }
  }

  return NextResponse.next();
});

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
```

### 4.4 Frontend Security Practices

- **Never store JWT in localStorage** — Auth.js manages HTTP-only cookies
- **CSRF protection** — Auth.js v5 uses double-submit cookie pattern
- **XSS prevention** — React escapes by default; never use `dangerouslySetInnerHTML` with user data
- **Sensitive data masking** — IBANs displayed as `DE89 **** **** **** 3000` until user clicks to reveal
- **Auto-logout** — On `RefreshAccessTokenError`, trigger `signOut()` immediately
- **Content Security Policy** — Set via `next.config.ts` headers (§3.2)
- **Input sanitization** — All form inputs validated with Zod before any API call

---

## 5. API Client Layer & Backend Integration

### 5.1 Base API Client

```typescript
// lib/api/client.ts
import axios, { AxiosInstance } from 'axios';
import { getSession } from 'next-auth/react';
import { v4 as uuidv4 } from 'uuid';

export function createApiClient(): AxiosInstance {
  const client = axios.create({
    baseURL:        process.env.NEXT_PUBLIC_API_URL,
    timeout:        30_000,
    headers: {
      'Content-Type': 'application/json',
      'Accept':       'application/json',
    },
  });

  // Request interceptor: attach Bearer token + correlation ID
  client.interceptors.request.use(async (config) => {
    const session = await getSession();
    if (session?.accessToken) {
      config.headers.Authorization = `Bearer ${session.accessToken}`;
    }
    // Every request gets a unique correlation ID (traceable in backend logs)
    config.headers['X-Correlation-ID'] = uuidv4();
    return config;
  });

  // Response interceptor: normalize errors
  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error.response?.status === 401) {
        // Token expired mid-session, force re-auth
        await import('next-auth/react').then(({ signOut }) => signOut({ callbackUrl: '/login' }));
      }
      return Promise.reject(normalizeApiError(error));
    }
  );

  return client;
}

export const apiClient = createApiClient();
```

### 5.2 RFC 7807 Error Normalization

The backend returns Problem Details (see backend spec §11.5):

```typescript
// lib/api/errors.ts
export interface ApiProblem {
  type:      string;
  title:     string;
  status:    number;
  detail:    string;
  instance?: string;
  traceId?:  string;
  timestamp: string;
}

export class BankingApiError extends Error {
  constructor(
    public readonly problem: ApiProblem,
    public readonly httpStatus: number,
  ) {
    super(problem.detail);
    this.name = 'BankingApiError';
  }

  get isInsufficientFunds() { return this.problem.type.includes('insufficient-funds'); }
  get isAccountFrozen()     { return this.problem.type.includes('account-frozen'); }
  get isTransferBlocked()   { return this.problem.type.includes('transfer-blocked'); }
  get isRateLimited()       { return this.httpStatus === 429; }
}

export function normalizeApiError(error: any): BankingApiError {
  const problem: ApiProblem = error.response?.data ?? {
    type:      'about:blank',
    title:     'Unexpected Error',
    status:    error.response?.status ?? 0,
    detail:    error.message ?? 'An unexpected error occurred',
    timestamp: new Date().toISOString(),
  };
  return new BankingApiError(problem, error.response?.status ?? 0);
}
```

### 5.3 Transfer API Client

```typescript
// features/transfers/api.ts
import { apiClient } from '@/lib/api/client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { v4 as uuidv4 } from 'uuid';
import type { InitiateTransferRequest, TransferResponse, TransferStatus } from './types';

// Query keys — centralized to avoid string duplication
export const transferKeys = {
  all:    () => ['transfers'] as const,
  list:   (accountId: string) => ['transfers', 'list', accountId] as const,
  detail: (transferId: string) => ['transfers', 'detail', transferId] as const,
};

export function useInitiateTransfer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (request: InitiateTransferRequest) => {
      const idempotencyKey = uuidv4(); // generate per attempt
      const { data } = await apiClient.post<TransferResponse>(
        '/api/v1/transfers',
        request,
        { headers: { 'X-Idempotency-Key': idempotencyKey } }
      );
      return data;
    },
    onSuccess: (data) => {
      // Optimistically invalidate balance + transaction queries
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Pre-populate detail cache
      queryClient.setQueryData(transferKeys.detail(data.transferId), data);
    },
  });
}

export function useTransferStatus(transferId: string, enabled: boolean) {
  return useQuery({
    queryKey: transferKeys.detail(transferId),
    queryFn: async () => {
      const { data } = await apiClient.get<TransferResponse>(`/api/v1/transfers/${transferId}`);
      return data;
    },
    enabled,
    refetchInterval: (query) => {
      // Poll until terminal state — WebSocket handles real-time, this is fallback
      const status = query.state.data?.status;
      const isTerminal = ['COMPLETED', 'FAILED', 'BLOCKED', 'REVERSED'].includes(status ?? '');
      return isTerminal ? false : 3000;
    },
    staleTime: 0,
  });
}
```

### 5.4 Account API Client

```typescript
// features/accounts/api.ts
export const accountKeys = {
  all:          () => ['accounts'] as const,
  byCustomer:   (customerId: string) => ['accounts', 'customer', customerId] as const,
  detail:       (accountId: string) => ['accounts', 'detail', accountId] as const,
  balance:      (accountId: string) => ['accounts', 'balance', accountId] as const,
  transactions: (accountId: string, params: TransactionQueryParams) =>
                  ['accounts', 'transactions', accountId, params] as const,
};

export function useAccountBalance(accountId: string) {
  return useQuery({
    queryKey: accountKeys.balance(accountId),
    queryFn: async () => {
      const { data } = await apiClient.get<BalanceResponse>(`/api/v1/accounts/${accountId}/balance`);
      return data;
    },
    staleTime:       5_000,   // balance is fresh for 5s (matches backend Redis TTL)
    refetchInterval: 30_000,  // background refresh every 30s
  });
}

export function useTransactionHistory(accountId: string, params: TransactionQueryParams) {
  return useInfiniteQuery({
    queryKey: accountKeys.transactions(accountId, params),
    queryFn: async ({ pageParam = 0 }) => {
      const { data } = await apiClient.get(`/api/v1/accounts/${accountId}/transactions`, {
        params: { ...params, page: pageParam, size: 20 },
      });
      return data;
    },
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    initialPageParam: 0,
  });
}
```

---

## 6. Real-Time WebSocket Integration

The backend exposes `WS /api/v1/ws/notifications` (see backend spec §11.4).

### 6.1 WebSocket Manager

```typescript
// features/notifications/websocket.ts
import { useNotificationsStore } from '@/stores/notifications.store';
import { useAccountsStore }      from '@/stores/accounts.store';
import { getSession }            from 'next-auth/react';

type WsEvent =
  | { type: 'TRANSFER_COMPLETED'; data: TransferCompletedPayload }
  | { type: 'TRANSFER_FAILED';    data: TransferFailedPayload }
  | { type: 'ACCOUNT_FROZEN';     data: AccountFrozenPayload }
  | { type: 'BALANCE_UPDATED';    data: BalanceUpdatedPayload };

class BankingWebSocket {
  private ws:           WebSocket | null = null;
  private reconnectMs:  number = 1_000;
  private maxReconnect: number = 30_000;
  private disposed:     boolean = false;
  private pingInterval: ReturnType<typeof setInterval> | null = null;

  async connect(): Promise<void> {
    if (this.disposed) return;
    const session = await getSession();
    if (!session?.accessToken) return;

    const url = `${process.env.NEXT_PUBLIC_WS_URL}?token=${session.accessToken}`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      this.reconnectMs = 1_000; // reset backoff
      this.startPing();
    };

    this.ws.onmessage = (event) => {
      try {
        const msg: WsEvent = JSON.parse(event.data);
        this.dispatch(msg);
      } catch (e) {
        console.error('[WS] Failed to parse message', e);
      }
    };

    this.ws.onclose = () => {
      this.stopPing();
      if (!this.disposed) this.scheduleReconnect();
    };

    this.ws.onerror = () => this.ws?.close();
  }

  private dispatch(event: WsEvent): void {
    const notifStore  = useNotificationsStore.getState();
    const accountStore = useAccountsStore.getState();

    switch (event.type) {
      case 'TRANSFER_COMPLETED':
        notifStore.addNotification({ type: 'success', ...event.data });
        // Invalidate balance in TanStack Query cache
        window.dispatchEvent(new CustomEvent('balance:refresh', { detail: event.data }));
        break;
      case 'TRANSFER_FAILED':
        notifStore.addNotification({ type: 'error', ...event.data });
        break;
      case 'ACCOUNT_FROZEN':
        notifStore.addNotification({ type: 'warning', ...event.data });
        accountStore.markAccountFrozen(event.data.accountId);
        break;
      case 'BALANCE_UPDATED':
        accountStore.updateBalance(event.data.accountId, event.data);
        break;
    }
  }

  private scheduleReconnect(): void {
    setTimeout(() => this.connect(), this.reconnectMs);
    this.reconnectMs = Math.min(this.reconnectMs * 2, this.maxReconnect);
  }

  private startPing(): void {
    this.pingInterval = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'PING' }));
      }
    }, 25_000);
  }

  private stopPing(): void {
    if (this.pingInterval) clearInterval(this.pingInterval);
  }

  dispose(): void {
    this.disposed = true;
    this.stopPing();
    this.ws?.close();
  }
}

export const bankingWs = new BankingWebSocket();
```

### 6.2 WebSocket Provider

```typescript
// app/(banking)/layout.tsx
'use client';
import { useEffect } from 'react';
import { bankingWs } from '@/features/notifications/websocket';
import { useQueryClient } from '@tanstack/react-query';

export default function BankingLayout({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();

  useEffect(() => {
    bankingWs.connect();

    // TanStack Query cache invalidation bridge
    const handleBalanceRefresh = (e: CustomEvent) => {
      queryClient.invalidateQueries({ queryKey: ['accounts', 'balance', e.detail.accountId] });
      queryClient.invalidateQueries({ queryKey: ['accounts', 'transactions', e.detail.accountId] });
    };
    window.addEventListener('balance:refresh', handleBalanceRefresh as EventListener);

    return () => {
      bankingWs.dispose();
      window.removeEventListener('balance:refresh', handleBalanceRefresh as EventListener);
    };
  }, [queryClient]);

  return <>{children}</>;
}
```

### 6.3 Real-Time Balance Hook

```typescript
// hooks/useRealTimeBalance.ts
export function useRealTimeBalance(accountId: string) {
  const queryClient = useQueryClient();
  const queryResult = useAccountBalance(accountId);

  // Optimistic update when WS arrives before query re-fetches
  useEffect(() => {
    const handleUpdate = (e: CustomEvent<BalanceUpdatedPayload>) => {
      if (e.detail.accountId !== accountId) return;
      queryClient.setQueryData(accountKeys.balance(accountId), (old: BalanceResponse | undefined) =>
        old ? { ...old, balance: e.detail.balance, asOf: e.detail.updatedAt } : old
      );
    };
    window.addEventListener('balance:refresh', handleUpdate as EventListener);
    return () => window.removeEventListener('balance:refresh', handleUpdate as EventListener);
  }, [accountId, queryClient]);

  return queryResult;
}
```

---

## 7. State Management Architecture

### 7.1 Store Topology

```
TanStack Query (server state)        Zustand (client state)
─────────────────────────────        ─────────────────────
Account data + balances              UI state (sidebar open, modals)
Transaction history (paginated)      Notification queue
Transfer details + status            Active transfer wizard step
Risk scores (cached)                 Account freeze optimistic state
                                     Session metadata
```

**Rule:** If data comes from the backend, it lives in TanStack Query. If it's pure UI state, it lives in Zustand.

### 7.2 Notifications Store

```typescript
// stores/notifications.store.ts
import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';

interface Notification {
  id:        string;
  type:      'success' | 'error' | 'warning' | 'info';
  title:     string;
  message?:  string;
  data?:     unknown;
  createdAt: Date;
  read:      boolean;
}

interface NotificationsState {
  notifications: Notification[];
  unreadCount:   number;
  addNotification: (n: Omit<Notification, 'id' | 'createdAt' | 'read'>) => void;
  markAllRead:     () => void;
  dismiss:         (id: string) => void;
}

export const useNotificationsStore = create<NotificationsState>()(
  immer((set) => ({
    notifications: [],
    unreadCount:   0,
    addNotification: (n) => set((state) => {
      const notification: Notification = {
        ...n, id: crypto.randomUUID(), createdAt: new Date(), read: false
      };
      state.notifications.unshift(notification);
      state.unreadCount++;
      // Cap at 100 notifications
      if (state.notifications.length > 100) state.notifications.pop();
    }),
    markAllRead: (set) => set((state) => {
      state.notifications.forEach(n => { n.read = true; });
      state.unreadCount = 0;
    }),
    dismiss: (id) => set((state) => {
      const idx = state.notifications.findIndex(n => n.id === id);
      if (idx !== -1) {
        if (!state.notifications[idx].read) state.unreadCount--;
        state.notifications.splice(idx, 1);
      }
    }),
  }))
);
```

### 7.3 Transfer Wizard Store

```typescript
// features/transfers/store.ts
import { create } from 'zustand';

type WizardStep = 'accounts' | 'amount' | 'confirm' | 'status';

interface TransferWizardState {
  step:            WizardStep;
  sourceAccountId: string | null;
  destination:     TransferDestination | null;
  amount:          string;
  currency:        string;
  remittanceInfo:  string;
  transferId:      string | null;
  idempotencyKey:  string;

  setStep:        (step: WizardStep) => void;
  setSource:      (id: string) => void;
  setDestination: (dest: TransferDestination) => void;
  setAmount:      (amount: string, currency: string) => void;
  setRemittance:  (info: string) => void;
  setTransferId:  (id: string) => void;
  reset:          () => void;
}

const initialState = {
  step: 'accounts' as WizardStep,
  sourceAccountId: null, destination: null, amount: '',
  currency: 'EUR', remittanceInfo: '', transferId: null,
  idempotencyKey: crypto.randomUUID(),
};

export const useTransferWizardStore = create<TransferWizardState>()((set) => ({
  ...initialState,
  setStep:        (step) => set({ step }),
  setSource:      (id) => set({ sourceAccountId: id }),
  setDestination: (dest) => set({ destination: dest }),
  setAmount:      (amount, currency) => set({ amount, currency }),
  setRemittance:  (info) => set({ remittanceInfo: info }),
  setTransferId:  (id) => set({ transferId: id }),
  reset:          () => set({ ...initialState, idempotencyKey: crypto.randomUUID() }),
}));
```

---

## 8. Page & Feature Blueprints

### 8.1 Dashboard Page

**Route:** `/dashboard`  
**Data:** Accounts summary, recent transactions (last 5 per account), pending transfers

```typescript
// app/(banking)/dashboard/page.tsx
import { auth } from '@/lib/auth/config';
import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';
import { prefetchAccountsByCustomer } from '@/features/accounts/api.server';

export default async function DashboardPage() {
  const session = await auth();
  const queryClient = new QueryClient();

  // Server-side prefetch for instant paint
  await prefetchAccountsByCustomer(queryClient, session!.user.customerId);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DashboardClient customerId={session!.user.customerId} />
    </HydrationBoundary>
  );
}
```

**UI Sections:**
- **Net Worth Banner** — animated counter showing total balance across all accounts
- **Account Cards Grid** — 2-column responsive grid; each card shows balance + last transaction
- **Quick Transfer CTA** — prominent button leading to `/transfers/new`
- **Recent Activity Feed** — virtualized list, last 10 transactions across all accounts
- **Spending Insights Widget** — Recharts donut chart of transaction categories (current month)

### 8.2 Account Detail Page

**Route:** `/accounts/[accountId]`  
**Tabs:** Overview · Transactions · Statements

**Overview Tab:**
- Large balance display (monospace, animates on real-time update)
- Account meta: IBAN (masked with reveal), product, status badge, opened date
- Balance breakdown: Current / Available / Reserved with progress bar visualization
- Pending transactions list

**Transactions Tab:**
- Filter bar: date range, type (debit/credit), amount range, search
- Virtualized infinite-scroll list (react-virtuoso) — handles 10,000+ entries
- Each row: merchant name, category icon, amount, balance after
- Click → side panel with full transaction detail (IBAN, reference, risk score if elevated)

**Statements Tab:**
- Month/year picker grid
- Generate PDF button (calls backend `/api/v1/accounts/{id}/statements/{year}/{month}`)
- Statement preview in-page

### 8.3 Transfer History Page

**Route:** `/transfers`

- Status filter tabs: All / Pending / Completed / Failed
- Transfer list with: recipient, amount, rail badge (INTERNAL/SEPA/SWIFT), status, time
- Click → transfer detail page

### 8.4 Transfer Detail Page

**Route:** `/transfers/[transferId]`

- Full saga event timeline (maps to `transfer.events` array from API)
- Risk score badge with signal breakdown (expandable)
- Amount breakdown: principal, FX rate if applicable, fees
- Reversal status if failed
- Share / Download receipt button

---

## 9. Transfer Flow UI

The new transfer flow is a **4-step wizard** at `/transfers/new`. It maps directly to the backend Transfer Saga (backend spec §8.1).

### 9.1 Step 1 — Select Accounts

```typescript
// features/transfers/transfer-wizard/Step1Accounts.tsx
export function Step1Accounts() {
  const { setSource, setDestination, setStep } = useTransferWizardStore();
  const { data: accounts } = useCustomerAccounts();

  return (
    <motion.div variants={fadeUp} initial="hidden" animate="visible">
      <h2 className="text-heading-xl text-text-primary mb-6">From Account</h2>

      <div className="grid gap-3 mb-8">
        {accounts?.map(account => (
          <AccountSelectCard
            key={account.accountId}
            account={account}
            disabled={account.status !== 'ACTIVE'}
            onSelect={(id) => setSource(id)}
          />
        ))}
      </div>

      <h2 className="text-heading-xl text-text-primary mb-6">To</h2>
      <DestinationSelector onSelect={(dest) => {
        setDestination(dest);
        setStep('amount');
      }} />
    </motion.div>
  );
}
```

### 9.2 Step 2 — Enter Amount

```typescript
// features/transfers/transfer-wizard/Step2Amount.tsx
export function Step2Amount() {
  const { sourceAccountId, destination, setAmount, setStep } = useTransferWizardStore();
  const { data: balance } = useAccountBalance(sourceAccountId!);
  const { data: limits }  = useTransferLimits(sourceAccountId!);
  const { register, handleSubmit, watch, formState: { errors } } = useForm({
    resolver: zodResolver(transferAmountSchema),
  });

  const enteredAmount = watch('amount');

  return (
    <motion.div variants={fadeUp} initial="hidden" animate="visible">
      <AmountInput
        currency={balance?.balance.currency ?? 'EUR'}
        availableBalance={balance?.balance.available}
        {...register('amount')}
      />

      {/* Live limit check */}
      {limits && (
        <LimitProgressBar
          label="Daily limit remaining"
          used={limits.consumed}
          total={limits.limit}
        />
      )}

      {/* Real-time FX preview for cross-currency */}
      {destination?.currency !== balance?.balance.currency && (
        <FxPreviewBanner
          fromCurrency={balance?.balance.currency}
          toCurrency={destination?.currency}
          amount={enteredAmount}
        />
      )}

      <Button
        onClick={handleSubmit(data => {
          setAmount(data.amount, data.currency);
          setStep('confirm');
        })}
        className="w-full mt-8"
      >
        Continue
      </Button>
    </motion.div>
  );
}
```

### 9.3 Step 3 — Confirm

Displays a full summary before submission. The submit button calls `useInitiateTransfer()`.

Key UX considerations:
- Show `X-Idempotency-Key` is pre-generated and stored in wizard store
- Submit button shows loading spinner; disabled during mutation
- On error: display specific message based on `BankingApiError` type (`isInsufficientFunds`, `isAccountFrozen`, `isTransferBlocked`)
- On `TRANSFER_BLOCKED` error: show risk score + case ID + "Contact support" CTA

### 9.4 Step 4 — Transfer Status Tracker

```typescript
// features/transfers/transfer-wizard/Step4Status.tsx
export function Step4Status() {
  const { transferId } = useTransferWizardStore();
  const { data: transfer } = useTransferStatus(transferId!, !!transferId);

  // Also listen for WS real-time update
  const wsUpdate = useNotificationsStore(s =>
    s.notifications.find(n => n.data?.transferId === transferId)
  );

  const isCompleted = transfer?.status === 'COMPLETED' || wsUpdate?.type === 'success';
  const isFailed    = transfer?.status === 'FAILED'    || wsUpdate?.type === 'error';

  return (
    <motion.div variants={scaleIn} initial="hidden" animate="visible" className="text-center">
      {/* Animated status ring */}
      <StatusRing status={transfer?.status ?? 'INITIATED'} />

      {/* Saga event timeline */}
      <TransferEventTimeline events={transfer?.events ?? []} />

      {isCompleted && (
        <motion.div variants={fadeUp} initial="hidden" animate="visible" className="mt-8">
          <p className="text-success text-heading-md">Transfer Complete</p>
          <BalanceUpdateFlash accountId={transfer!.sourceAccount.id} />
        </motion.div>
      )}

      {isFailed && <TransferFailureDetail transfer={transfer!} />}
    </motion.div>
  );
}
```

### 9.5 Transfer Saga Event Timeline

Maps the backend transfer event chain to a visual step-by-step tracker:

```typescript
const SAGA_STEPS: Record<string, { label: string; icon: string }> = {
  INITIATED:    { label: 'Transfer initiated',  icon: 'arrow-right' },
  RISK_SCORED:  { label: 'Security check',       icon: 'shield' },
  VALIDATED:    { label: 'Validated',             icon: 'check' },
  DEBITED:      { label: 'Funds reserved',        icon: 'minus' },
  CREDITED:     { label: 'Funds sent',            icon: 'plus' },
  SETTLING:     { label: 'Settling with bank',    icon: 'clock' },
  COMPLETED:    { label: 'Completed',             icon: 'check-circle' },
  FAILED:       { label: 'Failed',                icon: 'x-circle' },
  REVERSED:     { label: 'Reversed',              icon: 'rotate-ccw' },
};
```

---

## 10. Component Library Specification

### 10.1 Primitives — Button

```typescript
// components/ui/Button/Button.tsx
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center rounded-[var(--radius-md)] font-body font-medium transition-all duration-[var(--duration-fast)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg-base disabled:pointer-events-none disabled:opacity-40 select-none',
  {
    variants: {
      variant: {
        primary:   'bg-accent text-white hover:bg-accent-hover active:scale-[0.98] shadow-sm hover:shadow-accent',
        secondary: 'bg-bg-subtle text-text-primary border border-border hover:border-border-strong hover:bg-bg-elevated',
        ghost:     'text-text-secondary hover:text-text-primary hover:bg-bg-subtle',
        danger:    'bg-danger/10 text-danger border border-danger/20 hover:bg-danger/20',
        link:      'text-accent underline-offset-4 hover:underline p-0 h-auto',
      },
      size: {
        sm: 'h-8  px-3  text-body-sm gap-1.5',
        md: 'h-10 px-4  text-body-md gap-2',
        lg: 'h-12 px-6  text-body-lg gap-2.5',
        xl: 'h-14 px-8  text-heading-md gap-3',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  }
);

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>,
  VariantProps<typeof buttonVariants> {
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export function Button({ variant, size, loading, leftIcon, rightIcon, children, className, ...props }: ButtonProps) {
  return (
    <button className={cn(buttonVariants({ variant, size }), className)} disabled={loading || props.disabled} {...props}>
      {loading ? <Spinner size={size === 'sm' ? 12 : 16} /> : leftIcon}
      {children}
      {!loading && rightIcon}
    </button>
  );
}
```

### 10.2 Banking — BalanceDisplay

```typescript
// components/banking/BalanceDisplay/BalanceDisplay.tsx
import { useCountUp } from '@/hooks/useCountUp';
import { formatCurrency } from '@/lib/formatting/currency';

interface BalanceDisplayProps {
  amount:   number;
  currency: string;
  size?:    'sm' | 'md' | 'lg' | 'xl';
  animate?: boolean;
  dimCents?: boolean;
}

export function BalanceDisplay({ amount, currency, size = 'lg', animate = true, dimCents }: BalanceDisplayProps) {
  const displayAmount = useCountUp(amount, { duration: 600, enabled: animate });
  const formatted = formatCurrency(displayAmount, currency);
  const [whole, cents] = formatted.split('.');

  const sizeClasses = {
    sm: 'text-mono-md',
    md: 'text-mono-lg',
    lg: 'text-[2rem] font-mono font-semibold tracking-tight',
    xl: 'text-[3rem] font-mono font-semibold tracking-tight',
  };

  return (
    <span className={cn(sizeClasses[size], 'text-text-primary tabular-nums')}>
      {whole}
      {cents && (
        <span className={cn(dimCents && 'text-text-tertiary', 'text-[0.75em]')}>
          .{cents}
        </span>
      )}
    </span>
  );
}
```

### 10.3 Banking — AccountCard

```typescript
// components/banking/AccountCard/AccountCard.tsx
export function AccountCard({ account, onClick }: AccountCardProps) {
  const { data: balance } = useRealTimeBalance(account.accountId);

  return (
    <motion.div
      variants={scaleIn}
      whileHover={{ y: -2, transition: { duration: 0.15 } }}
      onClick={onClick}
      className={cn(
        'relative overflow-hidden rounded-xl p-6 cursor-pointer transition-colors',
        'bg-bg-surface border border-border hover:border-border-strong',
        account.status === 'FROZEN' && 'border-frozen/40 bg-frozen/5',
      )}
    >
      {/* Subtle gradient top-left accent */}
      <div className="absolute inset-0 bg-gradient-to-br from-accent/5 to-transparent pointer-events-none" />

      <div className="flex items-start justify-between mb-6">
        <div>
          <span className="text-label text-text-tertiary">{account.type}</span>
          <p className="text-body-md text-text-secondary font-mono mt-0.5">
            {maskIban(account.iban)}
          </p>
        </div>
        <AccountStatusBadge status={account.status} />
      </div>

      {balance ? (
        <BalanceDisplay
          amount={parseFloat(balance.balance.available.value)}
          currency={balance.balance.currency}
          size="lg"
          animate
        />
      ) : (
        <BalanceSkeleton />
      )}

      <div className="flex items-center justify-between mt-4 pt-4 border-t border-border">
        <span className="text-body-sm text-text-tertiary">Available</span>
        <span className="text-body-sm text-text-secondary">{account.productCode}</span>
      </div>
    </motion.div>
  );
}
```

### 10.4 Banking — TransactionRow

```typescript
// components/banking/TransactionRow/TransactionRow.tsx
export function TransactionRow({ transaction, onSelect }: TransactionRowProps) {
  const isDebit = transaction.debitCredit === 'D';

  return (
    <motion.button
      variants={slideIn}
      onClick={() => onSelect(transaction)}
      className="w-full flex items-center gap-4 p-4 hover:bg-bg-subtle rounded-lg transition-colors text-left group"
    >
      {/* Category icon */}
      <div className="flex-shrink-0 w-10 h-10 rounded-full bg-bg-elevated flex items-center justify-center">
        <TransactionCategoryIcon category={transaction.category} />
      </div>

      {/* Description + date */}
      <div className="flex-1 min-w-0">
        <p className="text-body-md text-text-primary truncate font-medium">
          {transaction.counterpartName ?? transaction.description}
        </p>
        <p className="text-body-sm text-text-tertiary">
          {formatDate(transaction.bookedAt, 'dd MMM · HH:mm')}
        </p>
      </div>

      {/* Amount */}
      <div className="text-right flex-shrink-0">
        <p className={cn(
          'text-mono-md font-semibold tabular-nums',
          isDebit ? 'text-text-primary' : 'text-success',
        )}>
          {isDebit ? '−' : '+'}{formatCurrency(transaction.amount, transaction.currency)}
        </p>
        <p className="text-mono-sm text-text-tertiary">
          {formatCurrency(transaction.balanceAfter, transaction.currency)}
        </p>
      </div>
    </motion.button>
  );
}
```

### 10.5 Banking — RiskBadge

Displays risk score from transfer detail (maps to backend §9.1):

```typescript
export function RiskBadge({ score, level }: { score: number; level: RiskLevel }) {
  const config = {
    LOW:      { color: 'text-success bg-success-muted border-success/20', label: 'Low Risk' },
    MEDIUM:   { color: 'text-warning bg-warning-muted border-warning/20', label: 'Medium Risk' },
    HIGH:     { color: 'text-danger  bg-danger-muted  border-danger/20',  label: 'High Risk' },
    CRITICAL: { color: 'text-danger  bg-danger-muted  border-danger/30 animate-pulse', label: 'Critical' },
  }[level];

  return (
    <Tooltip content={`Risk score: ${score}/1000`}>
      <span className={cn('inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-body-sm font-medium', config.color)}>
        <ShieldIcon size={12} />
        {config.label}
      </span>
    </Tooltip>
  );
}
```

### 10.6 Skeleton Loaders

Never use spinners for content — use skeletons that match the content shape:

```typescript
// components/feedback/Skeletons.tsx
function Skeleton({ className }: { className?: string }) {
  return (
    <div className={cn(
      'rounded bg-bg-subtle',
      'bg-gradient-to-r from-bg-subtle via-bg-elevated to-bg-subtle',
      'bg-[length:200%_100%] animate-shimmer',
      className
    )} />
  );
}

export function AccountCardSkeleton() {
  return (
    <div className="rounded-xl p-6 bg-bg-surface border border-border">
      <Skeleton className="h-4 w-20 mb-4" />
      <Skeleton className="h-4 w-36 mb-6" />
      <Skeleton className="h-8 w-48 mb-4" />
      <Skeleton className="h-4 w-full" />
    </div>
  );
}

export function TransactionRowSkeleton() {
  return (
    <div className="flex items-center gap-4 p-4">
      <Skeleton className="w-10 h-10 rounded-full flex-shrink-0" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-3 w-24" />
      </div>
      <div className="space-y-2 text-right">
        <Skeleton className="h-4 w-20 ml-auto" />
        <Skeleton className="h-3 w-16 ml-auto" />
      </div>
    </div>
  );
}
```

---

## 11. Form Architecture & Validation

### 11.1 Zod Schemas (Transfer)

Schemas mirror backend validation (backend spec §8.2 + §10.2):

```typescript
// features/transfers/validation.ts
import { z } from 'zod';

const MoneySchema = z.object({
  value:    z.string().regex(/^\d+(\.\d{1,2})?$/, 'Invalid amount format'),
  currency: z.string().length(3),
});

const InternalDestinationSchema = z.object({
  type:      z.literal('INTERNAL'),
  accountId: z.string().min(1, 'Account required'),
});

const SepaDestinationSchema = z.object({
  type: z.literal('SEPA'),
  iban: z.string()
    .regex(/^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,}$/, 'Invalid IBAN')
    .transform(s => s.replace(/\s/g, '')),
  name: z.string().min(2).max(200),
});

const SwiftDestinationSchema = z.object({
  type:          z.literal('SWIFT'),
  swiftCode:     z.string().regex(/^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$/, 'Invalid SWIFT code'),
  accountNumber: z.string().min(1),
  bankCode:      z.string().optional(),
  name:          z.string().min(2).max(200),
});

export const InitiateTransferSchema = z.object({
  sourceAccountId: z.string().min(1),
  destination: z.discriminatedUnion('type', [
    InternalDestinationSchema,
    SepaDestinationSchema,
    SwiftDestinationSchema,
  ]),
  amount: z.object({
    value: z.string()
      .regex(/^\d+(\.\d{1,2})?$/, 'Enter a valid amount')
      .refine(v => parseFloat(v) > 0, 'Amount must be greater than zero')
      .refine(v => parseFloat(v) <= 250_000, 'Amount exceeds single transfer limit'),
    currency: z.string().length(3),
  }),
  remittanceInfo: z.string().max(500).optional(),
});

export type InitiateTransferFormData = z.infer<typeof InitiateTransferSchema>;
```

### 11.2 Amount Input Component

Currency amounts need special treatment — never allow floating point arithmetic in the UI:

```typescript
// components/banking/AmountInput/AmountInput.tsx
export function AmountInput({ currency, availableBalance, onChange, value, error }: AmountInputProps) {
  const [display, setDisplay] = useState('');

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    // Allow only valid decimal input
    const raw = e.target.value.replace(/[^0-9.]/g, '');
    const parts = raw.split('.');
    if (parts.length > 2) return; // reject double decimals
    if (parts[1]?.length > 2)    return; // max 2 decimal places
    setDisplay(raw);
    onChange(raw);
  };

  const isExceedingBalance = availableBalance &&
    parseFloat(value) > parseFloat(availableBalance.value);

  return (
    <div className="relative">
      <div className={cn(
        'flex items-center rounded-xl border bg-bg-elevated px-4 h-20',
        'transition-colors focus-within:border-accent',
        error || isExceedingBalance ? 'border-danger' : 'border-border',
      )}>
        <span className="text-text-tertiary text-heading-lg font-mono mr-3 select-none">
          {currency}
        </span>
        <input
          type="text"
          inputMode="decimal"
          pattern="[0-9]*\.?[0-9]{0,2}"
          placeholder="0.00"
          value={display}
          onChange={handleChange}
          className="flex-1 bg-transparent text-[2.5rem] font-mono font-semibold text-text-primary
                     placeholder:text-text-tertiary focus:outline-none tabular-nums"
        />
      </div>

      {availableBalance && (
        <div className="flex justify-between mt-2 px-1">
          <span className="text-body-sm text-text-tertiary">
            Available: <span className={cn('font-mono', isExceedingBalance && 'text-danger')}>
              {formatCurrency(parseFloat(availableBalance.value), currency)}
            </span>
          </span>
          {isExceedingBalance && (
            <span className="text-body-sm text-danger">Insufficient funds</span>
          )}
        </div>
      )}

      {error && <p className="mt-1.5 text-body-sm text-danger px-1">{error}</p>}
    </div>
  );
}
```

---

## 12. Data Fetching & Caching Strategy

### 12.1 Cache Configuration

```typescript
// app/layout.tsx — QueryClient configuration
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime:            60_000,    // 1 minute default freshness
        gcTime:               5 * 60_000, // 5 minute garbage collection
        retry:                2,
        retryDelay:           (attempt) => Math.min(1000 * 2 ** attempt, 10_000),
        refetchOnWindowFocus: true,
        refetchOnReconnect:   true,
      },
      mutations: {
        retry: 0,  // Never auto-retry mutations (idempotency managed explicitly)
      },
    },
  });
}
```

### 12.2 Stale Time Strategy

| Query | staleTime | Rationale |
|---|---|---|
| Account list | 2 min | Changes infrequently |
| Account balance | 5s | Matches backend Redis TTL |
| Transaction history | 30s | Append-only, low change rate |
| Transfer detail (active) | 0 | Must reflect saga state in real-time |
| Transfer detail (terminal) | `Infinity` | Immutable once completed |
| Transfer limits | 30s | Dynamic but not critical to be instant |
| Customer profile | 5 min | Rarely changes |

### 12.3 Optimistic Updates

For perceived speed on transfers:

```typescript
// In useInitiateTransfer mutation
onMutate: async (request) => {
  // Cancel in-flight balance queries
  await queryClient.cancelQueries({ queryKey: accountKeys.balance(request.sourceAccountId) });

  // Snapshot previous state
  const previous = queryClient.getQueryData(accountKeys.balance(request.sourceAccountId));

  // Optimistically deduct amount
  queryClient.setQueryData(accountKeys.balance(request.sourceAccountId), (old: BalanceResponse) => ({
    ...old,
    balance: {
      ...old.balance,
      available: {
        ...old.balance.available,
        value: (parseFloat(old.balance.available.value) - parseFloat(request.amount.value)).toFixed(2),
      },
    },
  }));

  return { previous };
},
onError: (_err, request, context) => {
  // Roll back optimistic update
  queryClient.setQueryData(accountKeys.balance(request.sourceAccountId), context?.previous);
},
onSettled: (_data, _err, request) => {
  queryClient.invalidateQueries({ queryKey: accountKeys.balance(request.sourceAccountId) });
},
```

---

## 13. Error Handling & Resilience UI

### 13.1 Error Boundary

```typescript
// components/feedback/ErrorBoundary.tsx
'use client';
import { useEffect } from 'react';

export default function GlobalError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => {
    // Send to error tracking (Sentry, etc.)
    console.error('[GlobalError]', error);
  }, [error]);

  return (
    <div className="min-h-screen bg-bg-base flex items-center justify-center p-8">
      <div className="max-w-md w-full text-center space-y-6">
        <div className="w-16 h-16 rounded-full bg-danger-muted border border-danger/20 flex items-center justify-center mx-auto">
          <AlertTriangle className="text-danger" size={28} />
        </div>
        <h1 className="text-heading-xl text-text-primary">Something went wrong</h1>
        <p className="text-body-md text-text-secondary">
          We've been notified and are looking into it. Your funds are safe.
        </p>
        <Button onClick={reset} variant="secondary">Try again</Button>
      </div>
    </div>
  );
}
```

### 13.2 API Error → UI Message Mapping

```typescript
// lib/api/errorMessages.ts
export function getTransferErrorMessage(error: BankingApiError): {
  title: string;
  description: string;
  action?: string;
} {
  if (error.isInsufficientFunds) return {
    title:       'Insufficient funds',
    description: error.problem.detail,
  };
  if (error.isAccountFrozen) return {
    title:       'Account restricted',
    description: 'This account is currently frozen. Contact support to resolve.',
    action:      'Contact support',
  };
  if (error.isTransferBlocked) return {
    title:       'Transfer blocked',
    description: 'This transfer was flagged by our security system.',
    action:      'View case details',
  };
  if (error.isRateLimited) return {
    title:       'Too many requests',
    description: 'You've reached the transfer limit. Please wait before trying again.',
  };
  if (error.httpStatus >= 500) return {
    title:       'Service unavailable',
    description: 'Our payment system is temporarily unavailable. Your funds have not been moved.',
    action:      'Check status page',
  };
  return {
    title:       'Transfer failed',
    description: error.problem.detail ?? 'An unexpected error occurred.',
  };
}
```

### 13.3 Toast Notification Integration

```typescript
// Using Sonner for rich notifications
import { toast } from 'sonner';

// On TRANSFER_COMPLETED WebSocket event:
toast.success('Transfer complete', {
  description: `${formatCurrency(amount, currency)} sent to ${recipientName}`,
  duration: 5000,
  action: { label: 'View', onClick: () => router.push(`/transfers/${transferId}`) },
});

// On ACCOUNT_FROZEN WebSocket event:
toast.warning('Account restricted', {
  description: 'Your account has been temporarily frozen. Contact support.',
  duration: 0, // persist until dismissed
  important: true,
});
```

---

## 14. Accessibility & Internationalisation

### 14.1 Accessibility Requirements

- **WCAG 2.1 AA** compliance minimum
- All interactive elements have `aria-label` or visible text
- Focus management in wizard steps — first focusable element on step change
- Screen reader announcements for real-time balance updates via `aria-live`
- Color is never the sole indicator of meaning (always paired with icon/text)
- Minimum 4.5:1 contrast ratio for all text
- Keyboard-navigable transfer wizard

```typescript
// Balance with screen reader announcement
export function LiveBalanceRegion({ balance, currency }: LiveBalanceProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-atomic="true"
      aria-label={`Current balance: ${formatCurrency(balance, currency)}`}
    >
      <BalanceDisplay amount={balance} currency={currency} animate />
    </div>
  );
}
```

### 14.2 Internationalisation

```typescript
// lib/formatting/currency.ts
export function formatCurrency(
  amount: number,
  currency: string,
  locale?: string,
): string {
  const userLocale = locale ?? navigator.language ?? 'en-GB';
  return new Intl.NumberFormat(userLocale, {
    style:                 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

// lib/formatting/iban.ts
export function maskIban(iban: string): string {
  // DE89 **** **** **** 3000
  const clean = iban.replace(/\s/g, '');
  const country = clean.slice(0, 4);
  const last4   = clean.slice(-4);
  return `${country} **** **** **** ${last4}`;
}

export function formatIban(iban: string): string {
  // Group in blocks of 4
  return iban.replace(/\s/g, '').match(/.{1,4}/g)?.join(' ') ?? iban;
}
```

---

## 15. Performance Architecture

### 15.1 Core Web Vitals Targets

| Metric | Target |
|---|---|
| LCP (Largest Contentful Paint) | < 1.5s |
| FID / INP (Interaction to Next Paint) | < 100ms |
| CLS (Cumulative Layout Shift) | < 0.05 |
| TTFB (Time to First Byte) | < 200ms |

### 15.2 Next.js Rendering Strategy

| Page | Strategy | Rationale |
|---|---|---|
| `/login` | Static | No user data |
| `/dashboard` | PPR (Partial Pre-rendering) | Shell static, data streamed |
| `/accounts/[id]` | PPR + Suspense | Account meta static, balance dynamic |
| `/transfers/new` | Client-side | Full interactivity required |
| `/transfers/[id]` | ISR (10s revalidate) | Until COMPLETED, then static |

```typescript
// Parallel data fetching with Suspense boundaries
export default async function AccountDetailPage({ params }: Props) {
  return (
    <div>
      {/* Metadata never changes — render immediately */}
      <AccountHeader accountId={params.accountId} />

      {/* Balance can stream in */}
      <Suspense fallback={<BalanceSkeleton size="xl" />}>
        <AccountBalanceServer accountId={params.accountId} />
      </Suspense>

      {/* Transactions are heavy — load after */}
      <Suspense fallback={<TransactionListSkeleton count={10} />}>
        <TransactionListServer accountId={params.accountId} />
      </Suspense>
    </div>
  );
}
```

### 15.3 Bundle Optimization

```typescript
// Dynamic imports for heavy components
const TransferWizard  = dynamic(() => import('@/features/transfers/transfer-wizard'), { loading: () => <WizardSkeleton /> });
const StatementViewer = dynamic(() => import('@/components/banking/StatementViewer'),  { ssr: false });
const SpendingChart   = dynamic(() => import('@/components/banking/SpendingChart'),    { ssr: false });
```

### 15.4 Virtualized Transaction List

For accounts with thousands of transactions:

```typescript
import { Virtuoso } from 'react-virtuoso';

export function TransactionList({ accountId }: Props) {
  const { data, fetchNextPage, hasNextPage } = useTransactionHistory(accountId, filters);
  const allTransactions = data?.pages.flatMap(p => p.content) ?? [];

  return (
    <Virtuoso
      style={{ height: '600px' }}
      data={allTransactions}
      endReached={() => hasNextPage && fetchNextPage()}
      itemContent={(_, transaction) => (
        <TransactionRow
          key={transaction.entryId}
          transaction={transaction}
          onSelect={setSelectedTransaction}
        />
      )}
      components={{
        Footer: () => hasNextPage ? <TransactionRowSkeleton /> : null,
        EmptyPlaceholder: () => <EmptyTransactions />,
      }}
    />
  );
}
```

---

## 16. Testing Strategy

### 16.1 Test Pyramid

```
                  ┌───────────────────┐
                  │  E2E (Playwright)  │  10% — full user journeys
                  ├───────────────────┤
                  │ Integration Tests  │  25% — components + MSW mocks
                  ├───────────────────┤
                  │   Unit Tests       │  65% — utils, hooks, stores, validators
                  └───────────────────┘
```

### 16.2 MSW Mock Handlers

Mock Service Worker intercepts API calls in tests and Storybook:

```typescript
// mocks/handlers/transfers.ts
import { http, HttpResponse } from 'msw';

export const transferHandlers = [
  http.post('/api/v1/transfers', async ({ request }) => {
    const body = await request.json() as any;
    return HttpResponse.json({
      transferId:            'TRF-TEST-001',
      status:                'INITIATED',
      estimatedCompletionAt: new Date(Date.now() + 5000).toISOString(),
      links: {
        self:   `/api/v1/transfers/TRF-TEST-001`,
        status: `/api/v1/transfers/TRF-TEST-001/status`,
      },
    }, { status: 202 });
  }),

  http.get('/api/v1/transfers/:transferId', ({ params }) => {
    return HttpResponse.json({
      transferId: params.transferId,
      status:     'COMPLETED',
      // ... full fixture
    });
  }),

  // Error scenarios
  http.post('/api/v1/transfers/insufficient', () => {
    return HttpResponse.json({
      type:      'https://api.bank.com/errors/insufficient-funds',
      title:     'Insufficient Funds',
      status:    422,
      detail:    'Available balance EUR 100.00 is less than EUR 250.00',
      timestamp: new Date().toISOString(),
    }, { status: 422 });
  }),
];
```

### 16.3 Component Tests

```typescript
// features/transfers/transfer-wizard/__tests__/Step2Amount.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Step2Amount } from '../Step2Amount';
import { withQueryClient, withTransferStore } from '@/test-utils';

describe('Step2Amount', () => {
  it('shows insufficient funds warning when amount exceeds available balance', async () => {
    const user = userEvent.setup();
    render(withQueryClient(withTransferStore(<Step2Amount />)));

    const input = screen.getByRole('textbox', { name: /amount/i });
    await user.type(input, '9999.00');

    await waitFor(() => {
      expect(screen.getByText(/insufficient funds/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /continue/i })).not.toBeDisabled();
    // Note: we allow submission — server validates; client shows warning only
  });

  it('formats IBAN correctly in destination preview', async () => {
    // ...
  });
});
```

### 16.4 E2E Tests (Playwright)

```typescript
// e2e/transfer.spec.ts
import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures/auth';

test.describe('Transfer Flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'test-customer-premium');
  });

  test('completes internal transfer end-to-end', async ({ page }) => {
    await page.goto('/transfers/new');

    // Step 1: Select source account
    await page.click('[data-testid="account-card-ACC-000001"]');
    await page.click('[data-testid="internal-destination-ACC-000002"]');

    // Step 2: Enter amount
    await page.fill('[data-testid="amount-input"]', '50.00');
    await expect(page.locator('[data-testid="available-balance"]')).toContainText('EUR');
    await page.click('button:has-text("Continue")');

    // Step 3: Confirm
    await expect(page.locator('[data-testid="transfer-summary-amount"]')).toContainText('50.00');
    await page.fill('[data-testid="remittance-input"]', 'Test transfer');
    await page.click('button:has-text("Send Transfer")');

    // Step 4: Status
    await expect(page.locator('[data-testid="transfer-status"]')).toHaveText('INITIATED', { timeout: 2000 });
    await expect(page.locator('[data-testid="transfer-status"]')).toHaveText('COMPLETED', { timeout: 15000 });
  });

  test('shows blocked message for risk-flagged transfer', async ({ page }) => {
    // Uses MSW to return TRANSFER_BLOCKED error
    await page.goto('/transfers/new?mock=blocked');
    // ... verify error UI
  });
});
```

### 16.5 Test Coverage Requirements

| Area | Minimum Coverage | Test Type |
|---|---|---|
| Zod validation schemas | 100% of rules + edge cases | Unit |
| `BankingApiError` classification | 100% of error types | Unit |
| Currency formatting | All currencies + edge cases | Unit |
| IBAN masking / formatting | Valid + invalid inputs | Unit |
| Transfer wizard store | All state transitions | Unit |
| `useRealTimeBalance` hook | WS update + query sync | Integration |
| Transfer wizard steps | Happy path + each error | Integration |
| API client interceptors | Auth injection, 401 redirect | Integration |
| Dashboard page | Renders with MSW data | Integration |
| Complete transfer flow | End-to-end | E2E |
| Failed transfer flow | Error states | E2E |

---

## 17. Infrastructure & Deployment

### 17.1 Environment Variables

```bash
# .env.local.example

# Backend API (from retail-banking-platform-spec.md)
NEXT_PUBLIC_API_URL=https://api.bank.com
NEXT_PUBLIC_WS_URL=wss://api.bank.com/api/v1/ws/notifications
NEXT_PUBLIC_WS_HOST=api.bank.com

# Auth.js + Keycloak (backend spec §10.1)
AUTH_SECRET=generate-with-openssl-rand-hex-32
AUTH_URL=https://app.bank.com
KEYCLOAK_CLIENT_ID=retail-web
KEYCLOAK_CLIENT_SECRET=<from-vault>
KEYCLOAK_ISSUER=https://auth.bank.com/realms/retail

# Feature flags
NEXT_PUBLIC_FEATURE_SWIFT_TRANSFERS=false
NEXT_PUBLIC_FEATURE_FX_PREVIEW=true

# Observability
NEXT_PUBLIC_SENTRY_DSN=https://...
SENTRY_AUTH_TOKEN=<from-vault>
```

### 17.2 Dockerfile

```dockerfile
FROM node:22-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --frozen-lockfile

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

RUN addgroup --system --gid 1001 nodejs
RUN adduser  --system --uid  1001 nextjs
COPY --from=builder /app/public            ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static     ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
```

### 17.3 Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: banking-frontend
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
      - name: banking-frontend
        image: bank/banking-frontend:1.0.0
        ports:
        - containerPort: 3000
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        env:
        - name: NEXT_PUBLIC_API_URL
          valueFrom:
            configMapKeyRef:
              name: frontend-config
              key: api_url
        - name: AUTH_SECRET
          valueFrom:
            secretKeyRef:
              name: frontend-secrets
              key: auth_secret
        readinessProbe:
          httpGet:
            path: /api/health
            port: 3000
          initialDelaySeconds: 10
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /api/health
            port: 3000
          initialDelaySeconds: 30
          periodSeconds: 15
```

### 17.4 CI/CD Pipeline

```yaml
# .github/workflows/frontend-ci.yml
name: Frontend CI
on: [push, pull_request]
jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-node@v4
      with: { node-version: '22', cache: 'npm' }
    - run: npm ci
    - run: npm run type-check
    - run: npm run lint
    - run: npm run test:unit -- --coverage
    - run: npm run build

  e2e:
    runs-on: ubuntu-latest
    needs: quality
    steps:
    - uses: actions/checkout@v4
    - run: npm ci
    - run: npx playwright install --with-deps chromium
    - run: npm run test:e2e
    - uses: actions/upload-artifact@v4
      if: failure()
      with:
        name: playwright-report
        path: playwright-report/
```

---

## 18. LLM Implementation Instructions

### 18.1 Backend API Contract Alignment

This frontend spec is tightly coupled to `retail-banking-platform-spec.md`. The following mappings must be preserved exactly:

| Frontend Concern | Backend Spec Section | Key Contract |
|---|---|---|
| JWT claims parsing | §10.1 | `customerId`, `accountIds`, `tier`, `kycLevel` claims |
| Transfer initiation | §11.2 | `X-Idempotency-Key` header, 202 response, `transferId` in response |
| Transfer status events | §11.2 | `events[]` array with `status` + `at` fields |
| Balance response shape | §11.3 | `balance.current`, `.available`, `.reserved` objects |
| WebSocket events | §11.4 | `type` + `data` envelope, all 4 event types |
| Error response shape | §11.5 | RFC 7807 `type`, `title`, `status`, `detail`, `traceId` |
| Transfer saga states | §7.3 | 9 states: INITIATED → ... → COMPLETED / FAILED / REVERSED |
| Risk levels | §9.1 | `LOW / MEDIUM / HIGH / CRITICAL`, scores 0–1000 |

### 18.2 File Creation Order

Implement in this order to avoid import resolution issues:

```
1.  package.json + tsconfig.json + tailwind.config.ts + next.config.ts
2.  app/globals.css (design tokens)
3.  lib/utils.ts (cn helper)
4.  lib/formatting/ (currency, iban, date)
5.  lib/api/errors.ts (BankingApiError)
6.  lib/api/client.ts (base Axios client)
7.  lib/auth/config.ts (Auth.js setup)
8.  types/ (domain.ts, api.ts, next-auth.d.ts)
9.  middleware.ts (route protection)
10. stores/ (notifications, ui — no API dependencies)
11. components/ui/ (Button, Input, Badge, Skeleton — no feature dependencies)
12. features/accounts/types.ts + api.ts + store.ts
13. features/transfers/types.ts + validation.ts + api.ts + store.ts
14. features/notifications/websocket.ts + store.ts
15. components/banking/ (AccountCard, BalanceDisplay, etc.)
16. app/(banking)/layout.tsx (WS connection + query provider)
17. app/(banking)/dashboard/page.tsx
18. app/(banking)/accounts/[accountId]/page.tsx
19. features/transfers/transfer-wizard/ (steps 1–4)
20. app/(banking)/transfers/new/page.tsx
21. Remaining pages (transfer detail, statements, notifications)
22. Tests (unit → integration → E2E last)
```

### 18.3 Critical Implementation Details

- **Money arithmetic**: Never use JavaScript `number` for currency calculations in the UI. Accept `string` from backend (`"250.00"`), display with `Intl.NumberFormat`, compare with `parseFloat` only for display logic.
- **Idempotency key lifecycle**: Generate one UUID per wizard session in the Zustand store. Regenerate on `reset()`. Never regenerate on retry — same key = safe retry.
- **Balance animation**: Use the `useCountUp` hook on every balance update from WS. Duration 600ms, ease-out. This makes real-time updates feel premium.
- **IBAN handling**: Store IBANs as received (stripped of spaces), display with `formatIban()` (grouped in 4s), mask with `maskIban()` by default with click-to-reveal.
- **Transfer status polling**: `useTransferStatus` polls every 3s only when transfer is non-terminal. The WS is the real-time channel; polling is the fallback. Both update the same TanStack Query cache entry.
- **Route protection**: The middleware (§4.3) enforces account ownership at the URL level. The application layer (API hooks) also enforces it via backend 403s. Defense in depth.
- **Frozen account UI**: Every account card and the transfer source selector must check `account.status === 'FROZEN'` and visually disable + show reason. Never just grey out silently.
- **Correlation ID**: The API client interceptor (§5.1) adds `X-Correlation-ID` to every request. This links frontend requests to backend traces (backend spec §15.3).

### 18.4 Anti-Patterns to Avoid

- ❌ Never store `accessToken` in `localStorage` or `sessionStorage` — Auth.js manages HTTP-only cookies
- ❌ Never call backend endpoints directly from React Server Components — use the server-side API client with the session token
- ❌ Never use `parseInt` or `parseFloat` for currency arithmetic — display only
- ❌ Never render raw `error.message` from caught exceptions — always map through `getTransferErrorMessage()` or equivalent
- ❌ Never poll a terminal transfer status — check `isTerminal` and return `false` from `refetchInterval`
- ❌ Never show a spinner for initial page data — use skeletons that match content shape
- ❌ Never `dangerouslySetInnerHTML` with any data that originated from user input or backend string fields

### 18.5 Storybook Stories Requirements

Every component in `components/ui/` and `components/banking/` must have a Storybook story with:

- Default state
- All variants (for primitives)
- Loading / skeleton state
- Error / disabled state
- With real-time data (use MSW in Storybook for WS simulation)

```typescript
// components/banking/AccountCard/AccountCard.stories.tsx
import type { Meta, StoryObj } from '@storybook/nextjs';
import { AccountCard } from './AccountCard';
import { mockAccount, mockFrozenAccount } from '@/mocks/fixtures';

const meta: Meta<typeof AccountCard> = {
  component: AccountCard,
  parameters: { msw: { handlers: [accountHandlers.balance] } },
};
export default meta;

export const Active:  StoryObj = { args: { account: mockAccount } };
export const Frozen:  StoryObj = { args: { account: mockFrozenAccount } };
export const Premium: StoryObj = { args: { account: { ...mockAccount, tier: 'PREMIUM' } } };
export const Loading: StoryObj = { parameters: { msw: { handlers: [accountHandlers.balanceLoading] } } };
```

### 18.6 Performance Checklist (Before Ship)

- [ ] Lighthouse score ≥ 90 on all 4 categories in production build
- [ ] No layout shift on balance update (reserved space for amount)
- [ ] All images use `next/image` with explicit `width` + `height`
- [ ] No `useEffect` with missing dependencies
- [ ] React Compiler auto-memo is verified active (`react.compiler.ReactCompilerConfig`)
- [ ] Bundle analyzer run — no duplicate packages, no accidental server-only imports
- [ ] Virtual scrolling active on transaction list (verify with 500+ transactions)
- [ ] WebSocket reconnects correctly after 30s browser idle
- [ ] Transfer wizard does not re-render parent on each keystroke (check with React DevTools profiler)

---

*Frontend Specification Version: 1.0 — Generated for LLM-assisted implementation*  
*Pairs with: `retail-banking-platform-spec.md` (Java backend)*  
*Stack: Next.js 15 · TypeScript 5 · TailwindCSS 4 · TanStack Query v5 · Zustand · Auth.js v5*
