"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api";
import type { ClusterNode } from "@/types/cluster-node";

export const NODES_QUERY_KEY = ["nodes"] as const;

export function useNodes() {
  return useQuery({
    queryKey: NODES_QUERY_KEY,
    queryFn: ({ signal }) =>
      apiRequest<ClusterNode[]>("/api/nodes", {
        method: "GET",
        signal,
      }),
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function useNode(nodeId: string) {
  return useQuery({
    queryKey: NODES_QUERY_KEY,
    queryFn: ({ signal }) =>
      apiRequest<ClusterNode[]>("/api/nodes", {
        method: "GET",
        signal,
      }),
    select: (nodes) => nodes.find((node) => node.id === nodeId),
    staleTime: Number.POSITIVE_INFINITY,
  });
}
