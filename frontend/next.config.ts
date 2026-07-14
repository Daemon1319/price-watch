import type { NextConfig } from "next";
import path from "path";
import { fileURLToPath } from "url";

// frontend/ — keep turbopack + tracing roots identical (Vercel requires match).
const appRoot = path.dirname(fileURLToPath(import.meta.url));
// Monorepo root (price_watch/) so Vercel / vercel path0 and local both agree.
const repoRoot = path.join(appRoot, "..");

const nextConfig: NextConfig = {
  // Avoid picking a parent lockfile; both fields must be the same value.
  outputFileTracingRoot: repoRoot,
  turbopack: {
    root: repoRoot,
  },
};

export default nextConfig;
