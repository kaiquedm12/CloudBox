"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api";
import type {
  CloudContainer,
  CreateContainerRequest,
} from "@/types/container";

export const CONTAINERS_QUERY_KEY = ["containers"] as const;

export function useContainers() {
  return useQuery({
    queryKey: CONTAINERS_QUERY_KEY,
    queryFn: ({ signal }) =>
      apiRequest<CloudContainer[]>("/api/containers", {
        method: "GET",
        signal,
      }),
  });
}

export function useCreateContainer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateContainerRequest) =>
      apiRequest<CloudContainer>("/api/containers", {
        method: "POST",
        body: request,
      }),
    onSuccess: (container) => {
      queryClient.setQueryData<CloudContainer[]>(
        CONTAINERS_QUERY_KEY,
        (current) =>
          current?.some((item) => item.id === container.id)
            ? current
            : [...(current ?? []), container],
      );
      void queryClient.invalidateQueries({ queryKey: CONTAINERS_QUERY_KEY });
    },
  });
}

function useContainerAction(method: "POST" | "DELETE", suffix = "") {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (containerId: string) =>
      apiRequest<CloudContainer>(`/api/containers/${containerId}${suffix}`, { method }),
    onSuccess: (updated) => {
      queryClient.setQueryData<CloudContainer[]>(CONTAINERS_QUERY_KEY, (current) =>
        current?.map((container) => container.id === updated.id ? updated : container),
      );
      void queryClient.invalidateQueries({ queryKey: CONTAINERS_QUERY_KEY });
    },
  });
}

export function useStopContainer() {
  return useContainerAction("POST", "/stop");
}

export function useRemoveContainer() {
  return useContainerAction("DELETE");
}
