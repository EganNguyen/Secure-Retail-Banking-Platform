"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { accountsApi } from "@/api/accounts";
import { transfersApi } from "@/api/transfers";
import { useAuth } from "@/components/AuthProvider";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Send, AlertCircle } from "lucide-react";

export default function TransferPage() {
  const router = useRouter();
  const { userProfile } = useAuth();
  const customerId = userProfile?.id || userProfile?.sub;

  const [sourceAccountId, setSourceAccountId] = useState("");
  const [destinationAccountId, setDestinationAccountId] = useState("");
  const [beneficiaryName, setBeneficiaryName] = useState("");
  const [amount, setAmount] = useState("");
  const [reference, setReference] = useState("");

  const { data: accounts } = useQuery({
    queryKey: ['accounts', customerId],
    queryFn: () => accountsApi.getAccountsByCustomer(customerId!),
    enabled: !!customerId,
  });

  const activeAccounts = accounts?.filter(a => a.status === 'ACTIVE') || [];
  
  const selectedSourceAccount = activeAccounts.find(a => a.accountId === sourceAccountId);

  const { data: balanceData } = useQuery({
    queryKey: ['balance', sourceAccountId],
    queryFn: () => accountsApi.getBalance(sourceAccountId),
    enabled: !!sourceAccountId,
  });

  const transferMutation = useMutation({
    mutationFn: transfersApi.createTransfer,
    onSuccess: (data) => {
      router.push(`/transfers/${data.transferId}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceAccountId || !destinationAccountId || !selectedSourceAccount) return;
    
    // Client-side validation for amounts. 
    // CAUTION: This depends on the 'balance' projection in the read model.
    // In scenarios with rapid updates (e.g., E2E tests), the cached balance might 
    // be stale. The backend saga performs the authoritative validation.
    if (balanceData && parseFloat(amount) > balanceData.availableBalance) {
      alert("Insufficient funds");
      return;
    }

    if (sourceAccountId === destinationAccountId) {
      alert("Source and destination accounts must be different");
      return;
    }

    transferMutation.mutate({
      sourceAccountId,
      destinationAccountId,
      beneficiaryName,
      amount: parseFloat(amount),
      currency: selectedSourceAccount.currency,
      reference,
    });
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight flex items-center gap-3">
          <Send className="text-[var(--primary)]" /> Execute Transfer
        </h1>
        <p className="text-[var(--foreground)] opacity-70 mt-1">Move money securely between internal accounts.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Transfer Details</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            
            <div>
              <label className="block text-sm font-medium mb-1.5" htmlFor="sourceAccount">From Account</label>
              <select 
                id="sourceAccount"
                className="flex h-10 w-full rounded-md border border-[var(--border)] bg-transparent px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--primary)] disabled:opacity-50"
                value={sourceAccountId}
                onChange={(e) => setSourceAccountId(e.target.value)}
                required
              >
                <option value="" disabled>Select a source account</option>
                {activeAccounts.map(account => (
                  <option key={account.accountId} value={account.accountId}>
                    {account.type} - {account.accountId.split('-')[0]} ({account.currency})
                  </option>
                ))}
              </select>
              
              {balanceData && (
                <p className="text-xs mt-2 font-medium opacity-80 flex items-center gap-1">
                  Available Balance: <span className="text-[var(--primary)]">{new Intl.NumberFormat('en-US', { style: 'currency', currency: balanceData.currency }).format(balanceData.availableBalance)}</span>
                </p>
              )}
            </div>

            <div className="pt-4 border-t border-[var(--border)]">
              <Input 
                label="To Account ID (Destination)" 
                placeholder="Enter exact account ID dashboard..." 
                value={destinationAccountId}
                onChange={(e) => setDestinationAccountId(e.target.value)}
                required
              />
            </div>

            <Input 
              label="Beneficiary Name" 
              placeholder="e.g. John Doe or Jane Smith" 
              value={beneficiaryName}
              onChange={(e) => setBeneficiaryName(e.target.value)}
              required
            />

            <div className="grid grid-cols-2 gap-4">
              <Input 
                type="number"
                step="0.01"
                min="0.01"
                label="Amount" 
                placeholder="0.00" 
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
              <div>
                <label className="block text-sm font-medium mb-1.5" htmlFor="currency">Currency</label>
                <input 
                  type="text"
                  className="flex h-10 w-full rounded-md border border-[var(--border)] bg-[var(--secondary)] px-3 py-2 text-sm opacity-50 cursor-not-allowed"
                  value={selectedSourceAccount?.currency || "-"}
                  readOnly
                  disabled
                />
              </div>
            </div>

            <Input 
              label="Reference / Memo (Optional)" 
              placeholder="e.g. Rent payment" 
              value={reference}
              onChange={(e) => setReference(e.target.value)}
            />

            {transferMutation.isError && (
              <div className="flex items-center gap-2 p-3 text-sm rounded-md bg-[var(--danger)]/10 text-[var(--danger)] border border-[var(--danger)]/20">
                <AlertCircle size={16} /> 
                {transferMutation.error?.message || "Failed to execute transfer. Please verify account details and funds."}
              </div>
            )}

            <div className="flex justify-end pt-4 border-t border-[var(--border)]">
              <Button 
                type="submit" 
                size="lg" 
                isLoading={transferMutation.isPending}
                disabled={!sourceAccountId || !destinationAccountId || !amount || parseFloat(amount) <= 0}
                className="w-full sm:w-auto text-lg px-8"
              >
                Send Money
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
