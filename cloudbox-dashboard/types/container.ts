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

export type PortProtocol = "TCP" | "UDP";
export type PortExposure = "INTERNAL" | "HTTP" | "TCP" | "UDP";

export type ContainerPort = {
  containerPort: number;
  hostPort: number | null;
  protocol: PortProtocol;
  exposure: PortExposure;
  bindAddress: string | null;
};

export type ContainerEndpoint = {
  containerPort: number;
  hostPort: number;
  protocol: PortProtocol;
  address: string;
  /** URL calculada e autorizada pelo master; só existe para exposição HTTP. */
  url: string | null;
};

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
  ports: ContainerPort[];
  endpoints: ContainerEndpoint[];
};

export type CreateContainerRequest = {
  imageName: string;
  cpuCores: number;
  memoryMb: number;
  diskMb: number;
  ports: ContainerPort[];
};
