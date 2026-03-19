"use client";

import { useEffect } from "react";
import { Button } from "@/components/ui/Button";
import { AlertTriangle } from "lucide-react";
import Link from "next/link";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Global Error Boundary caught:", error);
  }, [error]);

  return (
    <div className="min-h-screen bg-[var(--background)] flex items-center justify-center p-6">
      <div className="w-full max-w-md bg-[var(--card)] rounded-xl border border-[var(--danger)]/30 shadow-lg p-8 text-center flex flex-col gap-4 items-center glass">
        <div className="w-16 h-16 bg-[var(--danger)]/10 rounded-full flex items-center justify-center text-[var(--danger)] mb-2">
          <AlertTriangle size={32} />
        </div>
        <h2 className="text-2xl font-bold">Something went wrong</h2>
        <p className="text-[var(--foreground)] opacity-70 mb-6 font-medium">
          We experienced an unexpected error. Our systems have been notified.
        </p>
        <div className="flex w-full gap-4">
          <Button variant="outline" className="flex-1" onClick={() => reset()}>
            Try again
          </Button>
          <Link href="/" className="flex-1">
            <Button className="w-full">Go Home</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
