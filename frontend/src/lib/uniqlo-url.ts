/** Parse and normalize Uniqlo colorCode / sizeCode from a product URL. */

export function parseUniqloVariantFromUrl(rawUrl: string): {
  colorCode: string | null;
  sizeCode: string | null;
} {
  try {
    const u = new URL(rawUrl.trim());
    return {
      colorCode: normalizeColorCode(u.searchParams.get("colorCode")),
      sizeCode: normalizeSizeCode(u.searchParams.get("sizeCode")),
    };
  } catch {
    return { colorCode: null, sizeCode: null };
  }
}

/** `09` / `col09` / `COL09` → `COL09` */
export function normalizeColorCode(raw: string | null | undefined): string | null {
  if (raw == null || raw.trim() === "") return null;
  const upper = raw.trim().toUpperCase();
  if (/^\d{1,2}$/.test(upper)) {
    return `COL${upper.padStart(2, "0")}`;
  }
  if (upper.startsWith("COL") && /^\d{1,2}$/.test(upper.slice(3))) {
    return `COL${upper.slice(3).padStart(2, "0")}`;
  }
  return upper.startsWith("COL") ? upper : upper;
}

export function normalizeSizeCode(raw: string | null | undefined): string | null {
  if (raw == null || raw.trim() === "") return null;
  return raw.trim().toUpperCase();
}
