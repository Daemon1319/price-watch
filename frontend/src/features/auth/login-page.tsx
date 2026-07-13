"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import {
  Button,
  Card,
  ErrorAlert,
  Input,
  Label,
  Spinner,
} from "@/components/ui";
import { useRetryCooldown } from "@/lib/hooks/use-retry-cooldown";

function LoginForm() {
  const { login, authenticated, ready } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = searchParams.get("next") || "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const cooldown = useRetryCooldown(error);
  const submitBlocked = loading || cooldown > 0;

  useEffect(() => {
    if (ready && authenticated) router.replace(next);
  }, [ready, authenticated, router, next]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitBlocked) return;
    setError(null);
    setLoading(true);
    try {
      await login(email.trim(), password);
      router.replace(next);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-[70dvh] flex-col justify-center py-4">
      <div className="mb-6 text-center">
        <div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-2xl bg-[var(--accent)] text-lg font-bold text-white">
          P
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">Welcome back</h1>
        <p className="mt-1.5 text-sm text-[var(--muted)]">
          Sign in to track prices and stock alerts.
        </p>
      </div>

      <Card className="shadow-[var(--shadow-md)]">
        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              name="email"
              autoComplete="email"
              required
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div>
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              name="password"
              autoComplete="current-password"
              required
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <ErrorAlert error={error} />
          <Button
            type="submit"
            className="w-full"
            size="lg"
            disabled={submitBlocked}
          >
            {loading ? (
              <>
                <Spinner /> Signing in…
              </>
            ) : cooldown > 0 ? (
              `Try again in ${cooldown}s`
            ) : (
              "Sign in"
            )}
          </Button>
        </form>
        <p className="mt-5 text-center text-sm text-[var(--muted)]">
          No account?{" "}
          <Link
            href="/register"
            className="font-semibold text-[var(--accent)] hover:underline"
          >
            Create one
          </Link>
        </p>
      </Card>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[70dvh] items-center justify-center text-sm text-[var(--muted)]">
          Loading…
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
