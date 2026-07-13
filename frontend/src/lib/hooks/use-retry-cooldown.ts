"use client";

import { useEffect, useState } from "react";
import { getUserFacingError } from "@/lib/api/errors";

/**
 * When the last error carries retryAfterSeconds (e.g. 429), returns remaining
 * seconds until the user should retry. Used to disable submit buttons.
 */
export function useRetryCooldown(error: unknown): number {
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (!error) {
      setRemaining(0);
      return;
    }

    const { retryAfterSeconds } = getUserFacingError(error);
    if (retryAfterSeconds == null || retryAfterSeconds <= 0) {
      setRemaining(0);
      return;
    }

    const endsAt = Date.now() + retryAfterSeconds * 1000;
    setRemaining(retryAfterSeconds);

    const id = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((endsAt - Date.now()) / 1000));
      setRemaining(left);
      if (left <= 0) {
        window.clearInterval(id);
      }
    }, 250);

    return () => window.clearInterval(id);
  }, [error]);

  return remaining;
}
