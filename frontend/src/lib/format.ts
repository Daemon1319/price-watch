export function formatPrice(
  value: number | null | undefined,
  currency = "PHP",
): string {
  if (value == null || Number.isNaN(Number(value))) return "—";
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function stockLabel(status: string | null | undefined): string {
  switch (status) {
    case "IN_STOCK":
      return "In stock";
    case "OUT_OF_STOCK":
      return "Out of stock";
    case "UNKNOWN":
      return "Unknown";
    default:
      return "—";
  }
}

/** Human-readable color / size line, e.g. "Black / M". */
export function formatVariant(parts: {
  colorName?: string | null;
  colorCode?: string | null;
  sizeName?: string | null;
  sizeCode?: string | null;
}): string | null {
  const color = parts.colorName?.trim() || parts.colorCode?.trim() || null;
  const size = parts.sizeName?.trim() || parts.sizeCode?.trim() || null;
  if (!color && !size) return null;
  if (color && size) return `${color} / ${size}`;
  return color ?? size;
}

export function colorOptionLabel(code: string, name: string | null | undefined): string {
  return name?.trim() ? `${name} (${code})` : code;
}

export function sizeOptionLabel(code: string, name: string | null | undefined): string {
  return name?.trim() ? `${name} (${code})` : code;
}

export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}
