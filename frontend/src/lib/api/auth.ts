import {
  applyAuthSession,
  clearAccessToken,
  clearLegacyTokenStorage,
  getAccessToken,
  getRefreshToken,
} from "@/lib/auth/session";
import { apiFetch, tryRefresh } from "@/lib/api/client";
import type { LoginResponse } from "@/lib/types";

function saveSession(data: LoginResponse): void {
  applyAuthSession(data.accessToken, data.expiresIn, data.refreshToken);
}

export async function register(email: string, password: string): Promise<void> {
  clearLegacyTokenStorage();
  const data = await apiFetch<LoginResponse>("/api/v1/auth/register", {
    method: "POST",
    body: { email, password },
    auth: false,
  });
  saveSession(data);
}

export async function login(email: string, password: string): Promise<void> {
  clearLegacyTokenStorage();
  const data = await apiFetch<LoginResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { email, password },
    auth: false,
  });
  saveSession(data);
}

/**
 * After page load / reopen:
 * 1) valid access JWT in localStorage
 * 2) else body refresh token → new access JWT (required for Vercel → local API)
 */
export async function restoreSession(): Promise<boolean> {
  clearLegacyTokenStorage();
  if (getAccessToken()) {
    return true;
  }
  if (!getRefreshToken()) {
    return false;
  }
  return tryRefresh();
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    await apiFetch<void>("/api/v1/auth/logout", {
      method: "POST",
      body: refreshToken ? { refreshToken } : undefined,
    });
  } catch {
    // Always clear local session even if server revoke fails
  } finally {
    clearAccessToken();
    clearLegacyTokenStorage();
  }
}
