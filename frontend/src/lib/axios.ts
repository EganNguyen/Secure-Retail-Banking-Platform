import axios from 'axios';
import keycloak from './keycloak';

// Create a custom axios instance
const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add Keycloak token & Correlation ID
api.interceptors.request.use(
  async (config) => {
    if (keycloak && keycloak.authenticated) {
      // Ensure token is somewhat fresh before sending
      try {
        await keycloak.updateToken(5);
        if (keycloak.token) {
          config.headers.Authorization = `Bearer ${keycloak.token}`;
        }
      } catch (e) {
        console.error("Failed to refresh token", e);
        keycloak.login();
      }
    }
    
    // Add Correlation ID if missing
    if (!config.headers['X-Correlation-ID']) {
      config.headers['X-Correlation-ID'] = crypto.randomUUID();
    }
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default api;
