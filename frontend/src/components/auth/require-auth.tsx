"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { Skeleton } from "@/components/ui";
import { useMounted } from "@/lib/hooks/use-mounted";

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { ready, authenticated } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const mounted = useMounted();

  useEffect(() => {
    if (mounted && ready && !authenticated) {
      router.replace(`/login?next=${encodeURIComponent(pathname || "/")}`);
    }
  }, [mounted, ready, authenticated, router, pathname]);

  if (!mounted || !ready) {
    return (
      <div className="space-y-4 py-4" aria-busy aria-label="Loading">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-4 w-72 max-w-full" />
        <div className="grid gap-3 sm:grid-cols-3">
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center text-sm text-[var(--muted)]">
        Redirecting to login…
      </div>
    );
  }

  return <>{children}</>;
}
