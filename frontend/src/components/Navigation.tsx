"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from './AuthProvider';

export function Navigation() {
  const pathname = usePathname();
  const { logout, userProfile } = useAuth();

  const getInitials = (firstName?: string, lastName?: string) => {
    if (firstName && lastName) return `${firstName[0]}${lastName[0]}`;
    if (firstName) return firstName[0];
    return 'U';
  };

  return (
    <div className="sidebar">
      <div className="sidebar-logo">
        <div className="logo-mark">
          <div className="logo-star">
            <svg viewBox="0 0 20 20">
              <path d="M10 2l2.4 5.1 5.6.8-4 4 .9 5.5L10 15 5.1 17.4 6 11.9l-4-4 5.6-.8z" />
            </svg>
          </div>
          <div>
            <div className="logo-text">FinTrust</div>
            <div className="logo-sub">Retail Banking</div>
          </div>
        </div>
      </div>
      <div className="sidebar-user">
        <div className="user-info">
          <div className="user-avatar">{getInitials(userProfile?.firstName, userProfile?.lastName)}</div>
          <div>
            <div className="user-name">{userProfile?.firstName || 'User'} {userProfile?.lastName || ''}</div>
            <div className="user-tier">FinTrust Advantage</div>
          </div>
        </div>
      </div>
      <div className="nav">
        <div className="nav-section">Main</div>
        
        <Link href="/dashboard" className={`nav-item ${pathname === '/dashboard' ? 'active' : ''}`}>
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="7" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
            <rect x="14" y="14" width="7" height="7" rx="1" />
          </svg>
          Overview
        </Link>
        <Link href="/accounts" className={`nav-item ${pathname.startsWith('/accounts') && pathname !== '/accounts/new' ? 'active' : ''}`}>
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <rect x="2" y="5" width="20" height="14" rx="2" />
            <path d="M2 10h20" />
          </svg>
          Accounts
        </Link>
        <Link href="/transfers" className={`nav-item ${pathname.startsWith('/transfers') ? 'active' : ''}`}>
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Transfer <span className="nav-badge">New</span>
        </Link>
        <div className="nav-item">
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          Transactions
        </div>
        
        <div className="nav-section">Financial</div>
        <div className="nav-item">
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
          </svg>
          Investments
        </div>
        <div className="nav-item">
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
          </svg>
          Home Loans
        </div>
        <div className="nav-item">
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path d="M9 14s-4 0-4-4 4-4 4-4h6s4 0 4 4-4 4-4 4H9zM9 14v2a2 2 0 002 2h2a2 2 0 002-2v-2" />
          </svg>
          Insurance
        </div>
        
        <div className="nav-section">Settings</div>
        <div className="nav-item" onClick={logout}>
          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          Logout
        </div>
      </div>
      <div className="sidebar-bottom">
        <div className="upgrade-card">
          <p>Unlock premium insights & concierge support with FinTrust Pro</p>
          <button className="upgrade-btn">Upgrade to Pro</button>
        </div>
      </div>
    </div>
  );
}
