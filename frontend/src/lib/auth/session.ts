/**
 * Browser session for access + refresh tokens in localStorage.
 *
 * Vercel (https) → local API (http) cannot rely on the HttpOnly refresh cookie
 * for credentialed fetches, so the SPA keeps the opaque refresh token here and
 * sends it in the refresh request body.
 */

const ACCESS_TOKEN_KEY = "pw_access_token";
const ACCESS_EXPIRES_KEY = "pw_access_expires_at";
const REFRESH_TOKEN_KEY = "pw_refresh_token";

let accessToken: string | null = null;
let expiresAt = 0;
let refreshToken: string | null = null;
let hydrated = false;

function canUseStorage(): boolean {
  return typeof window !== "undefined";
}

function readStore(key: string): string | null {
  if (!canUseStorage()) return null;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStore(key: string, value: string | null): void {
  if (!canUseStorage()) return;
  try {
    if (value == null || value === "") {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // private mode / blocked
  }
}

function hydrate(): void {
  if (hydrated || !canUseStorage()) return;
  hydrated = true;
  try {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    const expRaw = localStorage.getItem(ACCESS_EXPIRES_KEY);
    const exp = expRaw ? Number(expRaw) : 0;
    if (token && exp > Date.now()) {
      accessToken = token;
      expiresAt = exp;
    } else {
      accessToken = null;
      expiresAt = 0;
      if (token) {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(ACCESS_EXPIRES_KEY);
      }
    }
    refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    // ignore
  }
}

function persist(): void {
  if (!canUseStorage()) return;
  try {
    if (accessToken && expiresAt > Date.now()) {
      localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
      localStorage.setItem(ACCESS_EXPIRES_KEY, String(expiresAt));
    } else {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(ACCESS_EXPIRES_KEY);
    }
    if (refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    }
  } catch {
    // ignore
  }
}

/** Apply a full token pair from login / register / refresh. */
export function applyAuthSession(
  access: string,
  expiresInSeconds: number,
  refresh: string | null | undefined,
): void {
  accessToken = access;
  // Guard: never treat missing/zero expiresIn as "already expired"
  const ttlSec =
    Number.isFinite(expiresInSeconds) && expiresInSeconds > 0
      ? expiresInSeconds
      : 900;
  expiresAt = Date.now() + ttlSec * 1000;
  if (refresh != null && refresh.length > 0) {
    refreshToken = refresh;
  }
  hydrated = true;
  persist();
}

export function setAccessToken(token: string, expiresInSeconds: number): void {
  applyAuthSession(token, expiresInSeconds, getRefreshToken());
}

export function getAccessToken(): string | null {
  hydrate();
  // Always prefer storage so another tab's refresh is visible
  const storeToken = readStore(ACCESS_TOKEN_KEY);
  const storeExp = Number(readStore(ACCESS_EXPIRES_KEY) || 0);
  if (storeToken && storeExp > Date.now()) {
    accessToken = storeToken;
    expiresAt = storeExp;
    return accessToken;
  }
  if (accessToken && expiresAt > Date.now()) {
    return accessToken;
  }
  // expired
  accessToken = null;
  expiresAt = 0;
  writeStore(ACCESS_TOKEN_KEY, null);
  writeStore(ACCESS_EXPIRES_KEY, null);
  return null;
}

/** Always re-read from localStorage so multi-tab rotations are picked up. */
export function getRefreshToken(): string | null {
  hydrate();
  const fromStore = readStore(REFRESH_TOKEN_KEY);
  if (fromStore) {
    refreshToken = fromStore;
    return fromStore;
  }
  return refreshToken;
}

export function clearAccessToken(): void {
  accessToken = null;
  expiresAt = 0;
  refreshToken = null;
  hydrated = true;
  writeStore(ACCESS_TOKEN_KEY, null);
  writeStore(ACCESS_EXPIRES_KEY, null);
  writeStore(REFRESH_TOKEN_KEY, null);
}

export function clearLegacyTokenStorage(): void {
  // no-op
}

export function isAuthenticated(): boolean {
  return getAccessToken() != null || getRefreshToken() != null;
}

export function getAccessExpiresAt(): number {
  hydrate();
  const storeExp = Number(readStore(ACCESS_EXPIRES_KEY) || 0);
  if (storeExp > 0) {
    expiresAt = storeExp;
  }
  return expiresAt;
}
