"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
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
  Checkbox,
  ErrorAlert,
  Input,
  Label,
  PageHeader,
  Select,
  Skeleton,
  Spinner,
  TextLink,
} from "@/components/ui";
import {
  getPriceHistory,
  reenableChecks,
  requestProductCheck,
} from "@/lib/api/products";
import {
  deleteTrackedItem,
  getTrackedItem,
  updateTrackedItem,
} from "@/lib/api/tracked-items";
import {
  cn,
  formatDateTime,
  formatPrice,
  formatVariant,
  scrapeFailureLabel,
  stockLabel,
} from "@/lib/format";
import type {
  PriceHistoryPoint,
  TrackedItem,
  TrackedItemStatus,
} from "@/lib/types";
import { useRetryCooldown } from "@/lib/hooks/use-retry-cooldown";

function ItemDetailContent() {
  const params = useParams();
  const id = String(params.id);
  const router = useRouter();

  const [item, setItem] = useState<TrackedItem | null>(null);
  const [history, setHistory] = useState<PriceHistoryPoint[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  const [threshold, setThreshold] = useState("");
  const [restockOnly, setRestockOnly] = useState(false);
  const [status, setStatus] = useState<TrackedItemStatus>("ACTIVE");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [saveOk, setSaveOk] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [reenabling, setReenabling] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkHint, setCheckHint] = useState<string | null>(null);
  const saveCooldown = useRetryCooldown(saveError);
  const saveBlocked = saving || saveCooldown > 0;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getTrackedItem(id);
      setItem(data);
      setThreshold(
        data.priceThreshold != null ? String(data.priceThreshold) : "",
      );
      setRestockOnly(data.notifyOnRestockOnly);
      setStatus(data.status);
      try {
        const h = await getPriceHistory(data.productId, { size: 100 });
        setHistory([...h.content].reverse());
      } catch {
        setHistory([]);
      }
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

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (saveBlocked) return;
    if (saveError != null) setSaveError(null);
    setSaveOk(false);
    setSaving(true);
    try {
      const priceThreshold =
        threshold.trim() === "" ? undefined : Number(threshold);
      if (priceThreshold != null && Number.isNaN(priceThreshold)) {
        throw new Error("Minimum drop amount must be a number");
      }
      if (priceThreshold != null && priceThreshold < 0) {
        throw new Error("Minimum drop amount cannot be negative");
      }
      const updated = await updateTrackedItem(id, {
        priceThreshold,
        notifyOnRestockOnly: restockOnly,
        status,
      });
      setItem(updated);
      setSaveOk(true);
    } catch (err) {
      setSaveError(err);
      setSaveOk(false);
    } finally {
      setSaving(false);
    }
  }

  function markFormDirty() {
    if (saveOk) setSaveOk(false);
  }

  async function onDelete() {
    if (!confirm("Stop tracking this product? This cannot be undone.")) return;
    setDeleting(true);
    try {
      await deleteTrackedItem(id);
      router.replace("/items");
    } catch (err) {
      setSaveError(err);
      setDeleting(false);
    }
  }

  async function onReenable() {
    if (!item) return;
    setReenabling(true);
    setError(null);
    try {
      const p = await reenableChecks(item.productId);
      setItem((prev) =>
        prev
          ? {
              ...prev,
              healthy: p.healthy,
              lastFailureReason: p.lastFailureReason,
              lastFailureDetail: p.lastFailureDetail,
              lastCheckedAt: p.lastCheckedAt,
            }
          : prev,
      );
      setCheckHint(
        "Checks re-enabled. Use Refresh now or wait for the next schedule.",
      );
    } catch (err) {
      setError(err);
    } finally {
      setReenabling(false);
    }
  }

  async function onCheckNow() {
    if (!item) return;
    setChecking(true);
    setError(null);
    setCheckHint(null);
    try {
      await requestProductCheck(item.productId);
      setCheckHint(
        "Check queued. Price and image update in a few seconds.",
      );
      window.setTimeout(() => {
        void load();
      }, 4000);
    } catch (err) {
      setError(err);
    } finally {
      setChecking(false);
    }
  }

  if (loading && !item) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-2/3" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
        <Skeleton className="h-64" />
      </div>
    );
  }

  if (error && !item) {
    return (
      <div className="space-y-4">
        <ErrorAlert error={error} />
        <Link href="/items">
          <Button variant="secondary">Back to items</Button>
        </Link>
      </div>
    );
  }

  if (!item) return null;

  const unhealthy = item.healthy === false;

  return (
    <>
      <PageHeader
        title={item.productName || "Tracked item"}
        description={
          formatVariant(item)
            ? `${formatVariant(item)} · Uniqlo SKU`
            : item.url
        }
        actions={
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={onCheckNow}
              disabled={checking || loading}
            >
              {checking ? (
                <>
                  <Spinner /> Queuing…
                </>
              ) : (
                "Refresh now"
              )}
            </Button>
            <Link href="/items" className="hidden sm:block">
              <Button variant="ghost" size="sm">
                Back
              </Button>
            </Link>
          </div>
        }
      />

      <ErrorAlert error={error} />
      {checkHint && (
        <p className="mb-4 text-sm text-[var(--muted)]">{checkHint}</p>
      )}

      <div className="mb-4 grid gap-4 lg:mb-6 lg:grid-cols-2 lg:gap-6">
        <Card
          className={
            unhealthy
              ? "border-[var(--danger)]/30 ring-1 ring-[var(--danger)]/15"
              : undefined
          }
        >
          <div className="flex flex-col gap-4 sm:flex-row">
            {item.thumbnailUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={item.thumbnailUrl}
                alt=""
                className="mx-auto size-28 shrink-0 rounded-2xl object-cover bg-[var(--surface-muted)] sm:mx-0 sm:size-32"
              />
            ) : (
              <div className="mx-auto flex size-28 shrink-0 items-center justify-center rounded-2xl bg-[var(--surface-muted)] text-xs font-semibold text-[var(--muted)] sm:mx-0 sm:size-32">
                {item.site}
              </div>
            )}
            <div className="min-w-0 flex-1 space-y-3 text-sm">
              <div className="flex flex-wrap gap-2">
                <Badge tone="neutral">{item.site}</Badge>
                <Badge tone={item.status === "ACTIVE" ? "success" : "warn"}>
                  {item.status}
                </Badge>
                {unhealthy ? (
                  <Badge tone="danger">Unhealthy</Badge>
                ) : (
                  <Badge tone="success">Healthy</Badge>
                )}
                {formatVariant(item) && (
                  <Badge tone="neutral">{formatVariant(item)}</Badge>
                )}
              </div>
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-[var(--muted)]">
                  Last price
                </p>
                <p className="text-2xl font-semibold tabular-nums">
                  {formatPrice(item.lastKnownPrice)}
                </p>
              </div>
              <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-[var(--muted)]">Stock</dt>
                  <dd className="font-medium">
                    {stockLabel(item.lastKnownStockStatus)}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">Last checked</dt>
                  <dd className="font-medium">
                    {formatDateTime(item.lastCheckedAt)}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">Color</dt>
                  <dd className="font-medium">
                    {item.colorName || item.colorCode || "—"}
                    {item.colorName && item.colorCode
                      ? ` (${item.colorCode})`
                      : ""}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">Size</dt>
                  <dd className="font-medium">
                    {item.sizeName || item.sizeCode || "—"}
                    {item.sizeName && item.sizeCode
                      ? ` (${item.sizeCode})`
                      : ""}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">Tracked since</dt>
                  <dd className="font-medium">
                    {formatDateTime(item.createdAt)}
                  </dd>
                </div>
              </dl>
              <TextLink href={item.url} external>
                Open on storefront ↗
              </TextLink>
            </div>
          </div>

          {unhealthy && (
            <div className="mt-5 rounded-xl border border-[var(--danger)]/25 bg-[var(--danger-soft)] p-4">
              <p className="text-sm font-medium text-[var(--danger)]">
                {scrapeFailureLabel(item.lastFailureReason)}
              </p>
              {item.lastFailureDetail && (
                <p className="mt-1 text-xs text-[var(--muted)]">
                  {item.lastFailureDetail}
                </p>
              )}
              {(item.lastFailureReason === "VARIANT_MISSING" ||
                item.lastFailureReason === "PRODUCT_UNAVAILABLE") && (
                <p className="mt-2 text-xs text-[var(--muted)]">
                  Re-enable won’t bring this SKU back if Uniqlo removed it.
                  Track a different color/size, or delete this item.
                </p>
              )}
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
          <h2 className="mb-1 font-semibold">Preferences</h2>
          <p className="mb-4 text-xs text-[var(--muted)]">
            Pause tracking or set when you want to be notified.
          </p>
          <form onSubmit={onSave} className="space-y-4">
            <div>
              <Label htmlFor="threshold">Min. price drop to notify</Label>
              <Input
                id="threshold"
                type="number"
                min={0}
                step="0.01"
                inputMode="decimal"
                placeholder="Leave blank to keep current"
                value={threshold}
                onChange={(e) => {
                  setThreshold(e.target.value);
                  markFormDirty();
                }}
              />
              <p className="mt-1.5 text-xs text-[var(--muted)]">
                Minimum drop amount (not a target price). Leave blank to keep the
                current value; clearing to “no threshold” isn’t supported yet.
              </p>
            </div>
            <div>
              <Label htmlFor="status">Status</Label>
              <Select
                id="status"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value as TrackedItemStatus);
                  markFormDirty();
                }}
              >
                <option value="ACTIVE">Active</option>
                <option value="PAUSED">Paused</option>
              </Select>
            </div>
            <Checkbox
              label="Notify on restock only"
              checked={restockOnly}
              onChange={(e) => {
                setRestockOnly(e.target.checked);
                markFormDirty();
              }}
            />
            <ErrorAlert error={saveError} />
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button
                type="submit"
                disabled={saveBlocked}
                aria-busy={saving}
                className={cn(
                  "w-full min-w-[10.5rem] sm:w-auto",
                  saving &&
                    "disabled:opacity-100 disabled:cursor-wait active:scale-100",
                )}
              >
                <span className="inline-flex min-h-5 min-w-[7.5rem] items-center justify-center gap-2">
                  {saving ? (
                    <>
                      <Spinner />
                      Saving…
                    </>
                  ) : saveCooldown > 0 ? (
                    `Try again in ${saveCooldown}s`
                  ) : saveOk ? (
                    "Saved"
                  ) : (
                    "Save changes"
                  )}
                </span>
              </Button>
              <Button
                type="button"
                variant="danger"
                disabled={deleting}
                onClick={onDelete}
                className="w-full sm:w-auto"
              >
                {deleting ? "Deleting…" : "Delete"}
              </Button>
            </div>
          </form>
        </Card>
      </div>

      <div className="mb-4 grid gap-4 lg:grid-cols-2 lg:gap-6">
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
      </div>

      <div className="mt-4 sm:hidden">
        <Link href="/items">
          <Button variant="ghost" className="w-full">
            ← Back to items
          </Button>
        </Link>
      </div>
    </>
  );
}

export default function ItemDetailPage() {
  return (
    <RequireAuth>
      <ItemDetailContent />
    </RequireAuth>
  );
}
