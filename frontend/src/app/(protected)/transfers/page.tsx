"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { accountsApi } from "@/api/accounts";
import { transfersApi } from "@/api/transfers";
import { useAuth } from "@/components/AuthProvider";
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
    <>
      <div className="topbar">
        <div className="topbar-left">
          <span className="page-title text-[var(--nab-blue-deep)] font-semibold flex items-center gap-2">
            <Send size={18} /> Execute Transfer
          </span>
        </div>
        <div className="topbar-right">
          <div className="topbar-btn notif-dot">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 01-3.46 0" />
            </svg>
          </div>
        </div>
      </div>

      <div className="content animate-fade-in">
        <div className="card max-w-2xl mx-auto shadow-sm">
          <div className="card-header pb-2 mb-6 border-b border-[var(--nab-border)]">
            <div className="card-title text-lg flex items-center gap-2">
              Transfer Details
            </div>
            <p className="text-[var(--nab-text-3)] text-xs font-normal">Move money securely between internal accounts.</p>
          </div>
          
          <form onSubmit={handleSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-medium text-[var(--nab-text-2)] mb-2" htmlFor="sourceAccount">From Account</label>
              <select 
                id="sourceAccount"
                className="flex h-11 w-full rounded-lg border border-[var(--nab-border-2)] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[var(--nab-blue)] disabled:opacity-50 font-[family-name:var(--fm)]"
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
              
              {balanceData ? (
                <p className="text-xs mt-2 font-medium flex items-center gap-1 justify-end text-[var(--nab-text-3)]">
                  Available Balance: <span className="text-[var(--nab-text)] font-[family-name:var(--fm)]">{new Intl.NumberFormat('en-US', { style: 'currency', currency: balanceData.currency }).format(balanceData.availableBalance)}</span>
                </p>
              ) : (
                <p className="text-xs mt-2 min-h-[16px]"></p>
              )}
            </div>

            <div className="pt-2">
              <Input 
                label="To Account ID (Destination)" 
                placeholder="Enter exact account ID..." 
                value={destinationAccountId}
                onChange={(e) => setDestinationAccountId(e.target.value)}
                required
              />
            </div>

            <Input 
              label="Beneficiary Name" 
              placeholder="e.g. John Doe" 
              value={beneficiaryName}
              onChange={(e) => setBeneficiaryName(e.target.value)}
              required
            />

            <div className="grid grid-cols-3 gap-4">
              <div className="col-span-2">
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
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--nab-text-2)] mb-1.5" htmlFor="currency">Currency</label>
                <input 
                  type="text"
                  className="flex h-[42px] w-full rounded-md border border-[var(--nab-border-2)] bg-[var(--nab-surface-3)] px-3 py-2 text-sm opacity-60 cursor-not-allowed font-[family-name:var(--fm)]"
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
              <div className="notif-strip mt-6 mb-0 !border-[var(--nab-red)] !bg-[var(--nab-red-light)]">
                <AlertCircle size={18} className="text-[var(--nab-red)]" /> 
                <p className="!text-[var(--nab-red)]">
                  {transferMutation.error?.message || "Failed to execute transfer. Please verify account details and funds."}
                </p>
              </div>
            )}

            <div className="flex justify-end pt-6">
              <button 
                type="submit" 
                disabled={!sourceAccountId || !destinationAccountId || !amount || parseFloat(amount) <= 0 || transferMutation.isPending}
                className="wa-btn primary !bg-[var(--nab-blue)] !text-white hover:!bg-[var(--nab-blue-deep)] disabled:opacity-50 disabled:cursor-not-allowed w-full sm:w-auto px-8 py-3 text-sm flex items-center justify-center gap-2"
              >
                {transferMutation.isPending ? "Processing..." : "Submit Transfer"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </>
  );
}
