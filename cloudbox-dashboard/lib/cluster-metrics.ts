import type { ClusterNode } from "@/types/cluster-node";

export const MAX_SAFE_TEMPERATURE_CELSIUS = 75;
export const WARNING_USAGE_PERCENTAGE = 75;
export const CRITICAL_USAGE_PERCENTAGE = 90;

export type ResourceTotals = {
  free: number;
  total: number;
  usedPercentage: number;
};

export function usedPercentage(free: number, total: number) {
  if (!Number.isFinite(free) || !Number.isFinite(total) || total <= 0) {
    return 0;
  }

  const used = ((total - free) / total) * 100;
  return Math.min(100, Math.max(0, used));
}

export function aggregateResource(
  nodes: ClusterNode[],
  getFree: (node: ClusterNode) => number,
  getTotal: (node: ClusterNode) => number,
): ResourceTotals {
  const totals = nodes.reduce(
    (current, node) => ({
      free: current.free + getFree(node),
      total: current.total + getTotal(node),
    }),
    { free: 0, total: 0 },
  );

  return {
    ...totals,
    usedPercentage: usedPercentage(totals.free, totals.total),
  };
}

export function nodeHasAlert(node: ClusterNode) {
  return (
    node.status === "OFFLINE" ||
    (node.temperatureCelsius !== null &&
      node.temperatureCelsius >= MAX_SAFE_TEMPERATURE_CELSIUS) ||
    usedPercentage(node.cpuFree, node.cpuTotal) >= CRITICAL_USAGE_PERCENTAGE ||
    usedPercentage(node.ramFreeMb, node.ramTotalMb) >= CRITICAL_USAGE_PERCENTAGE ||
    usedPercentage(node.diskFreeMb, node.diskTotalMb) >= CRITICAL_USAGE_PERCENTAGE
  );
}

export function utilizationTone(percentage: number) {
  if (percentage >= CRITICAL_USAGE_PERCENTAGE) return "critical" as const;
  if (percentage >= WARNING_USAGE_PERCENTAGE) return "warning" as const;
  return "healthy" as const;
}
