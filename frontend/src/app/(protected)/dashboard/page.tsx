"use client";

import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { accountsApi, type Account } from "@/api/accounts";
import { useAuth } from "@/components/AuthProvider";
import Link from "next/link";
import { format } from "date-fns";
import {
  CreditCard,
  ArrowRight,
  TrendingUp,
  TrendingDown,
  DollarSign,
  PieChart,
  ShieldAlert,
  Target,
  Send,
  Plus
} from "lucide-react";

export default function DashboardPage() {
  const { userProfile } = useAuth();
  const customerId = userProfile?.id || userProfile?.sub;

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts', customerId],
    queryFn: () => accountsApi.getAccountsByCustomer(customerId!),
    enabled: !!customerId,
  });

  const currentDate = format(new Date(), "EEEE, d MMM yyyy");

  return (
    <>
      <div className="topbar">
        <div className="topbar-left">
          <span className="page-title">Good morning, {userProfile?.firstName || 'User'}</span>
          <span className="date-badge">{currentDate}</span>
        </div>
        <div className="topbar-right">
          <div className="topbar-btn notif-dot">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 01-3.46 0" />
            </svg>
          </div>
          <div className="topbar-btn">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.35-4.35" />
            </svg>
          </div>
          <Link href="/transfers">
            <button className="transfer-btn">
              <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M7 16V4m0 0L3 8m4-4l4 4M17 8v12m0 0l4-4m-4 4l-4-4" />
              </svg>
              New Transfer
            </button>
          </Link>
        </div>
      </div>

      <div className="content animate-fade-in">
        
        {/* Placeholder Saga Strip (Mocked for now) */}
        <div className="saga-strip">
          <div className="saga-label">Transfer TRF-2026-001 · $250.00 USD · in progress</div>
          <div className="saga-steps">
            <div className="saga-step done">Initiated</div>
            <div className="saga-sep"></div>
            <div className="saga-step done">Risk scored</div>
            <div className="saga-sep"></div>
            <div className="saga-step active">Debiting</div>
            <div className="saga-sep"></div>
            <div className="saga-step pending">Crediting</div>
            <div className="saga-sep"></div>
            <div className="saga-step pending">Complete</div>
          </div>
        </div>

        <div className="welcome-bar">
          <div className="welcome-text">
            <h2>Financial Overview</h2>
            <p>Your money at a glance. You have {accounts?.length || 0} active accounts.</p>
          </div>
          <div className="welcome-actions">
            <Link href="/accounts/new">
              <button className="wa-btn primary">Open New Account</button>
            </Link>
            <button className="wa-btn ghost">View Report</button>
          </div>
        </div>

        <div className="stats-row">
          <div className="stat-card">
            <div className="stat-icon si-blue"><DollarSign /></div>
            <div className="stat-label">Total Balance</div>
            <div className="stat-value">$14,250.00</div>
            <div className="stat-change up"><TrendingUp size={12}/> +4.2% this month</div>
          </div>
          <div className="stat-card">
            <div className="stat-icon si-green"><TrendingUp /></div>
            <div className="stat-label">Income (30d)</div>
            <div className="stat-value">$6,450.00</div>
            <div className="stat-change up"><TrendingUp size={12}/> +1.1% this month</div>
          </div>
          <div className="stat-card">
            <div className="stat-icon si-amber"><TrendingDown /></div>
            <div className="stat-label">Spending (30d)</div>
            <div className="stat-value">$2,140.50</div>
            <div className="stat-change down"><TrendingDown size={12}/> +12.4% this month</div>
          </div>
          <div className="stat-card">
            <div className="stat-icon si-teal"><PieChart /></div>
            <div className="stat-label">Savings Rate</div>
            <div className="stat-value">66.8%</div>
            <div className="stat-change neutral">-2.1% from last month</div>
          </div>
        </div>

        <div className="qaction-grid mb-5" style={{marginBottom: "22px"}}>
          <Link href="/transfers" className="qaction">
            <div className="qaction-icon" style={{background: "var(--nab-blue-light)", color: "var(--nab-blue)"}}><Send /></div>
            <div className="qaction-label">Send Money</div>
          </Link>
          <div className="qaction">
            <div className="qaction-icon" style={{background: "var(--nab-green-light)", color: "var(--nab-green)"}}><Plus /></div>
            <div className="qaction-label">Add Funds</div>
          </div>
          <div className="qaction">
            <div className="qaction-icon" style={{background: "var(--nab-amber-light)", color: "var(--nab-amber)"}}><ShieldAlert /></div>
            <div className="qaction-label">Freeze Card</div>
          </div>
          <div className="qaction">
            <div className="qaction-icon" style={{background: "var(--nab-teal-light)", color: "var(--nab-teal)"}}><Target /></div>
            <div className="qaction-label">Settings</div>
          </div>
        </div>

        <div className="grid-3">
          <div className="card">
            <div className="card-header">
              <div className="card-title">Your Accounts</div>
              <Link href="/accounts"><div className="card-action">View all</div></Link>
            </div>
            
            {isLoading ? (
              <div className="animate-pulse space-y-4">
                <div className="h-12 bg-[var(--nab-surface-2)] rounded"></div>
                <div className="h-12 bg-[var(--nab-surface-2)] rounded"></div>
              </div>
            ) : accounts?.length === 0 ? (
              <div className="text-center py-6">
                <p className="text-[var(--nab-text-3)] text-sm mb-4">You don't have any accounts yet.</p>
                <Link href="/accounts/new">
                  <button className="wa-btn primary bg-[var(--nab-blue)] text-white">Open Account</button>
                </Link>
              </div>
            ) : (
              <div>
                {accounts
                  ?.sort((a, b) => new Date(b.openedAt).getTime() - new Date(a.openedAt).getTime())
                  .map(account => (
                    <AccountListItem key={account.accountId} account={account} />
                  ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-header">
              <div className="card-title">Spending</div>
              <div className="card-action">Details</div>
            </div>
            <div className="spending-tabs">
              <div className="spending-tab active">This month</div>
              <div className="spending-tab">Quarter</div>
              <div className="spending-tab">Year</div>
            </div>
            <div className="chart-wrap bg-[var(--nab-surface-2)] rounded-lg flex items-center justify-center">
              <div className="text-center">
                <PieChart size={40} className="mx-auto text-[var(--nab-blue-mid)] mb-2" />
                <span className="text-xs text-[var(--nab-text-3)]">Chart.js pending</span>
              </div>
            </div>
          </div>
        </div>

        <div className="grid-2">
          <div className="card">
            <div className="card-header">
              <div className="card-title">Recent Transactions</div>
              <div className="card-action">View all</div>
            </div>
            <div className="text-center py-8">
              <p className="text-[var(--nab-text-3)] text-sm">No recent transactions to display.</p>
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <div className="card-title">Transfer Limits</div>
              <div className="card-action">Manage</div>
            </div>
            
            <div className="limit-item">
              <div className="limit-top">
                <div className="limit-name">Daily Internal</div>
                <div className="limit-vals">$250 / $10,000</div>
              </div>
              <div className="progress-bar">
                <div className="progress-fill bg-[var(--nab-blue)]" style={{width: "2.5%"}}></div>
              </div>
            </div>

            <div className="limit-item">
              <div className="limit-top">
                <div className="limit-name">Monthly External</div>
                <div className="limit-vals">$4,500 / $25,000</div>
              </div>
              <div className="progress-bar">
                <div className="progress-fill bg-[var(--nab-teal)]" style={{width: "18%"}}></div>
              </div>
            </div>

            <div className="limit-item">
              <div className="limit-top">
                <div className="limit-name">Overseas Transfer</div>
                <div className="limit-vals">$0 / $5,000</div>
              </div>
              <div className="progress-bar">
                <div className="progress-fill bg-[var(--nab-amber)]" style={{width: "0%"}}></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

function AccountListItem({ account }: { account: Account }) {
  const { data: balanceData } = useQuery({
    queryKey: ['balance', account.accountId],
    queryFn: () => accountsApi.getBalance(account.accountId),
  });

  return (
    <div className="account-item group">
      <div className="acct-icon bg-[var(--nab-blue-light)] text-[var(--nab-blue)]">
        <CreditCard size={20} />
      </div>
      <div className="acct-info">
        <div className="acct-name">{account.type} Account</div>
        <div className="acct-num">ID: {account.accountId.split('-')[0]}</div>
      </div>
      <div className="acct-balance">
        {balanceData ? (
          <>
            <div className="acct-amount">{new Intl.NumberFormat('en-US', { style: 'currency', currency: balanceData.currency || account.currency }).format(balanceData.availableBalance)}</div>
            <div className="acct-avail">Available</div>
          </>
        ) : (
          <div className="acct-avail animate-pulse bg-[var(--nab-surface-2)] h-4 w-16 rounded mt-1"></div>
        )}
      </div>
    </div>
  );
}
