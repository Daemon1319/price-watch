"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { RequireAuth } from "@/components/auth/require-auth";
import {
  Badge,
  Button,
  Card,
  ErrorAlert,
  PageHeader,
  Skeleton,
  Spinner,
  TextLink,
} from "@/components/ui";
import {
  getPriceHistory,
  getProduct,
  reenableChecks,
} from "@/lib/api/products";
import {
  formatDateTime,
  formatPrice,
  formatVariant,
  stockLabel,
} from "@/lib/format";
import type { PriceHistoryPoint, Product } from "@/lib/types";

function ProductContent() {
  const params = useParams();
  const id = String(params.id);

  const [product, setProduct] = useState<Product | null>(null);
  const [history, setHistory] = useState<PriceHistoryPoint[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [reenabling, setReenabling] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, h] = await Promise.all([
        getProduct(id),
        getPriceHistory(id, { size: 100 }),
      ]);
      setProduct(p);
      setHistory([...h.content].reverse());
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const chartData = useMemo(
    () =>
      history
        .filter((h) => h.price != null)
        .map((h) => ({
          t: new Date(h.recordedAt).getTime(),
          label: formatDateTime(h.recordedAt),
          price: Number(h.price),
        })),
    [history],
  );

  async function onReenable() {
    setReenabling(true);
    setError(null);
    try {
      setProduct(await reenableChecks(id));
    } catch (err) {
      setError(err);
    } finally {
      setReenabling(false);
    }
  }

  if (loading && !product) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-1/2" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-56" />
          <Skeleton className="h-56" />
        </div>
        <Skeleton className="h-64" />
      </div>
    );
  }

  if (error && !product) {
    return (
      <div className="space-y-4">
        <ErrorAlert error={error} />
        <Link href="/items">
          <Button variant="secondary">Back to items</Button>
        </Link>
      </div>
    );
  }

  if (!product) return null;

  return (
    <>
      <PageHeader
        title={product.name || "Product"}
        description={
          formatVariant(product)
            ? `${formatVariant(product)} · live snapshot and price samples`
            : "Live product snapshot and recorded price samples."
        }
        actions={
          <Link href="/items">
            <Button variant="secondary" size="sm">
              Back to items
            </Button>
          </Link>
        }
      />

      <ErrorAlert error={error} />

      <div className="mb-4 grid gap-4 lg:mb-6 lg:grid-cols-2">
        <Card>
          <div className="flex flex-col gap-4 sm:flex-row">
            {product.thumbnailUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={product.thumbnailUrl}
                alt=""
                className="mx-auto size-32 shrink-0 rounded-2xl object-cover bg-[var(--surface-muted)] sm:mx-0"
              />
            ) : (
              <div className="mx-auto flex size-32 shrink-0 items-center justify-center rounded-2xl bg-[var(--surface-muted)] text-xs font-semibold text-[var(--muted)] sm:mx-0">
                {product.site}
              </div>
            )}
            <div className="min-w-0 flex-1 space-y-3 text-sm">
              <div className="flex flex-wrap gap-2">
                <Badge tone="neutral">{product.site}</Badge>
                <Badge tone={product.healthy ? "success" : "danger"}>
                  {product.healthy ? "Healthy" : "Unhealthy"}
                </Badge>
                {formatVariant(product) && (
                  <Badge tone="neutral">{formatVariant(product)}</Badge>
                )}
              </div>
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-[var(--muted)]">
                  Current price
                </p>
                <p className="text-3xl font-semibold tabular-nums tracking-tight">
                  {formatPrice(product.lastKnownPrice)}
                </p>
              </div>
              <dl className="space-y-1.5">
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">Variant</dt>
                  <dd className="font-medium text-right">
                    {formatVariant(product) || "—"}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">Stock</dt>
                  <dd className="font-medium text-right">
                    {stockLabel(product.lastKnownStockStatus)}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">Last checked</dt>
                  <dd className="font-medium text-right">
                    {formatDateTime(product.lastCheckedAt)}
                  </dd>
                </div>
              </dl>
              <TextLink href={product.url} external>
                Open on storefront ↗
              </TextLink>
            </div>
          </div>

          {!product.healthy && (
            <div className="mt-5 rounded-xl border border-[var(--warn)]/20 bg-[var(--warn-soft)] p-4">
              <p className="text-sm text-[var(--warn)]">
                Checks paused after repeated scrape failures. Re-enable when the
                site or scraper is healthy again.
              </p>
              <Button
                className="mt-3 w-full sm:w-auto"
                onClick={onReenable}
                disabled={reenabling}
              >
                {reenabling ? (
                  <>
                    <Spinner /> Re-enabling…
                  </>
                ) : (
                  "Re-enable checks"
                )}
              </Button>
            </div>
          )}
        </Card>

        <Card>
          <div className="mb-3">
            <h2 className="font-semibold">Price history</h2>
            <p className="text-xs text-[var(--muted)]">
              Up to 100 latest samples (chronological)
            </p>
          </div>
          {chartData.length < 2 ? (
            <div className="flex h-52 items-center justify-center rounded-xl bg-[var(--surface-muted)]/50 px-4 text-center text-sm text-[var(--muted)]">
              Not enough data points for a chart yet.
            </div>
          ) : (
            <div className="h-52 w-full min-w-0 sm:h-60">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={chartData}
                  margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
                >
                  <CartesianGrid
                    strokeDasharray="3 3"
                    stroke="var(--border)"
                    vertical={false}
                  />
                  <XAxis
                    dataKey="t"
                    type="number"
                    domain={["dataMin", "dataMax"]}
                    tickFormatter={(v) =>
                      new Date(v).toLocaleDateString(undefined, {
                        month: "short",
                        day: "numeric",
                      })
                    }
                    tick={{ fontSize: 11, fill: "var(--muted)" }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: "var(--muted)" }}
                    domain={["auto", "auto"]}
                    width={48}
                    axisLine={false}
                    tickLine={false}
                  />
                  <Tooltip
                    contentStyle={{
                      borderRadius: 12,
                      border: "1px solid var(--border)",
                      background: "var(--surface)",
                      fontSize: 12,
                    }}
                    labelFormatter={(_, payload) =>
                      payload?.[0]?.payload?.label ?? ""
                    }
                    formatter={(value) => [
                      formatPrice(Number(value)),
                      "Price",
                    ]}
                  />
                  <Line
                    type="monotone"
                    dataKey="price"
                    stroke="var(--accent)"
                    strokeWidth={2.5}
                    dot={false}
                    activeDot={{ r: 4 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      </div>

      <Card className="overflow-hidden p-0">
        <div className="border-b border-[var(--border)] px-4 py-3.5 font-semibold sm:px-5">
          History samples
        </div>
        {history.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-[var(--muted)] sm:px-5">
            No history yet.
          </p>
        ) : (
          <div className="table-scroll max-h-80">
            <table className="w-full min-w-[320px] text-left text-sm">
              <thead className="sticky top-0 bg-[var(--surface-muted)] text-xs uppercase tracking-wide text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-2.5 font-semibold sm:px-5">When</th>
                  <th className="px-4 py-2.5 font-semibold sm:px-5">Price</th>
                  <th className="px-4 py-2.5 font-semibold sm:px-5">Stock</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border)]">
                {[...history].reverse().map((row, i) => (
                  <tr
                    key={`${row.recordedAt}-${i}`}
                    className="hover:bg-[var(--surface-muted)]/40"
                  >
                    <td className="whitespace-nowrap px-4 py-2.5 sm:px-5">
                      {formatDateTime(row.recordedAt)}
                    </td>
                    <td className="px-4 py-2.5 tabular-nums font-medium sm:px-5">
                      {formatPrice(row.price)}
                    </td>
                    <td className="px-4 py-2.5 sm:px-5">
                      {stockLabel(row.stockStatus)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}

export default function ProductPage() {
  return (
    <RequireAuth>
      <ProductContent />
    </RequireAuth>
  );
}
