"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { accountsApi } from "@/api/accounts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Input";
import { ArrowLeft, Lock, Unlock, XCircle, Clock } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

export default function AccountDetailsPage() {
  const params = useParams();
  const router = useRouter();
  const accountId = params.id as string;
  const queryClient = useQueryClient();

  const [isActionModalOpen, setIsActionModalOpen] = useState(false);
  const [actionType, setActionType] = useState<"FREEZE" | "UNFREEZE" | "CLOSE" | null>(null);
  const [actionReason, setActionReason] = useState("");

  const { data: account, isLoading: accountLoading } = useQuery({
    queryKey: ['account', accountId],
    queryFn: () => accountsApi.getAccount(accountId),
  });

  const { data: balance, isLoading: balanceLoading } = useQuery({
    queryKey: ['balance', accountId],
    queryFn: () => accountsApi.getBalance(accountId),
  });

  const actionMutation = useMutation({
    mutationFn: () => {
      if (actionType === "FREEZE") return accountsApi.freezeAccount(accountId, actionReason);
      if (actionType === "UNFREEZE") return accountsApi.unfreezeAccount(accountId, actionReason);
      if (actionType === "CLOSE") return accountsApi.closeAccount(accountId, actionReason);
      return Promise.reject("Invalid action");
    },
    onSuccess: () => {
      setIsActionModalOpen(false);
      setActionReason("");
      queryClient.invalidateQueries({ queryKey: ['account', accountId] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] }); // invalidate list too
    }
  });

  const handleOpenAction = (type: "FREEZE" | "UNFREEZE" | "CLOSE") => {
    setActionType(type);
    setIsActionModalOpen(true);
  };

  const handleExecuteAction = () => {
    if (!actionReason.trim()) return;
    actionMutation.mutate();
  };

  if (accountLoading || balanceLoading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="flex items-center gap-4">
          <div className="w-10 h-10 bg-[var(--secondary)] rounded-full" />
          <div className="h-10 w-64 bg-[var(--secondary)] rounded" />
        </div>
        <Card className="h-64 bg-[var(--secondary)]/50" />
      </div>
    );
  }

  if (!account) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold">Account not found</h2>
        <Button className="mt-4" onClick={() => router.push("/dashboard")}>Return to Dashboard</Button>
      </div>
    );
  }

  const isActive = account.status === "ACTIVE";
  const isFrozen = account.status === "FROZEN";
  const isClosed = account.status === "CLOSED";

  return (
    <div className="space-y-8 max-w-5xl mx-auto">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div className="flex items-center gap-4">
          <Link href="/dashboard" className="p-2 rounded-full hover:bg-[var(--secondary)] transition-colors">
            <ArrowLeft size={24} />
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-3xl font-bold tracking-tight">{account.type} Account</h1>
              <span className={`text-sm px-3 py-1 rounded-full font-medium ${
                isActive ? 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400' : 
                isFrozen ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-400' : 
                'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
              }`}>
                {account.status}
              </span>
            </div>
            <p className="text-[var(--foreground)] opacity-70 mt-1 font-mono">{account.accountId}</p>
          </div>
        </div>
        
        <div className="flex gap-2">
          {isActive && (
            <Button variant="outline" onClick={() => handleOpenAction("FREEZE")} className="text-orange-500 hover:text-orange-600 border-orange-200 hover:bg-orange-50 dark:hover:bg-orange-900/20">
              <Lock className="mr-2 h-4 w-4" /> Freeze
            </Button>
          )}
          {isFrozen && (
            <Button variant="outline" onClick={() => handleOpenAction("UNFREEZE")} className="text-green-500 hover:text-green-600 border-green-200 hover:bg-green-50 dark:hover:bg-green-900/20">
              <Unlock className="mr-2 h-4 w-4" /> Unfreeze
            </Button>
          )}
          {!isClosed && (
            <Button variant="danger" onClick={() => handleOpenAction("CLOSE")}>
              <XCircle className="mr-2 h-4 w-4" /> Close
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="col-span-1 md:col-span-2 glass overflow-hidden relative">
          <div className="absolute top-0 right-0 p-8 opacity-5 text-[var(--primary)]">
            <Lock size={150} />
          </div>
          <CardHeader>
            <CardTitle className="opacity-70 text-sm font-medium uppercase tracking-wider">Available Balance</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-5xl lg:text-7xl font-bold tracking-tighter bg-clip-text text-transparent bg-gradient-to-r from-[var(--primary)] to-indigo-600 mb-2">
              {balance ? new Intl.NumberFormat('en-US', { style: 'currency', currency: balance.currency }).format(balance.availableBalance) : '$0.00'}
            </div>
            <p className="opacity-60 text-sm flex items-center gap-1 mt-6">
              <Clock size={14} /> Last updated: {balance?.updatedAt ? new Date(balance.updatedAt).toLocaleString() : 'Just now'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Account Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm opacity-60">Product Package</p>
              <p className="font-medium text-lg capitalize">{account.productCode.toLowerCase()} Banking</p>
            </div>
            <div>
              <p className="text-sm opacity-60">Currency</p>
              <p className="font-medium">{account.currency}</p>
            </div>
            <div>
              <p className="text-sm opacity-60">Customer ID</p>
              <p className="font-mono text-xs mt-1 p-2 bg-[var(--secondary)] rounded truncate">{account.customerId}</p>
            </div>
            <div>
              <p className="text-sm opacity-60">Opened On</p>
              <p className="font-medium">{new Date(account.openedAt).toLocaleDateString()}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Modal 
        isOpen={isActionModalOpen} 
        onClose={() => setIsActionModalOpen(false)}
        title={`${actionType === 'FREEZE' ? 'Freeze' : actionType === 'UNFREEZE' ? 'Unfreeze' : 'Close'} Account`}
        footer={
          <div className="flex gap-3">
            <Button variant="ghost" onClick={() => setIsActionModalOpen(false)}>Cancel</Button>
            <Button 
              variant={actionType === 'CLOSE' ? 'danger' : 'primary'} 
              onClick={handleExecuteAction}
              isLoading={actionMutation.isPending}
              disabled={!actionReason.trim()}
            >
              Confirm
            </Button>
          </div>
        }
      >
        <div className="space-y-4 py-2">
          <p className="text-sm">
            You are about to <strong>{actionType?.toLowerCase()}</strong> this account. 
            {actionType === 'CLOSE' && ' This action cannot be undone.'}
          </p>
          <Input 
            label="Reason" 
            placeholder="Please provide a reason..." 
            value={actionReason}
            onChange={(e) => setActionReason(e.target.value)}
            required
            error={actionMutation.isError ? "Action failed. Please try again." : ""}
          />
        </div>
      </Modal>
    </div>
  );
}
