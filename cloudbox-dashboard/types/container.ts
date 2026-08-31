export type ContainerStatus =
  | "PENDING"
  | "SCHEDULED"
  | "RUNNING"
  | "STOPPING"
  | "STOPPED"
  | "REMOVING"
  | "REMOVED"
  | "ERROR"
  | "FAILED";

export type CloudContainer = {
  id: string;
  imageName: string;
  cpuCores: number;
  memoryMb: number;
  diskMb: number;
  status: ContainerStatus;
  nodeId: string | null;
  dockerContainerId: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type CreateContainerRequest = {
  imageName: string;
  cpuCores: number;
  memoryMb: number;
  diskMb: number;
};
