"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  clearAccessToken,
  getAccessExpiresAt,
  getRefreshToken,
  isAuthenticated,
} from "@/lib/auth/session";
import * as authApi from "@/lib/api/auth";
import { tryRefresh } from "@/lib/api/client";

type AuthContextValue = {
  ready: boolean;
  authenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshAuthState: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

/** Refresh ~60s before access JWT expires so a page reload mid-session still has a valid token. */
const REFRESH_SKEW_MS = 60_000;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);

  const refreshAuthState = useCallback(() => {
    setAuthenticated(isAuthenticated());
  }, []);

  // On boot: restore access from storage, or rotate via refresh token body.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const ok = await authApi.restoreSession();
        if (!cancelled) {
          // If body refresh failed (API briefly down) but we still have tokens, stay signed in.
          setAuthenticated(ok || isAuthenticated());
        }
      } catch {
        if (!cancelled) {
          setAuthenticated(isAuthenticated());
        }
      } finally {
        if (!cancelled) {
          setReady(true);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Background: refresh access before it expires (avoids the "15 min then F5 = logged out" feel).
  useEffect(() => {
    if (!authenticated) return;

    const tick = async () => {
      if (!getRefreshToken()) return;
      const exp = getAccessExpiresAt();
      const msLeft = exp - Date.now();
      if (exp > 0 && msLeft <= REFRESH_SKEW_MS) {
        const ok = await tryRefresh();
        if (ok) {
          setAuthenticated(true);
        } else {
          // If refresh failed but we still have a token, stay; else mark logged out
          setAuthenticated(isAuthenticated());
        }
      }
    };

    const id = window.setInterval(() => {
      void tick();
    }, 30_000);
    void tick();
    return () => window.clearInterval(id);
  }, [authenticated]);

  const login = useCallback(
    async (email: string, password: string) => {
      await authApi.login(email, password);
      refreshAuthState();
    },
    [refreshAuthState],
  );

  const register = useCallback(
    async (email: string, password: string) => {
      await authApi.register(email, password);
      refreshAuthState();
    },
    [refreshAuthState],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    clearAccessToken();
    setAuthenticated(false);
  }, []);

  const value = useMemo(
    () => ({
      ready,
      authenticated,
      login,
      register,
      logout,
      refreshAuthState,
    }),
    [ready, authenticated, login, register, logout, refreshAuthState],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
