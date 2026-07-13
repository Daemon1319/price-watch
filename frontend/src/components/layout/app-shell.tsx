"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui";
import { cn } from "@/lib/format";
import { useMounted } from "@/lib/hooks/use-mounted";

const nav = [
  { href: "/", label: "Dashboard", short: "Home" },
  { href: "/items", label: "Tracked items", short: "Items" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { authenticated, logout, ready } = useAuth();
  const mounted = useMounted();
  const [menuOpen, setMenuOpen] = useState(false);

  const isAuthPage = pathname === "/login" || pathname === "/register";
  const showAuthChrome =
    mounted && ready && authenticated && !isAuthPage;

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenuOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  async function onLogout() {
    setMenuOpen(false);
    await logout();
    router.replace("/login");
  }

  return (
    <div className="flex min-h-dvh flex-1 flex-col bg-[var(--background)] text-[var(--foreground)]">
      <header className="sticky top-0 z-40 border-b border-[var(--border)] bg-[var(--surface)]">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-3 px-4 sm:h-16 sm:px-6">
          <div className="flex min-w-0 items-center gap-3 sm:gap-8">
            <Link
              href={showAuthChrome ? "/" : "/login"}
              className="flex items-center gap-2.5 font-semibold tracking-tight"
            >
              <span className="flex size-8 items-center justify-center rounded-xl bg-[var(--accent)] text-sm font-bold text-white">
                P
              </span>
              <span className="hidden xs:inline sm:inline">PriceWatch</span>
            </Link>

            {showAuthChrome && (
              <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
                {nav.map((item) => {
                  const active =
                    item.href === "/"
                      ? pathname === "/"
                      : pathname.startsWith(item.href);
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={cn(
                        "rounded-xl px-3.5 py-2 text-sm font-medium transition-colors",
                        active
                          ? "bg-[var(--accent)] text-white"
                          : "text-[var(--muted)] hover:bg-[var(--surface-muted)] hover:text-[var(--foreground)]",
                      )}
                    >
                      {item.label}
                    </Link>
                  );
                })}
              </nav>
            )}
          </div>

          {showAuthChrome && (
            <>
              <div className="hidden md:block">
                <Button variant="secondary" size="sm" onClick={onLogout}>
                  Log out
                </Button>
              </div>
              <button
                type="button"
                className="tap-target inline-flex items-center justify-center rounded-xl border border-[var(--border)] bg-[var(--surface)] text-[var(--foreground)] md:hidden"
                aria-expanded={menuOpen}
                aria-label={menuOpen ? "Close menu" : "Open menu"}
                onClick={() => setMenuOpen((o) => !o)}
              >
                {menuOpen ? (
                  <IconClose />
                ) : (
                  <IconMenu />
                )}
              </button>
            </>
          )}
        </div>

        {/* Mobile drawer */}
        {showAuthChrome && menuOpen && (
          <div className="border-t border-[var(--border)] bg-[var(--surface)] px-4 py-3 md:hidden">
            <nav className="flex flex-col gap-1" aria-label="Mobile">
              {nav.map((item) => {
                const active =
                  item.href === "/"
                    ? pathname === "/"
                    : pathname.startsWith(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setMenuOpen(false)}
                    className={cn(
                      "rounded-xl px-3.5 py-3 text-sm font-medium",
                      active
                        ? "bg-[var(--accent)] text-white"
                        : "text-[var(--muted)] hover:bg-[var(--surface-muted)]",
                    )}
                  >
                    {item.label}
                  </Link>
                );
              })}
              <button
                type="button"
                onClick={onLogout}
                className="mt-1 rounded-xl px-3.5 py-3 text-left text-sm font-medium text-[var(--danger)] hover:bg-[var(--danger-soft)]"
              >
                Log out
              </button>
            </nav>
          </div>
        )}
      </header>

      <main
        className={cn(
          "mx-auto w-full flex-1 px-4 py-6 sm:px-6 sm:py-8",
          isAuthPage ? "max-w-md" : "max-w-6xl",
          showAuthChrome && "pb-24 md:pb-8",
        )}
      >
        {children}
      </main>

      {/* Mobile bottom tab bar */}
      {showAuthChrome && (
        <nav
          className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--border)] bg-[var(--surface)] md:hidden"
          style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
          aria-label="Bottom navigation"
        >
          <div className="mx-auto grid max-w-lg grid-cols-2 gap-1 px-2 py-1.5">
            {nav.map((item) => {
              const active =
                item.href === "/"
                  ? pathname === "/"
                  : pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "flex min-h-12 flex-col items-center justify-center gap-0.5 rounded-xl text-[0.7rem] font-semibold",
                    active
                      ? "bg-[var(--accent-soft)] text-[var(--accent)]"
                      : "text-[var(--muted)]",
                  )}
                >
                  {item.href === "/" ? <IconHome active={active} /> : <IconList active={active} />}
                  {item.short}
                </Link>
              );
            })}
          </div>
        </nav>
      )}

      <footer className="hidden border-t border-[var(--border)] py-5 text-center text-xs text-[var(--muted)] md:block">
        PriceWatch · Track prices & stock on supported storefronts
      </footer>
    </div>
  );
}

function IconMenu() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" />
    </svg>
  );
}

function IconClose() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M6 6l12 12M18 6L6 18" strokeLinecap="round" />
    </svg>
  );
}

function IconHome({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill={active ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9.5Z" strokeLinejoin="round" />
    </svg>
  );
}

function IconList({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" strokeLinecap="round" opacity={active ? 1 : 0.85} />
    </svg>
  );
}
