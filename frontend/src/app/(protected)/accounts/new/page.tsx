"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { accountsApi } from "@/api/accounts";
import { useAuth } from "@/components/AuthProvider";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { ArrowLeft, CheckCircle2 } from "lucide-react";
import Link from "next/link";

export default function OpenAccountPage() {
  const router = useRouter();
  const { userProfile } = useAuth();
  const queryClient = useQueryClient();
  const customerId = userProfile?.id || userProfile?.sub;
  
  const [type, setType] = useState<"CHECKING" | "SAVINGS">("CHECKING");
  const [currency, setCurrency] = useState("USD");
  const [productCode, setProductCode] = useState("STANDARD");
  const [isSuccess, setIsSuccess] = useState(false);
  const [newAccountId, setNewAccountId] = useState("");

  const mutation = useMutation({
    mutationFn: accountsApi.openAccount,
    onSuccess: (data) => {
      setIsSuccess(true);
      setNewAccountId(data.accountId);
      queryClient.invalidateQueries({ queryKey: ["accounts", customerId] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerId) return;
    mutation.mutate({ customerId, type, currency, productCode });
  };

  if (isSuccess) {
    return (
      <div className="max-w-2xl mx-auto mt-10">
        <Card className="border-[var(--success)]/20 glass text-center py-10">
          <CardContent className="flex flex-col items-center">
            <CheckCircle2 className="h-20 w-20 text-[var(--success)] mb-6 animate-pulse" />
            <h2 className="text-3xl font-bold mb-4">Account Opened Successfully!</h2>
            <p className="text-[var(--foreground)] opacity-70 mb-8 max-w-md">
              Your new {type.toLowerCase()} account has been created and is ready to use. 
              Account ID: <span className="font-mono text-[var(--primary)]">{newAccountId}</span>
            </p>
            <div className="flex gap-4">
              <Button onClick={() => router.push("/dashboard")}>Go to Dashboard</Button>
              <Button variant="outline" onClick={() => router.push(`/accounts/${newAccountId}`)}>View Account Details</Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboard" className="p-2 rounded-full hover:bg-[var(--secondary)] transition-colors">
          <ArrowLeft size={24} />
        </Link>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Open New Account</h1>
          <p className="text-[var(--foreground)] opacity-70 mt-1">Select the type of account you'd like to open.</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Account Details</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1.5">Account Type</label>
                <div className="grid grid-cols-2 gap-4">
                  <button
                    type="button"
                    onClick={() => setType("CHECKING")}
                    className={`flex flex-col items-center justify-center p-4 rounded-xl border-2 transition-all ${
                      type === "CHECKING" 
                        ? "border-[var(--primary)] bg-[var(--primary)]/5" 
                        : "border-[var(--border)] hover:border-[var(--primary)]/50 bg-[var(--card)]"
                    }`}
                  >
                    <span className="font-semibold text-lg">Checking</span>
                    <span className="text-sm opacity-70 text-center mt-1">For your everyday spending</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setType("SAVINGS")}
                    className={`flex flex-col items-center justify-center p-4 rounded-xl border-2 transition-all ${
                      type === "SAVINGS" 
                        ? "border-[var(--primary)] bg-[var(--primary)]/5" 
                        : "border-[var(--border)] hover:border-[var(--primary)]/50 bg-[var(--card)]"
                    }`}
                  >
                    <span className="font-semibold text-lg">Savings</span>
                    <span className="text-sm opacity-70 text-center mt-1">Earn interest on your wealth</span>
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1.5" htmlFor="currency">Currency</label>
                <select 
                  id="currency"
                  className="flex h-10 w-full rounded-md border border-[var(--border)] bg-transparent px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--primary)]"
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                >
                  <option value="USD">USD - US Dollar</option>
                  <option value="EUR">EUR - Euro</option>
                  <option value="GBP">GBP - British Pound</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1.5" htmlFor="productCode">Product Package</label>
                <select 
                  id="productCode"
                  className="flex h-10 w-full rounded-md border border-[var(--border)] bg-transparent px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--primary)]"
                  value={productCode}
                  onChange={(e) => setProductCode(e.target.value)}
                >
                  <option value="STANDARD">Standard Banking</option>
                  <option value="PREMIUM">Premium Benefits</option>
                  <option value="STUDENT">Student Zero-fee</option>
                </select>
              </div>
            </div>

            {mutation.isError && (
              <div className="p-3 text-sm rounded-md bg-[var(--danger)]/10 text-[var(--danger)] border border-[var(--danger)]/20">
                Failed to open account. Please try again.
              </div>
            )}

            <div className="flex justify-end pt-4 border-t border-[var(--border)]">
              <Button type="submit" size="lg" isLoading={mutation.isPending}>
                Create Account
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
