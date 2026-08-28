"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api";
import type { ClusterNode } from "@/types/cluster-node";

export const NODES_POLLING_INTERVAL_MS = 5_000;

export function useNodes() {
  return useQuery({
    queryKey: ["nodes"],
    queryFn: ({ signal }) =>
      apiRequest<ClusterNode[]>("/api/nodes", {
        method: "GET",
        signal,
      }),
    refetchInterval: NODES_POLLING_INTERVAL_MS,
    refetchIntervalInBackground: true,
    staleTime: NODES_POLLING_INTERVAL_MS - 1_000,
  });
}
