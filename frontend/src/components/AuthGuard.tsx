"use client";

import { useAuth } from "@/components/AuthProvider";
import { useEffect } from "react";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isInitialized, login } = useAuth();

  useEffect(() => {
    if (isInitialized && !isAuthenticated) {
      login();
    }
  }, [isInitialized, isAuthenticated, login]);

  if (!isInitialized) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[var(--background)] text-[var(--foreground)]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-4 border-[var(--primary)] border-t-transparent rounded-full animate-spin"></div>
          <p className="font-medium animate-pulse">Initializing security context...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) return null; // Will redirect via useEffect

  return <>{children}</>;
}
