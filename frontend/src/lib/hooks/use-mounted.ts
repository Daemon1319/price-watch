"use client";

import { useEffect, useState } from "react";

/** True only after the component has mounted in the browser (post-hydration). */
export function useMounted(): boolean {
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    setMounted(true);
  }, []);
  return mounted;
}
