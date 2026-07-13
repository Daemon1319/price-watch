"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
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

export default function RegisterPage() {
  const { register, authenticated, ready } = useAuth();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const cooldown = useRetryCooldown(error);
  const submitBlocked = loading || cooldown > 0;

  useEffect(() => {
    if (ready && authenticated) router.replace("/");
  }, [ready, authenticated, router]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitBlocked) return;
    setError(null);
    setLoading(true);
    try {
      await register(email.trim(), password);
      router.replace("/");
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
        <h1 className="text-2xl font-semibold tracking-tight">
          Create your account
        </h1>
        <p className="mt-1.5 text-sm text-[var(--muted)]">
          Password must be 8–72 characters.
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
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
              placeholder="At least 8 characters"
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
                <Spinner /> Creating…
              </>
            ) : cooldown > 0 ? (
              `Try again in ${cooldown}s`
            ) : (
              "Create account"
            )}
          </Button>
        </form>
        <p className="mt-5 text-center text-sm text-[var(--muted)]">
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-semibold text-[var(--accent)] hover:underline"
          >
            Sign in
          </Link>
        </p>
      </Card>
    </div>
  );
}
