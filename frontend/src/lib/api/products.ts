import { apiFetch } from "@/lib/api/client";
import type {
  PageResponse,
  PriceHistoryPoint,
  Product,
  ProductVariantsResponse,
} from "@/lib/types";

export function getProduct(id: string) {
  return apiFetch<Product>(`/api/v1/products/${id}`);
}

/** Lists Uniqlo color/size SKUs for a product URL (no product row created). */
export function listProductVariants(url: string) {
  const q = new URLSearchParams({ url });
  return apiFetch<ProductVariantsResponse>(
    `/api/v1/products/variants?${q.toString()}`,
  );
}

export function getPriceHistory(
  id: string,
  params?: { page?: number; size?: number; from?: string; to?: string },
) {
  const q = new URLSearchParams();
  if (params?.page != null) q.set("page", String(params.page));
  if (params?.size != null) q.set("size", String(params.size));
  if (params?.from) q.set("from", params.from);
  if (params?.to) q.set("to", params.to);
  const qs = q.toString();
  return apiFetch<PageResponse<PriceHistoryPoint>>(
    `/api/v1/products/${id}/price-history${qs ? `?${qs}` : ""}`,
  );
}

export function reenableChecks(id: string) {
  return apiFetch<Product>(`/api/v1/products/${id}/reenable-checks`, {
    method: "POST",
  });
}
