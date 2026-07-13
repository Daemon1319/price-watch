"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { RequireAuth } from "@/components/auth/require-auth";
import {
  Badge,
  Button,
  Card,
  Checkbox,
  EmptyState,
  ErrorAlert,
  FilterChip,
  Input,
  Label,
  PageHeader,
  Select,
  Skeleton,
  Spinner,
} from "@/components/ui";
import { listProductVariants } from "@/lib/api/products";
import {
  createTrackedItem,
  listTrackedItems,
} from "@/lib/api/tracked-items";
import {
  colorOptionLabel,
  formatPrice,
  formatVariant,
  sizeOptionLabel,
  stockLabel,
} from "@/lib/format";
import { parseUniqloVariantFromUrl } from "@/lib/uniqlo-url";
import type {
  ProductVariantOption,
  ProductVariantsResponse,
  TrackedItem,
  TrackedItemStatus,
} from "@/lib/types";
import { useRetryCooldown } from "@/lib/hooks/use-retry-cooldown";

/** Prefer codes from the product URL; fall back to single-color auto-pick. */
function pickVariantSelection(
  res: ProductVariantsResponse,
  rawUrl: string,
): { colorCode: string; sizeCode: string } {
  const fromUrl = parseUniqloVariantFromUrl(rawUrl);
  const colorCodes = new Set(res.colors.map((c) => c.code));
  const sizeCodes = new Set(res.sizes.map((s) => s.code));

  let color = "";
  if (fromUrl.colorCode && colorCodes.has(fromUrl.colorCode)) {
    color = fromUrl.colorCode;
  } else if (res.colors.length === 1) {
    color = res.colors[0].code;
  }

  let size = "";
  if (fromUrl.sizeCode) {
    const sizeValidForColor =
      !color ||
      res.variants.some(
        (v) => v.colorCode === color && v.sizeCode === fromUrl.sizeCode,
      );
    if (sizeValidForColor && sizeCodes.has(fromUrl.sizeCode)) {
      size = fromUrl.sizeCode;
    }
  }

  return { colorCode: color, sizeCode: size };
}

function ItemsContent() {
  const [items, setItems] = useState<TrackedItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<TrackedItemStatus | "ALL">(
    "ALL",
  );
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  const [url, setUrl] = useState("");
  const [threshold, setThreshold] = useState("");
  const [restockOnly, setRestockOnly] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);
  const [formOpen, setFormOpen] = useState(true);
  const createCooldown = useRetryCooldown(createError);
  const createBlocked = creating || createCooldown > 0;

  const [variants, setVariants] = useState<ProductVariantsResponse | null>(null);
  const [variantsLoading, setVariantsLoading] = useState(false);
  const [variantsError, setVariantsError] = useState<unknown>(null);
  const [colorCode, setColorCode] = useState("");
  const [sizeCode, setSizeCode] = useState("");

  const size = 20;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listTrackedItems({
        page,
        size,
        status: statusFilter === "ALL" ? undefined : [statusFilter],
      });
      setItems(res.content);
      setTotal(res.totalElements);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const sizesForColor = useMemo(() => {
    if (!variants) return [];
    if (!colorCode) return variants.sizes;
    const codes = new Set(
      variants.variants
        .filter((v) => v.colorCode === colorCode)
        .map((v) => v.sizeCode),
    );
    return variants.sizes.filter((s) => codes.has(s.code));
  }, [variants, colorCode]);

  const selectedSku: ProductVariantOption | null = useMemo(() => {
    if (!variants || !colorCode || !sizeCode) return null;
    return (
      variants.variants.find(
        (v) => v.colorCode === colorCode && v.sizeCode === sizeCode,
      ) ?? null
    );
  }, [variants, colorCode, sizeCode]);

  const urlVariant = useMemo(() => parseUniqloVariantFromUrl(url), [url]);
  const canTrack = !!(colorCode || urlVariant.colorCode) && !!(sizeCode || urlVariant.sizeCode);

  function resetVariantState() {
    setVariants(null);
    setVariantsError(null);
    setColorCode("");
    setSizeCode("");
  }

  async function loadVariantsFor(rawUrl: string) {
    const trimmed = rawUrl.trim();
    if (!trimmed) {
      setVariantsError(new Error("Paste a product URL first"));
      return;
    }
    setVariantsLoading(true);
    setVariantsError(null);
    try {
      const res = await listProductVariants(trimmed);
      setVariants(res);
      const picked = pickVariantSelection(res, trimmed);
      setColorCode(picked.colorCode);
      setSizeCode(picked.sizeCode);
    } catch (err) {
      setVariants(null);
      setColorCode("");
      setSizeCode("");
      setVariantsError(err);
    } finally {
      setVariantsLoading(false);
    }
  }

  function onLoadVariants() {
    void loadVariantsFor(url);
  }

  // When the pasted URL already includes colorCode+sizeCode, load and preselect.
  useEffect(() => {
    const trimmed = url.trim();
    if (!trimmed) return;
    const { colorCode: c, sizeCode: s } = parseUniqloVariantFromUrl(trimmed);
    if (!c || !s) return;

    const handle = window.setTimeout(() => {
      void loadVariantsFor(trimmed);
    }, 350);
    return () => window.clearTimeout(handle);
    // Only re-run when the URL string changes (not when variants load).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    if (createBlocked) return;
    setCreateError(null);

    const fromUrl = parseUniqloVariantFromUrl(url);
    const finalColor = colorCode || fromUrl.colorCode || "";
    const finalSize = sizeCode || fromUrl.sizeCode || "";

    if (!finalColor || !finalSize) {
      setCreateError(
        new Error(
          "Choose a color and size first (Load colors & sizes after pasting the URL, or paste a URL that already has colorCode and sizeCode).",
        ),
      );
      return;
    }

    setCreating(true);
    try {
      const priceThreshold =
        threshold.trim() === "" ? null : Number(threshold);
      if (priceThreshold != null && Number.isNaN(priceThreshold)) {
        throw new Error("Minimum drop amount must be a number");
      }
      if (priceThreshold != null && priceThreshold < 0) {
        throw new Error("Minimum drop amount cannot be negative");
      }
      await createTrackedItem({
        url: url.trim(),
        colorCode: finalColor,
        sizeCode: finalSize,
        priceThreshold,
        notifyOnRestockOnly: restockOnly,
      });
      setUrl("");
      setThreshold("");
      setRestockOnly(false);
      resetVariantState();
      setPage(0);
      await load();
    } catch (err) {
      setCreateError(err);
    } finally {
      setCreating(false);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / size));

  return (
    <>
      <PageHeader
        title="Tracked items"
        description="Paste a Uniqlo URL (colorCode/sizeCode in the link are preselected). Optional minimum drop amount filters price-drop alerts."
        actions={
          <Button
            variant="secondary"
            size="sm"
            className="sm:hidden"
            onClick={() => setFormOpen((o) => !o)}
          >
            {formOpen ? "Hide form" : "Track new"}
          </Button>
        }
      />

      <Card className={formOpen ? "mb-6" : "mb-6 hidden sm:block"}>
        <div className="mb-4">
          <h2 className="font-semibold">Track a product</h2>
          <p className="mt-0.5 text-xs text-[var(--muted)]">
            Uniqlo only · tracking is per color + size. URLs with{" "}
            <code className="text-[0.7rem]">colorCode</code> &amp;{" "}
            <code className="text-[0.7rem]">sizeCode</code> auto-load and
            preselect.
          </p>
        </div>
        <form onSubmit={onCreate} className="space-y-4">
          <div>
            <Label htmlFor="url">Product URL</Label>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input
                id="url"
                type="url"
                required
                inputMode="url"
                autoComplete="url"
                placeholder="https://www.uniqlo.com/ph/en/products/..."
                value={url}
                onChange={(e) => {
                  setUrl(e.target.value);
                  resetVariantState();
                }}
                className="sm:flex-1"
              />
              <Button
                type="button"
                variant="secondary"
                disabled={variantsLoading || !url.trim()}
                onClick={() => void onLoadVariants()}
                className="w-full shrink-0 sm:w-auto"
              >
                {variantsLoading ? (
                  <>
                    <Spinner /> Loading…
                  </>
                ) : (
                  "Load colors & sizes"
                )}
              </Button>
            </div>
          </div>

          <ErrorAlert error={variantsError} />

          {variants && (
            <div className="space-y-3 rounded-xl border border-[var(--border)] bg-[var(--surface-muted)]/40 p-3 sm:p-4">
              {variants.productName && (
                <p className="text-sm font-medium">{variants.productName}</p>
              )}
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="color">Color</Label>
                  <Select
                    id="color"
                    required
                    value={colorCode}
                    onChange={(e) => {
                      setColorCode(e.target.value);
                      setSizeCode("");
                    }}
                  >
                    <option value="">Select color…</option>
                    {variants.colors.map((c) => (
                      <option key={c.code} value={c.code}>
                        {colorOptionLabel(c.code, c.name)}
                      </option>
                    ))}
                  </Select>
                </div>
                <div>
                  <Label htmlFor="size">Size</Label>
                  <Select
                    id="size"
                    required
                    value={sizeCode}
                    disabled={!colorCode}
                    onChange={(e) => setSizeCode(e.target.value)}
                  >
                    <option value="">
                      {colorCode ? "Select size…" : "Pick a color first"}
                    </option>
                    {sizesForColor.map((s) => (
                      <option key={s.code} value={s.code}>
                        {sizeOptionLabel(s.code, s.name)}
                      </option>
                    ))}
                  </Select>
                </div>
              </div>

              {selectedSku && (
                <div className="flex flex-wrap items-center gap-3 text-sm">
                  {selectedSku.thumbnailUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={selectedSku.thumbnailUrl}
                      alt=""
                      className="size-12 rounded-lg object-cover bg-[var(--surface)]"
                    />
                  ) : null}
                  <div className="min-w-0">
                    <p className="font-medium">
                      {formatVariant({
                        colorName: selectedSku.colorName,
                        colorCode: selectedSku.colorCode,
                        sizeName: selectedSku.sizeName,
                        sizeCode: selectedSku.sizeCode,
                      })}
                    </p>
                    <p className="text-xs text-[var(--muted)]">
                      {formatPrice(selectedSku.price)} ·{" "}
                      {stockLabel(selectedSku.stockStatus)}
                    </p>
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="threshold">
                Min. price drop to notify (optional)
              </Label>
              <Input
                id="threshold"
                type="number"
                min={0}
                step="0.01"
                inputMode="decimal"
                placeholder="e.g. 200 — leave empty for any drop"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
              />
              <p className="mt-1.5 text-xs text-[var(--muted)]">
                Not a target price. Example: 200 means “email me only if the
                price fell by at least 200.”
              </p>
            </div>
            <div className="flex items-end">
              <Checkbox
                label="Notify on restock only"
                checked={restockOnly}
                onChange={(e) => setRestockOnly(e.target.checked)}
              />
            </div>
          </div>
          <ErrorAlert error={createError} />
          <Button
            type="submit"
            disabled={createBlocked || !canTrack}
            className="w-full sm:w-auto"
          >
            {creating ? (
              <>
                <Spinner /> Tracking…
              </>
            ) : createCooldown > 0 ? (
              `Try again in ${createCooldown}s`
            ) : (
              "Start tracking"
            )}
          </Button>
        </form>
      </Card>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        {(["ALL", "ACTIVE", "PAUSED"] as const).map((s) => (
          <FilterChip
            key={s}
            active={statusFilter === s}
            onClick={() => {
              setStatusFilter(s);
              setPage(0);
            }}
          >
            {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
          </FilterChip>
        ))}
        <span className="ml-auto text-xs text-[var(--muted)] tabular-nums">
          {total} total
        </span>
      </div>

      <ErrorAlert error={error} />

      {loading && items.length === 0 ? (
        <div className="space-y-2">
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
        </div>
      ) : items.length === 0 ? (
        <Card className="p-0">
          <EmptyState
            title="Nothing tracked yet"
            description="Add a Uniqlo URL, pick color and size, then start tracking."
          />
        </Card>
      ) : (
        <Card className="overflow-hidden p-0">
          <ul className="divide-y divide-[var(--border)]">
            {items.map((item) => {
              const variant = formatVariant(item);
              return (
                <li key={item.id}>
                  <Link
                    href={`/items/${item.id}`}
                    className="flex items-center gap-3 px-4 py-3.5 transition-colors hover:bg-[var(--surface-muted)]/60 sm:gap-4 sm:px-5 sm:py-4"
                  >
                    {item.thumbnailUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={item.thumbnailUrl}
                        alt=""
                        className="size-14 shrink-0 rounded-xl object-cover bg-[var(--surface-muted)] sm:size-16"
                      />
                    ) : (
                      <div className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-[var(--surface-muted)] text-[0.65rem] font-semibold text-[var(--muted)] sm:size-16">
                        {item.site}
                      </div>
                    )}
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium leading-snug">
                        {item.productName || item.url}
                      </p>
                      <p className="mt-0.5 truncate text-xs text-[var(--muted)]">
                        {variant ? `${variant} · ` : ""}
                        {item.site} · {stockLabel(item.lastKnownStockStatus)}
                      </p>
                      <div className="mt-2 flex flex-wrap items-center gap-2 sm:hidden">
                        <span className="text-sm font-semibold tabular-nums">
                          {formatPrice(item.lastKnownPrice)}
                        </span>
                        <Badge
                          tone={item.status === "ACTIVE" ? "success" : "neutral"}
                        >
                          {item.status}
                        </Badge>
                      </div>
                    </div>
                    <div className="hidden shrink-0 text-right sm:block">
                      <p className="font-semibold tabular-nums">
                        {formatPrice(item.lastKnownPrice)}
                      </p>
                      <p className="text-xs text-[var(--muted)]">
                        {stockLabel(item.lastKnownStockStatus)}
                      </p>
                    </div>
                    <Badge
                      className="hidden sm:inline-flex"
                      tone={item.status === "ACTIVE" ? "success" : "neutral"}
                    >
                      {item.status}
                    </Badge>
                    <span
                      className="hidden text-[var(--muted)] sm:inline"
                      aria-hidden
                    >
                      →
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </Card>
      )}

      {totalPages > 1 && (
        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-[var(--muted)]">
            Page {page + 1} of {totalPages}
          </span>
          <div className="grid grid-cols-2 gap-2 sm:flex">
            <Button
              variant="secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="w-full sm:w-auto"
            >
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="w-full sm:w-auto"
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

export default function ItemsPage() {
  return (
    <RequireAuth>
      <ItemsContent />
    </RequireAuth>
  );
}
