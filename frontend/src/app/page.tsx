"use client";
import Image from "next/image";

import { useAuth } from "@/components/AuthProvider";
import { Button } from "@/components/ui/Button";
import { ArrowRight, ShieldCheck, CreditCard, Send } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function LandingPage() {
  const { isAuthenticated, login } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated) {
      router.push('/dashboard');
    }
  }, [isAuthenticated, router]);

  return (
    <div className="min-h-screen bg-[var(--background)] flex flex-col justify-center relative overflow-hidden isolate">
      {/* Background decoration */}
      <div className="absolute inset-x-0 -top-40 -z-10 transform-gpu overflow-hidden blur-3xl sm:-top-80">
        <div className="relative left-[calc(50%-11rem)] aspect-[1155/678] w-[36.125rem] -translate-x-1/2 rotate-[30deg] bg-gradient-to-tr from-[#ff80b5] to-[var(--primary)] opacity-20 sm:left-[calc(50%-30rem)] sm:w-[72.1875rem]"></div>
      </div>

      <main className="max-w-7xl mx-auto px-6 lg:px-8 py-24 sm:py-32 flex flex-col items-center text-center animate-fade-in">
        <div className="mb-8 flex justify-center w-20 h-20 rounded-2xl bg-[var(--primary)]/10 text-[var(--primary)] items-center shadow-lg border border-[var(--primary)]/20">
          <ShieldCheck size={40} />
        </div>

        <h1 className="text-4xl font-bold tracking-tight text-[var(--foreground)] sm:text-6xl">
          Secure Retail Banking
        </h1>
        <p className="mt-6 text-lg leading-8 text-[var(--foreground)] opacity-70 max-w-2xl">
          Experience next-generation banking. Open secure checking and savings accounts, execute seamless internal transfers, and manage your wealth from a premium dashboard.
        </p>

        <div className="mt-10 flex items-center justify-center gap-x-6">
          <Button onClick={login} size="lg" className="rounded-full px-8 text-lg font-semibold shadow-xl shadow-[var(--primary)]/20">
            Log In via Keycloak <ArrowRight className="ml-2 h-5 w-5" />
          </Button>
        </div>

        <div className="mt-24 grid grid-cols-1 gap-y-8 sm:grid-cols-2 lg:grid-cols-3 gap-x-8 max-w-4xl mx-auto">
          <div className="flex flex-col items-center p-6 bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-sm glass">
            <CreditCard className="h-10 w-10 text-[var(--primary)] mb-4" />
            <h3 className="text-lg font-medium">Multiple Accounts</h3>
            <p className="text-sm opacity-70 text-center mt-2">Open checking or savings accounts instantly.</p>
          </div>
          <div className="flex flex-col items-center p-6 bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-sm glass">
            <Send className="h-10 w-10 text-[var(--primary)] mb-4" />
            <h3 className="text-lg font-medium">Instant Transfers</h3>
            <p className="text-sm opacity-70 text-center mt-2">Move money between accounts securely in real-time.</p>
          </div>
          <div className="flex flex-col items-center p-6 bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-sm glass sm:col-span-2 lg:col-span-1">
            <ShieldCheck className="h-10 w-10 text-[var(--primary)] mb-4" />
            <h3 className="text-lg font-medium">Bank-grade Security</h3>
            <p className="text-sm opacity-70 text-center mt-2">Powered by Event Sourcing and Keycloak Auth.</p>
          </div>
        </div>
      </main>
    </div>
  );
}
