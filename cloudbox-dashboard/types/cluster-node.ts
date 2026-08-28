export type NodeStatus = "ONLINE" | "OFFLINE";

export type ClusterNode = {
  id: string;
  name: string;
  status: NodeStatus;
  cpuTotal: number;
  cpuFree: number;
  ramTotalMb: number;
  ramFreeMb: number;
  diskTotalMb: number;
  diskFreeMb: number;
  temperatureCelsius: number | null;
  lastHeartbeat: string | null;
};
