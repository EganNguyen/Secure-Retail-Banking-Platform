import { Button } from "@/components/ui/Button";
import { FileQuestion } from "lucide-react";
import Link from "next/link";

export default function NotFound() {
  return (
    <div className="min-h-screen bg-[var(--background)] flex items-center justify-center p-6">
      <div className="w-full max-w-md bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-md p-8 text-center flex flex-col gap-4 items-center">
        <div className="w-20 h-20 bg-[var(--secondary)] rounded-full flex items-center justify-center text-[var(--foreground)] opacity-50 mb-2">
          <FileQuestion size={40} />
        </div>
        <h2 className="text-3xl font-bold">Page Not Found</h2>
        <p className="text-[var(--foreground)] opacity-70 mb-6 font-medium">
          We couldn't find the page you were looking for. It may have been moved or doesn't exist.
        </p>
        <Link href="/">
          <Button size="lg" className="w-full px-8">Return Home</Button>
        </Link>
      </div>
    </div>
  );
}
