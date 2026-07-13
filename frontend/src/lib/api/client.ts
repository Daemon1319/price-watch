import {
  applyAuthSession,
  clearAccessToken,
  clearLegacyTokenStorage,
  getAccessToken,
  getRefreshToken,
} from "@/lib/auth/session";
import type { LoginResponse, ProblemDetail } from "@/lib/types";

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ||
  "http://localhost:8080";

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;
  /** Parsed from Retry-After when present (seconds). */
  readonly retryAfterSeconds?: number;

  constructor(
    status: number,
    problem: ProblemDetail,
    retryAfterSeconds?: number,
  ) {
    super(problem.detail || problem.title || `Request failed (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  auth?: boolean;
  /** Skip 401 refresh retry (used by refresh itself). */
  skipRefresh?: boolean;
};

let refreshPromise: Promise<boolean> | null = null;

/**
 * New access JWT via body refresh token (and cookie when same-site).
 * Dedupes concurrent callers so a rotated token is not reused (double-refresh race).
 */
export async function tryRefresh(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const storedRefresh = getRefreshToken();
    // Cross-origin (Vercel → local API): cookie is not sent; body token is required.
    if (!storedRefresh) {
      return false;
    }

    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({ refreshToken: storedRefresh }),
      });

      if (!res.ok) {
        // 401 on refresh usually means revoked/expired token. Do not clear on
        // every 401 if another tab may have just rotated — re-read storage once.
        if (res.status === 401 || res.status === 403) {
          const latest = getRefreshToken();
          if (latest && latest !== storedRefresh) {
            // Another tab rotated; retry once with the newer token.
            const retry = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
              method: "POST",
              headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
              },
              credentials: "include",
              body: JSON.stringify({ refreshToken: latest }),
            });
            if (retry.ok) {
              const data = (await retry.json()) as LoginResponse;
              applyAuthSession(
                data.accessToken,
                data.expiresIn,
                data.refreshToken ?? latest,
              );
              return true;
            }
          }
          clearAccessToken();
        }
        return false;
      }

      const data = (await res.json()) as LoginResponse;
      if (!data.accessToken) {
        return false;
      }
      // Server rotates refresh — must store the new one
      applyAuthSession(
        data.accessToken,
        data.expiresIn,
        data.refreshToken ?? storedRefresh,
      );
      return true;
    } catch {
      // Network blip (backend sleeping / offline): keep stored tokens for retry
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

function parseRetryAfter(header: string | null): number | undefined {
  if (header == null || header.trim() === "") return undefined;
  const asInt = Number.parseInt(header.trim(), 10);
  if (!Number.isNaN(asInt) && asInt >= 0) return asInt;
  return undefined;
}

async function parseError(res: Response): Promise<ApiError> {
  let problem: ProblemDetail = {
    title: res.statusText || "Error",
    status: res.status,
  };
  try {
    const json = (await res.json()) as ProblemDetail;
    problem = { ...problem, ...json };
  } catch {
    // non-JSON body
  }
  if (!problem.detail) {
    problem.detail = problem.title || `HTTP ${res.status}`;
  }
  const retryAfterSeconds = parseRetryAfter(res.headers.get("Retry-After"));
  return new ApiError(res.status, problem, retryAfterSeconds);
}

/**
 * Browser-side API helper.
 * - Access JWT: Authorization Bearer (localStorage)
 * - Refresh: body token (cross-site) + cookie when available
 * - On 401: one refresh attempt, then retry once
 */
export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { method = "GET", body, auth = true, skipRefresh = false } = options;
  const headers: Record<string, string> = {
    Accept: "application/json",
  };

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (auth) {
    // Proactively refresh if access is expired/missing but we still have refresh
    let token = getAccessToken();
    if (!token && getRefreshToken() && !skipRefresh) {
      await tryRefresh();
      token = getAccessToken();
    }
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && auth && !skipRefresh) {
    const ok = await tryRefresh();
    if (ok) {
      return apiFetch<T>(path, { ...options, skipRefresh: true });
    }
    clearLegacyTokenStorage();
    if (
      typeof window !== "undefined" &&
      !window.location.pathname.startsWith("/login")
    ) {
      window.location.href = `/login?next=${encodeURIComponent(window.location.pathname)}`;
    }
    throw await parseError(res);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  if (!res.ok) {
    throw await parseError(res);
  }

  if (res.headers.get("content-length") === "0") {
    return undefined as T;
  }

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}
