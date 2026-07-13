"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { RequireAuth } from "@/components/auth/require-auth";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorAlert,
  PageHeader,
  Skeleton,
  StatCard,
} from "@/components/ui";
import { getDashboardSummary } from "@/lib/api/dashboard";
import { formatDateTime, formatPrice } from "@/lib/format";
import type { DashboardSummary } from "@/lib/types";

function DashboardContent() {
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getDashboardSummary());
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Snapshot of what you’re tracking and recent price movement."
        actions={
          <Link href="/items">
            <Button variant="secondary" size="sm">
              Manage items
            </Button>
          </Link>
        }
      />

      <ErrorAlert error={error} />

      {loading && !data ? (
        <div className="space-y-6">
          <div className="grid gap-3 sm:grid-cols-3 sm:gap-4">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
          <Skeleton className="h-64" />
        </div>
      ) : data ? (
        <div className="space-y-6">
          <div className="grid gap-3 sm:grid-cols-3 sm:gap-4">
            <StatCard
              label="Tracked items"
              value={data.totalTrackedItems}
              hint="Products you’re watching"
            />
            <StatCard
              label="Recent price drops"
              value={data.recentPriceDrops.length}
              hint="Last 7 days"
              accent="success"
            />
            <StatCard
              label="Unhealthy products"
              value={data.unhealthyCount}
              hint={
                data.unhealthyCount > 0
                  ? "Scrape failures — re-enable checks from the product page"
                  : "All scrapers healthy"
              }
              accent={data.unhealthyCount > 0 ? "warn" : "default"}
            />
          </div>

          <Card className="p-0 sm:p-0">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--border)] px-4 py-3.5 sm:px-5">
              <div>
                <h2 className="font-semibold">Recent price drops</h2>
                <p className="text-xs text-[var(--muted)]">
                  Newest alerts from your tracked products
                </p>
              </div>
              <Link
                href="/items"
                className="text-sm font-medium text-[var(--accent)] hover:underline"
              >
                View all
              </Link>
            </div>

            {data.recentPriceDrops.length === 0 ? (
              <EmptyState
                title="No price drops yet"
                description="Track a product and wait for a check cycle. Drops show up here when prices fall."
                action={
                  <Link href="/items">
                    <Button>Track a product</Button>
                  </Link>
                }
              />
            ) : (
              <ul className="divide-y divide-[var(--border)]">
                {data.recentPriceDrops.map((drop) => (
                  <li key={`${drop.trackedItemId}-${drop.changedAt}`}>
                    <Link
                      href={`/items/${drop.trackedItemId}`}
                      className="flex flex-col gap-2 px-4 py-4 transition-colors hover:bg-[var(--surface-muted)]/60 sm:flex-row sm:items-center sm:justify-between sm:px-5"
                    >
                      <div className="min-w-0">
                        <p className="truncate font-medium">
                          {drop.productName || "Product"}
                        </p>
                        <p className="text-xs text-[var(--muted)]">
                          {formatDateTime(drop.changedAt)}
                        </p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2 text-sm">
                        <span className="tabular-nums text-[var(--muted)] line-through">
                          {formatPrice(drop.oldPrice)}
                        </span>
                        <span className="font-semibold tabular-nums text-[var(--accent)]">
                          {formatPrice(drop.newPrice)}
                        </span>
                        <Badge tone="success">Drop</Badge>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      ) : null}
    </>
  );
}

export default function HomePage() {
  return (
    <RequireAuth>
      <DashboardContent />
    </RequireAuth>
  );
}
