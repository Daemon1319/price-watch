import { apiFetch } from "@/lib/api/client";
import type {
  CreateTrackedItemRequest,
  PageResponse,
  TrackedItem,
  TrackedItemStatus,
  UpdateTrackedItemRequest,
} from "@/lib/types";

export function listTrackedItems(params?: {
  status?: TrackedItemStatus[];
  page?: number;
  size?: number;
}) {
  const q = new URLSearchParams();
  if (params?.status?.length) {
    for (const s of params.status) q.append("status", s);
  }
  if (params?.page != null) q.set("page", String(params.page));
  if (params?.size != null) q.set("size", String(params.size));
  const qs = q.toString();
  return apiFetch<PageResponse<TrackedItem>>(
    `/api/v1/tracked-items${qs ? `?${qs}` : ""}`,
  );
}

export function getTrackedItem(id: string) {
  return apiFetch<TrackedItem>(`/api/v1/tracked-items/${id}`);
}

export function createTrackedItem(body: CreateTrackedItemRequest) {
  return apiFetch<TrackedItem>("/api/v1/tracked-items", {
    method: "POST",
    body,
  });
}

export function updateTrackedItem(id: string, body: UpdateTrackedItemRequest) {
  return apiFetch<TrackedItem>(`/api/v1/tracked-items/${id}`, {
    method: "PATCH",
    body,
  });
}

export function deleteTrackedItem(id: string) {
  return apiFetch<void>(`/api/v1/tracked-items/${id}`, {
    method: "DELETE",
  });
}
