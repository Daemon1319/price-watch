/** Parse and normalize Uniqlo color/size from product URLs (old + new formats). */

/**
 * New storefront URLs look like:
 *   /ph/en/products/E475367-000/00?colorDisplayCode=18&sizeDisplayCode=005
 * Legacy:
 *   /ph/en/products/E475367-000?colorCode=COL18&sizeCode=SMA005
 */
export function parseUniqloVariantFromUrl(rawUrl: string): {
  colorCode: string | null;
  sizeCode: string | null;
  /** Bare display digits from colorDisplayCode when present (e.g. "18"). */
  colorDisplayCode: string | null;
  /** Bare display digits from sizeDisplayCode when present (e.g. "005"). */
  sizeDisplayCode: string | null;
} {
  try {
    const u = new URL(rawUrl.trim());
    const colorDisplayCode = normalizeDisplayDigits(
      u.searchParams.get("colorDisplayCode"),
      2,
    );
    const sizeDisplayCode = normalizeDisplayDigits(
      u.searchParams.get("sizeDisplayCode"),
      3,
    );

    const colorFromCode = normalizeColorCode(u.searchParams.get("colorCode"));
    const sizeFromCode = normalizeSizeCode(u.searchParams.get("sizeCode"));

    // Prefer full API codes; fall back to mapping display codes.
    const colorCode =
      colorFromCode ??
      (colorDisplayCode ? normalizeColorCode(colorDisplayCode) : null);
    const sizeCode =
      sizeFromCode ??
      (sizeDisplayCode ? sizeCodeFromDisplay(sizeDisplayCode) : null);

    return {
      colorCode,
      sizeCode,
      colorDisplayCode,
      sizeDisplayCode,
    };
  } catch {
    return {
      colorCode: null,
      sizeCode: null,
      colorDisplayCode: null,
      sizeDisplayCode: null,
    };
  }
}

/** True if the URL looks like a Uniqlo product page (enough to load variants). */
export function isUniqloProductUrl(rawUrl: string): boolean {
  try {
    const u = new URL(rawUrl.trim());
    const host = u.hostname.toLowerCase();
    if (!(host === "uniqlo.com" || host.endsWith(".uniqlo.com"))) {
      return false;
    }
    // /ph/en/products/E422992-000 or .../E422992-000/00 (price group suffix)
    return /\/[a-z]{2}\/[a-z]{2}\/products\/[A-Za-z0-9-]+/i.test(u.pathname);
  } catch {
    return false;
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
  const upper = raw.trim().toUpperCase();
  if (/^(SMA|INS|SMW|SMB)\d+$/i.test(upper)) {
    return upper;
  }
  // Bare display digits (005, 28) → best-effort full size code.
  if (/^\d{1,3}$/.test(upper)) {
    return sizeCodeFromDisplay(upper);
  }
  return upper;
}

/**
 * Map size display digits to a full size code.
 * Apparel chips are usually SMA### (002–010); larger numbers are inch sizes INS###.
 */
export function sizeCodeFromDisplay(raw: string | null | undefined): string | null {
  const digits = normalizeDisplayDigits(raw, 3);
  if (!digits) return null;
  const n = Number.parseInt(digits, 10);
  if (Number.isNaN(n)) return null;
  // Waist/inseam-style displays are typically 20+; apparel size chips are small.
  if (n >= 20) {
    return `INS${digits}`;
  }
  return `SMA${digits}`;
}

function normalizeDisplayDigits(
  raw: string | null | undefined,
  pad: number,
): string | null {
  if (raw == null || raw.trim() === "") return null;
  const digits = raw.trim().replace(/\D/g, "");
  if (!digits) return null;
  return digits.padStart(pad, "0").slice(-Math.max(pad, digits.length));
}
