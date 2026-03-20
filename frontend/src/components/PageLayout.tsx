import { Navigation } from './Navigation';
import { AuthGuard } from './AuthGuard';

export function PageLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="shell">
        <Navigation />
        <main className="main">
          {children}
        </main>
      </div>
    </AuthGuard>
  );
}
