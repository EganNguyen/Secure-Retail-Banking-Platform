"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from './AuthProvider';
import { Home, CreditCard, Send, LogOut, User } from 'lucide-react';

export function Navigation() {
  const pathname = usePathname();
  const { logout, userProfile } = useAuth();

  const links = [
    { name: 'Dashboard', href: '/dashboard', icon: Home },
    { name: 'Accounts', href: '/accounts', icon: CreditCard },
    { name: 'Transfers', href: '/transfers', icon: Send },
  ];

  return (
    <aside className="fixed inset-y-0 left-0 w-64 bg-[var(--card)] border-r border-[var(--border)] flex flex-col z-10 transition-transform">
      <div className="flex h-16 items-center flex-shrink-0 px-6 border-b border-[var(--border)]">
        <h1 className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-[var(--primary)] to-indigo-500">
          SecureBank
        </h1>
      </div>
      
      <div className="flex-1 overflow-y-auto py-6 flex flex-col gap-2 px-3">
        {links.map((link) => {
          const isActive = pathname.startsWith(link.href);
          const Icon = link.icon;
          return (
            <Link
              key={link.name}
              href={link.href}
              className={`flex items-center gap-3 px-3 py-2 rounded-lg transition-colors ${
                isActive 
                  ? 'bg-[var(--primary)] text-white shadow-sm' 
                  : 'text-[var(--foreground)] hover:bg-[var(--secondary)]'
              }`}
            >
              <Icon size={20} />
              <span className="font-medium">{link.name}</span>
            </Link>
          );
        })}
      </div>
      
      <div className="p-4 border-t border-[var(--border)]">
        <div className="flex items-center gap-3 mb-4 px-2">
          <div className="w-10 h-10 rounded-full bg-[var(--secondary)] flex items-center justify-center text-[var(--foreground)]">
            <User size={20} />
          </div>
          <div className="flex flex-col text-sm overflow-hidden">
            <span className="font-medium truncate">{userProfile?.firstName || 'User'} {userProfile?.lastName || ''}</span>
            <span className="text-xs opacity-70 truncate">{userProfile?.email || 'Customer'}</span>
          </div>
        </div>
        <button
          onClick={logout}
          className="flex items-center gap-3 w-full px-3 py-2 rounded-lg text-[var(--danger)] hover:bg-[var(--danger)] hover:text-white transition-colors"
        >
          <LogOut size={20} />
          <span className="font-medium">Logout</span>
        </button>
      </div>
    </aside>
  );
}
