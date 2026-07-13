"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
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
  deleteTrackedItem,
  getTrackedItem,
  updateTrackedItem,
} from "@/lib/api/tracked-items";
import {
  cn,
  formatDateTime,
  formatPrice,
  formatVariant,
  stockLabel,
} from "@/lib/format";
import type { TrackedItem, TrackedItemStatus } from "@/lib/types";
import { useRetryCooldown } from "@/lib/hooks/use-retry-cooldown";

function ItemDetailContent() {
  const params = useParams();
  const id = String(params.id);
  const router = useRouter();

  const [item, setItem] = useState<TrackedItem | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  const [threshold, setThreshold] = useState("");
  const [restockOnly, setRestockOnly] = useState(false);
  const [status, setStatus] = useState<TrackedItemStatus>("ACTIVE");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [saveOk, setSaveOk] = useState(false);
  const [deleting, setDeleting] = useState(false);
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
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (saveBlocked) return;
    // Only clear prior error when present — avoids an extra re-render flash.
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

  if (loading && !item) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-2/3" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
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
          <>
            <Link href="/items" className="hidden sm:block">
              <Button variant="ghost" size="sm">
                Back
              </Button>
            </Link>
            <Link href={`/products/${item.productId}`}>
              <Button variant="secondary" size="sm">
                History
              </Button>
            </Link>
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-2 lg:gap-6">
        <Card>
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
                  // Stable width so Saving… / Saved doesn’t resize the control.
                  "w-full min-w-[10.5rem] sm:w-auto",
                  // Busy: block double-submit without opacity/scale flash on disable.
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
