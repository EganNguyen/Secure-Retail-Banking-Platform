"use client";

import { useAuth } from "@/components/AuthProvider";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function LandingPage() {
  const { isAuthenticated, login } = useAuth();
  const router = useRouter();
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      router.push("/dashboard");
    }
  }, [isAuthenticated, router]);

  const handleLogin = async () => {
    if (isLoggingIn) return;
    setIsLoggingIn(true);
    try {
      await login();
      // AuthProvider handles redirect after Keycloak login
    } catch (e) {
      console.error(e);
      setIsLoggingIn(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col selection:bg-[var(--nab-blue)] selection:text-white pb-20 bg-[var(--nab-surface-2)] font-['DM_Sans',sans-serif]">
      {/* Navigation Bar */}
      <nav className="flex items-center justify-between px-8 h-20 bg-white border-b border-[var(--nab-blue-deep)]/10 sticky top-0 z-50">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 bg-[var(--nab-red)] rounded-lg flex items-center justify-center shadow-sm">
            <svg viewBox="0 0 20 20" className="w-5 h-5 fill-white">
              <path d="M10 2l2.4 5.1 5.6.8-4 4 .9 5.5L10 15 5.1 17.4 6 11.9l-4-4 5.6-.8z" />
            </svg>
          </div>
          <div>
            <div className="text-[19px] font-bold text-[var(--nab-text)] tracking-tight leading-none">
              FinTrust
            </div>
            <div className="text-[10px] font-medium text-[var(--nab-text-3)] tracking-widest uppercase mt-0.5">
              Retail Banking
            </div>
          </div>
        </div>
        <div className="flex items-center gap-6">
          <a
            href="#"
            className="hidden md:block text-[14px] font-medium text-[var(--nab-text-2)] hover:text-[var(--nab-blue)] transition-colors"
          >
            Personal
          </a>
          <a
            href="#"
            className="hidden md:block text-[14px] font-medium text-[var(--nab-text-2)] hover:text-[var(--nab-blue)] transition-colors"
          >
            Business
          </a>
          <button
            onClick={handleLogin}
            disabled={isLoggingIn}
            className="flex items-center gap-2 bg-white border border-[var(--nab-blue-deep)]/20 text-[var(--nab-blue-deep)] px-5 py-2.5 rounded-lg text-[14px] font-semibold hover:bg-[var(--nab-surface-2)] transition-all shadow-sm disabled:opacity-70"
          >
            {isLoggingIn ? (
              <span className="animate-spin rounded-full h-4 w-4 border-b-2 border-[var(--nab-blue-deep)] inline-block"></span>
            ) : (
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-[var(--nab-blue)]"
              >
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
            )}
            <span>{isLoggingIn ? "Authenticating..." : "Log In"}</span>
          </button>
        </div>
      </nav>

      {/* Main Hero Section */}
      <main 
        className="max-w-[1200px] w-full mx-auto px-6 mt-10 opacity-0" 
        style={{ animation: 'slideInUp 0.7s ease-out forwards' }}
      >
        <div className="relative overflow-hidden bg-gradient-to-br from-[var(--nab-blue-deep)] to-[var(--nab-blue)] rounded-[24px] px-8 py-20 sm:px-16 sm:py-24 shadow-xl">
          {/* Decorative background circles */}
          <div className="absolute -top-40 -right-40 w-[400px] h-[400px] rounded-full bg-white/5 pointer-events-none"></div>
          <div className="absolute -bottom-20 right-20 w-[250px] h-[250px] rounded-full bg-white/5 pointer-events-none"></div>
          <div className="absolute top-20 left-10 w-[100px] h-[100px] rounded-full bg-white/5 pointer-events-none"></div>

          <div className="relative z-10 max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/10 border border-white/20 text-white/90 text-xs font-semibold uppercase tracking-wider mb-8 backdrop-blur-sm">
              <span className="w-2 h-2 rounded-full bg-[var(--nab-green-light)]"></span>
              New: Keycloak Auth Integration
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-white tracking-tight leading-[1.1]">
              Secure Retail Banking <br />{" "}
              <span className="text-white/70">Reimagined.</span>
            </h1>

            <p className="mt-6 text-lg sm:text-xl text-white/80 leading-relaxed max-w-xl font-normal">
              Experience next-generation banking. Open secure checking and
              savings accounts, execute seamless internal transfers, and manage
              your wealth from a premium dashboard.
            </p>

            <div className="mt-10 flex flex-wrap items-center gap-4">
              <button
                onClick={handleLogin}
                className="bg-[var(--nab-red)] hover:bg-[#A80000] text-white px-8 py-3.5 rounded-xl text-[15px] font-bold transition-all shadow-lg shadow-[#CC0000]/20 flex items-center gap-2"
              >
                Open an Account
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <line x1="5" y1="12" x2="19" y2="12"></line>
                  <polyline points="12 5 19 12 12 19"></polyline>
                </svg>
              </button>
              <button className="bg-white/10 hover:bg-white/20 text-white border border-white/20 px-8 py-3.5 rounded-xl text-[15px] font-semibold transition-all flex items-center gap-2 backdrop-blur-sm">
                Explore Features
              </button>
            </div>
          </div>
        </div>

        {/* Feature Cards Grid */}
        <div className="mt-12 grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Feature 1 */}
          <div className="bg-white rounded-[16px] p-8 border border-[var(--nab-border)] shadow-sm hover:shadow-md transition-shadow group">
            <div className="w-14 h-14 bg-[var(--nab-blue-light)] rounded-[12px] flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-[var(--nab-blue)]"
              >
                <rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect>
                <line x1="1" y1="10" x2="23" y2="10"></line>
              </svg>
            </div>
            <h3 className="text-[18px] font-bold text-[var(--nab-text)] mb-3">
              Multiple Accounts
            </h3>
            <p className="text-[var(--nab-text-2)] text-[14px] leading-relaxed mb-6">
              Open flexible checking, high-yield iSaver accounts, or term
              deposits instantly without visiting a branch.
            </p>
            <div className="text-[var(--nab-blue)] text-[13px] font-semibold flex items-center gap-1 cursor-pointer hover:gap-2 transition-all">
              Learn more
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </div>
          </div>

          {/* Feature 2 */}
          <div className="bg-white rounded-[16px] p-8 border border-[var(--nab-border)] shadow-sm hover:shadow-md transition-shadow group">
            <div className="w-14 h-14 bg-[var(--nab-green-light)] rounded-[12px] flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-[var(--nab-green)]"
              >
                <line x1="22" y1="2" x2="11" y2="13"></line>
                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
              </svg>
            </div>
            <h3 className="text-[18px] font-bold text-[var(--nab-text)] mb-3">
              Instant Transfers
            </h3>
            <p className="text-[var(--nab-text-2)] text-[14px] leading-relaxed mb-6">
              Move money between accounts securely in real-time. Set up scheduled
              payments and custom daily limits.
            </p>
            <div className="text-[var(--nab-green)] text-[13px] font-semibold flex items-center gap-1 cursor-pointer hover:gap-2 transition-all">
              Learn more
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </div>
          </div>

          {/* Feature 3 */}
          <div className="bg-white rounded-[16px] p-8 border border-[var(--nab-border)] shadow-sm hover:shadow-md transition-shadow group">
            <div className="w-14 h-14 bg-[var(--nab-teal-light)] rounded-[12px] flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-[var(--nab-teal)]"
              >
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                <polyline points="9 12 11 14 15 10"></polyline>
              </svg>
            </div>
            <h3 className="text-[18px] font-bold text-[var(--nab-text)] mb-3">
              Bank-grade Security
            </h3>
            <p className="text-[var(--nab-text-2)] text-[14px] leading-relaxed mb-6">
              Powered by robust Event Sourcing architecture and industry-standard
              Keycloak authentication protocols.
            </p>
            <div className="text-[var(--nab-teal)] text-[13px] font-semibold flex items-center gap-1 cursor-pointer hover:gap-2 transition-all">
              Learn more
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </div>
          </div>
        </div>

        {/* Trust Banner */}
        <div className="mt-12 text-center pb-12">
          <p className="text-[var(--nab-text-3)] text-[13px] font-medium uppercase tracking-widest mb-6">
            Trusted by leading enterprises
          </p>
          <div className="flex flex-wrap justify-center items-center gap-8 md:gap-16 opacity-40 grayscale">
            <div className="font-bold text-xl font-['DM_Mono']">ACME Corp</div>
            <div className="font-bold text-xl font-['DM_Mono']">Globex</div>
            <div className="font-bold text-xl font-['DM_Mono']">Soylent</div>
            <div className="font-bold text-xl font-['DM_Mono']">Initech</div>
          </div>
        </div>
      </main>
      <style dangerouslySetInnerHTML={{__html: `
        @keyframes slideInUp {
          from { transform: translateY(20px); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
      `}} />
    </div>
  );
}
