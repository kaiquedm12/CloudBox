"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { CONTAINERS_QUERY_KEY } from "@/hooks/use-containers";
import { NODES_QUERY_KEY } from "@/hooks/use-nodes";
import type { ClusterNode } from "@/types/cluster-node";
import type { ClusterStatusMessage } from "@/types/cluster-status";

export type RealtimeConnectionStatus =
  | "connecting"
  | "connected"
  | "reconnecting";

type ClusterStatusContextValue = {
  connectionStatus: RealtimeConnectionStatus;
};

const ClusterStatusContext = createContext<ClusterStatusContextValue | null>(null);
const MAX_RECONNECT_DELAY_MS = 30_000;
const INITIAL_RECONNECT_DELAY_MS = 1_000;

function isClusterStatusMessage(value: unknown): value is ClusterStatusMessage {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const message = value as Record<string, unknown>;
  return (
    typeof message.resourceId === "string" &&
    typeof message.currentStatus === "string" &&
    (((message.eventType === "NODE_STATUS_CHANGED" ||
      message.eventType === "NODE_METRICS_UPDATED") &&
      message.resourceType === "NODE" &&
      ["ONLINE", "OFFLINE"].includes(message.currentStatus)) ||
      ((message.eventType === "CONTAINER_STATUS_CHANGE" ||
        message.eventType === "CONTAINER_STATUS_CHANGED") &&
        message.resourceType === "CONTAINER" &&
        ["PENDING", "SCHEDULED", "RUNNING", "STOPPED", "ERROR", "FAILED"].includes(
          message.currentStatus,
        )))
  );
}

function websocketUrl() {
  const url = new URL("/ws/cluster-status", window.location.href);
  url.protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return url;
}

export function ClusterStatusProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [connectionStatus, setConnectionStatus] =
    useState<RealtimeConnectionStatus>("connecting");

  useEffect(() => {
    let reconnectAttempt = 0;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let socket: WebSocket | undefined;
    let stopped = false;

    function refreshAfterMessage(message: ClusterStatusMessage) {
      if (message.resourceType === "NODE") {
        queryClient.setQueryData<ClusterNode[]>(NODES_QUERY_KEY, (current) =>
          current?.map((node) =>
            node.id === message.resourceId
              ? { ...node, status: message.currentStatus }
              : node,
          ),
        );
        void queryClient.invalidateQueries({ queryKey: NODES_QUERY_KEY });
        return;
      }

      // O master pode repetir RUNNING para sinalizar mudança de endpoints.
      // Mantemos o status local, mas sempre invalidamos abaixo para buscar o estado observado.
      void queryClient.invalidateQueries({ queryKey: CONTAINERS_QUERY_KEY });
    }

    function connect() {
      if (stopped) {
        return;
      }

      setConnectionStatus(reconnectAttempt === 0 ? "connecting" : "reconnecting");
      socket = new WebSocket(websocketUrl());

      socket.addEventListener("open", () => {
        reconnectAttempt = 0;
        setConnectionStatus("connected");
        void queryClient.invalidateQueries({ queryKey: NODES_QUERY_KEY });
        void queryClient.invalidateQueries({ queryKey: CONTAINERS_QUERY_KEY });
      });

      socket.addEventListener("message", (event) => {
        try {
          const message: unknown = JSON.parse(String(event.data));
          if (isClusterStatusMessage(message)) {
            refreshAfterMessage(message);
          }
        } catch {
          // Mensagens inválidas são ignoradas sem derrubar a conexão.
        }
      });

      socket.addEventListener("close", () => {
        if (stopped) {
          return;
        }

        reconnectAttempt += 1;
        setConnectionStatus("reconnecting");
        const delay = Math.min(
          INITIAL_RECONNECT_DELAY_MS * 2 ** (reconnectAttempt - 1),
          MAX_RECONNECT_DELAY_MS,
        );
        reconnectTimer = setTimeout(connect, delay);
      });

      socket.addEventListener("error", () => {
        socket?.close();
      });
    }

    connect();

    return () => {
      stopped = true;
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }
      socket?.close(1000, "Navegação encerrada");
    };
  }, [queryClient]);

  const value = useMemo(() => ({ connectionStatus }), [connectionStatus]);

  return (
    <ClusterStatusContext.Provider value={value}>
      {children}
    </ClusterStatusContext.Provider>
  );
}

export function useClusterStatus() {
  const context = useContext(ClusterStatusContext);

  if (!context) {
    throw new Error("useClusterStatus deve ser usado dentro de ClusterStatusProvider.");
  }

  return context;
}
