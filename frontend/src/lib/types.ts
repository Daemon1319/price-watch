export type Site = "UNIQLO";

export type StockStatus = "IN_STOCK" | "OUT_OF_STOCK" | "UNKNOWN";

export type TrackedItemStatus = "ACTIVE" | "PAUSED";

/** Access JWT + opaque refresh (also set as HttpOnly cookie when same-site). */
export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  /** Present so cross-origin SPAs can refresh when cookies are not sent. */
  refreshToken?: string | null;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  fieldErrors?: Record<string, string>;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface TrackedItem {
  id: string;
  productId: string;
  productName: string | null;
  url: string;
  site: Site;
  lastKnownPrice: number | null;
  lastKnownStockStatus: StockStatus | null;
  thumbnailUrl: string | null;
  /** Uniqlo color code, e.g. COL09 */
  colorCode: string | null;
  colorName: string | null;
  /** Uniqlo size code, e.g. SMA004 or INS029 */
  sizeCode: string | null;
  sizeName: string | null;
  priceThreshold: number | null;
  notifyOnRestockOnly: boolean;
  status: TrackedItemStatus;
  createdAt: string;
}

export interface CreateTrackedItemRequest {
  url: string;
  /** Required for Uniqlo — e.g. COL09 or bare 09 */
  colorCode?: string | null;
  /** Required for Uniqlo — e.g. SMA004 (M) or INS029 (29") */
  sizeCode?: string | null;
  priceThreshold?: number | null;
  notifyOnRestockOnly: boolean;
}

export interface UpdateTrackedItemRequest {
  priceThreshold?: number | null;
  notifyOnRestockOnly?: boolean;
  status?: TrackedItemStatus;
}

export interface Product {
  id: string;
  name: string | null;
  url: string;
  site: Site;
  lastKnownPrice: number | null;
  lastKnownStockStatus: StockStatus | null;
  thumbnailUrl: string | null;
  colorCode: string | null;
  colorName: string | null;
  sizeCode: string | null;
  sizeName: string | null;
  lastCheckedAt: string | null;
  healthy: boolean;
}

/** GET /api/v1/products/variants?url=… — pick a SKU before tracking. */
export interface ProductVariantsResponse {
  productName: string | null;
  productId: string | null;
  baseUrl: string;
  colors: ColorOption[];
  sizes: SizeOption[];
  variants: ProductVariantOption[];
}

export interface ColorOption {
  code: string;
  name: string | null;
  displayCode: string | null;
}

export interface SizeOption {
  code: string;
  name: string | null;
  displayCode: string | null;
}

export interface ProductVariantOption {
  colorCode: string;
  colorName: string | null;
  sizeCode: string;
  sizeName: string | null;
  price: number | null;
  stockStatus: StockStatus | null;
  thumbnailUrl: string | null;
}

export interface PriceHistoryPoint {
  price: number | null;
  stockStatus: StockStatus | null;
  recordedAt: string;
}

export interface DashboardSummary {
  totalTrackedItems: number;
  recentPriceDrops: PriceDropEntry[];
  unhealthyCount: number;
}

export interface PriceDropEntry {
  trackedItemId: string;
  productName: string | null;
  oldPrice: number;
  newPrice: number;
  changedAt: string;
}
