"use client";

import { useQuery } from "@tanstack/react-query";
import { accountsApi } from "@/api/accounts";
import { useAuth } from "@/components/AuthProvider";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import Link from "next/link";
import { Plus, CreditCard, ArrowRight } from "lucide-react";

export default function DashboardPage() {
  const { userProfile } = useAuth();
  // Using userProfile?.id assuming Keycloak injects sub/id there. 
  // If `id` isn't available from Keycloak, we should use `userProfile?.username` or a specific claim.
  const customerId = userProfile?.id || userProfile?.sub;

  const { data: accounts, isLoading, error } = useQuery({
    queryKey: ['accounts', customerId],
    queryFn: () => accountsApi.getAccountsByCustomer(customerId!),
    enabled: !!customerId,
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Welcome back!</h1>
          <p className="text-[var(--foreground)] opacity-70 mt-1">Here is the overview of your accounts.</p>
        </div>
        <Link href="/accounts/new">
          <Button className="bg-[var(--primary)] text-white shadow-md hover:shadow-lg transition-all rounded-full px-6">
            <Plus className="mr-2 h-4 w-4" /> Open New Account
          </Button>
        </Link>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map(i => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="h-20 bg-[var(--secondary)]/50 rounded-t-xl" />
              <CardContent className="h-24" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="border-[var(--danger)]/50 bg-[var(--danger)]/5">
          <CardContent className="pt-6 text-center text-[var(--danger)]">
            <p>Failed to load accounts. Please try again later.</p>
          </CardContent>
        </Card>
      ) : !accounts || accounts.length === 0 ? (
        <Card className="glass relative overflow-hidden group">
          <div className="absolute inset-0 bg-gradient-to-br from-[var(--primary)]/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
          <CardContent className="flex flex-col items-center justify-center p-12 text-center relative z-10">
            <div className="w-16 h-16 bg-[var(--secondary)] rounded-full flex items-center justify-center mb-4 text-[var(--primary)]">
              <CreditCard size={32} />
            </div>
            <h3 className="text-xl font-semibold mb-2">No Accounts Found</h3>
            <p className="text-[var(--foreground)] opacity-70 max-w-sm mb-6">
              Looks like you haven't opened any accounts yet. Open a checking or savings account to get started.
            </p>
            <Link href="/accounts/new">
              <Button>Open First Account</Button>
            </Link>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {accounts.map((account) => (
            <Card key={account.accountId} className="group hover:border-[var(--primary)]/50 transition-colors shadow-sm hover:shadow-md cursor-pointer flex flex-col justify-between relative overflow-hidden">
              <Link href={`/accounts/${account.accountId}`} className="absolute inset-0 z-10">
                <span className="sr-only">View Account</span>
              </Link>
              <div className="absolute top-0 right-0 p-4 opacity-0 group-hover:opacity-100 transition-opacity transform group-hover:translate-x-1 text-[var(--primary)]">
                <ArrowRight size={20} />
              </div>
              <CardHeader className="pb-2">
                <CardTitle className="text-[var(--foreground)]/70 text-sm font-medium uppercase tracking-wider flex justify-between">
                  {account.type} ACCOUNT
                  <span className={`text-xs px-2 py-0.5 rounded-full ${
                    account.status === 'ACTIVE' ? 'bg-green-100 text-green-700 dark:bg-green-900/30' : 
                    account.status === 'FROZEN' ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/30' : 
                    'bg-red-100 text-red-700 dark:bg-red-900/30'
                  }`}>
                    {account.status}
                  </span>
                </CardTitle>
                <div className="text-2xl font-bold mt-2 font-mono">
                  {account.accountId.split('-')[0]}...
                </div>
              </CardHeader>
              <CardContent>
                {/* Balance is fetched independently per account, or could be included in the list model depending on backend. We fetch it here eagerly. */}
                <AccountBalance accountId={account.accountId} />
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function AccountBalance({ accountId }: { accountId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['balance', accountId],
    queryFn: () => accountsApi.getBalance(accountId),
  });

  if (isLoading) return <div className="h-8 w-1/2 bg-[var(--secondary)] rounded animate-pulse mt-2" />;
  if (!data) return <div className="text-sm opacity-50">Balance unavailable</div>;

  return (
    <div className="mt-4">
      <div className="text-3xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-[var(--primary)] to-indigo-600">
        {new Intl.NumberFormat('en-US', { style: 'currency', currency: data.currency }).format(data.availableBalance)}
      </div>
      <p className="text-xs opacity-60 mt-1">Available Balance</p>
    </div>
  );
}
