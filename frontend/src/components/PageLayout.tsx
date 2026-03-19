import { Navigation } from './Navigation';
import { AuthGuard } from './AuthGuard';

export function PageLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="min-h-screen bg-[var(--background)] flex">
        <Navigation />
        <main className="flex-1 lg:pl-64 flex flex-col min-h-screen">
          <div className="flex-1 p-6 md:p-8 max-w-7xl mx-auto w-full animate-fade-in">
            {children}
          </div>
        </main>
      </div>
    </AuthGuard>
  );
}
