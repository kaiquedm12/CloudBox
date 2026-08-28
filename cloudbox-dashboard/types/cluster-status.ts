import type { ContainerStatus } from "@/types/container";
import type { NodeStatus } from "@/types/cluster-node";

export type ClusterStatusMessage =
  | {
      eventType: "NODE_STATUS_CHANGED" | "NODE_METRICS_UPDATED";
      resourceType: "NODE";
      resourceId: string;
      previousStatus: NodeStatus | null;
      currentStatus: NodeStatus;
      occurredAt: string;
    }
  | {
      eventType: "CONTAINER_STATUS_CHANGED";
      resourceType: "CONTAINER";
      resourceId: string;
      previousStatus: ContainerStatus | null;
      currentStatus: ContainerStatus;
      occurredAt: string;
    };
