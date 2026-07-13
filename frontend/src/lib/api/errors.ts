import { ApiError } from "@/lib/api/client";

/** Normalized copy for UI alerts. */
export type UserFacingError = {
  title: string;
  detail: string;
  fieldErrors?: Record<string, string>;
  retryAfterSeconds?: number;
  status?: number;
};

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/**
 * Maps API / network failures to short, user-safe title + detail.
 * Handles 429 (rate limit), tracking-limit 403, and field validation.
 */
export function getUserFacingError(error: unknown): UserFacingError {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      const sec =
        error.retryAfterSeconds != null && error.retryAfterSeconds > 0
          ? error.retryAfterSeconds
          : 60;
      return {
        title: "Too many requests",
        detail: `You've sent too many requests. Wait about ${sec} second${sec === 1 ? "" : "s"} and try again.`,
        retryAfterSeconds: sec,
        status: 429,
      };
    }

    if (isTrackingLimitError(error)) {
      return {
        title: error.problem.title || "Tracking limit reached",
        detail:
          error.problem.detail ||
          "You have reached the maximum number of tracked products. Delete or pause unused items first.",
        status: error.status,
      };
    }

    const fieldErrors = error.problem.fieldErrors;
    const hasFields = fieldErrors && Object.keys(fieldErrors).length > 0;

    return {
      title: error.problem.title || "Something went wrong",
      detail:
        error.problem.detail ||
        error.message ||
        (hasFields
          ? "One or more fields failed validation."
          : `Request failed (${error.status})`),
      fieldErrors: hasFields ? fieldErrors : undefined,
      status: error.status,
      retryAfterSeconds: error.retryAfterSeconds,
    };
  }

  if (error instanceof Error) {
    return {
      title: "Something went wrong",
      detail: error.message || "An unexpected error occurred.",
    };
  }

  return {
    title: "Something went wrong",
    detail: String(error ?? "An unexpected error occurred."),
  };
}

function isTrackingLimitError(error: ApiError): boolean {
  if (error.status !== 403) return false;
  const title = (error.problem.title || "").toLowerCase();
  const detail = (error.problem.detail || error.message || "").toLowerCase();
  return (
    title.includes("tracking limit") ||
    detail.includes("track at most") ||
    detail.includes("tracking limit")
  );
}
