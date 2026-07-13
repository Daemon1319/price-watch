import type { NextConfig } from "next";
import path from "path";
import { fileURLToPath } from "url";

const root = path.dirname(fileURLToPath(import.meta.url));

const nextConfig: NextConfig = {
  // Avoid picking a parent lockfile as workspace root (OneDrive/user-level npm).
  turbopack: {
    root,
  },
};

export default nextConfig;
