import Keycloak from 'keycloak-js';

// Configuration for Keycloak
const keycloakConfig = {
  url: process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8080',
  realm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM || 'retail',
  clientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'retail-app',
};

// Initialize Keycloak instance
// Check if window is defined to avoid SSR issues
const keycloak = typeof window !== 'undefined' ? new Keycloak(keycloakConfig) : null;

export default keycloak;
