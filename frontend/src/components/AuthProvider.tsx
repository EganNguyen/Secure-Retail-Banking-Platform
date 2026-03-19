"use client";

import React, { createContext, useContext, useEffect, useState } from 'react';
import keycloak from '@/lib/keycloak';

interface AuthContextType {
  isAuthenticated: boolean;
  isInitialized: boolean;
  token: string | null;
  login: () => void;
  logout: () => void;
  userProfile: any | null;
}

const AuthContext = createContext<AuthContextType>({
  isAuthenticated: false,
  isInitialized: false,
  token: null,
  login: () => {},
  logout: () => {},
  userProfile: null,
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [isInitialized, setIsInitialized] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [userProfile, setUserProfile] = useState<any | null>(null);

  useEffect(() => {
    if (!keycloak) return;

    keycloak
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        pkceMethod: 'S256',
      })
      .then((authenticated) => {
        setIsAuthenticated(authenticated);
        setIsInitialized(true);
        setToken(keycloak!.token || null);
        
        if (authenticated) {
          keycloak!.loadUserProfile().then((profile) => setUserProfile(profile));
        }

        // Token refresh mechanism
        keycloak!.onTokenExpired = () => {
          keycloak!.updateToken(30).then((refreshed) => {
            if (refreshed) {
              setToken(keycloak!.token || null);
            }
          }).catch(() => {
            // Failed to refresh token, force logout
            keycloak!.logout();
          });
        };
      })
      .catch(console.error);
  }, []);

  const login = () => keycloak?.login();
  const logout = () => keycloak?.logout({ redirectUri: window.location.origin });

  return (
    <AuthContext.Provider value={{ isAuthenticated, isInitialized, token, login, logout, userProfile }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
