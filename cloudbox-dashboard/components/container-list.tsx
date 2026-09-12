"use client";

import { useState } from "react";
import type { CloudContainer, ContainerEndpoint, ContainerStatus } from "@/types/container";

const statusLabels: Record<ContainerStatus, string> = {
  PENDING: "Pendente",
  SCHEDULED: "Agendado",
  RUNNING: "Em execução",
  STOPPED: "Parado",
  ERROR: "Erro",
  FAILED: "Falhou",
};

const statusClasses: Record<ContainerStatus, string> = {
  PENDING: "bg-amber-50 text-amber-700 ring-amber-200",
  SCHEDULED: "bg-blue-50 text-blue-700 ring-blue-200",
  RUNNING: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  STOPPED: "bg-slate-100 text-slate-600 ring-slate-200",
  ERROR: "bg-rose-50 text-rose-700 ring-rose-200",
  FAILED: "bg-rose-50 text-rose-700 ring-rose-200",
};

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

function formatCreatedAt(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Data indisponível" : dateFormatter.format(date);
}

function validHttpUrl(value: string | null) {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:" ? url : null;
  } catch {
    return null;
  }
}

function endpointAddress(endpoint: ContainerEndpoint) {
  const address = endpoint.address.includes(":") && !endpoint.address.startsWith("[")
    ? `[${endpoint.address}]`
    : endpoint.address;
  return `${address}:${endpoint.hostPort}`;
}

function isLoopbackAddress(address: string) {
  return address === "127.0.0.1" || address === "::1";
}

function EndpointRow({
  endpoint,
  actionsDisabled,
}: {
  endpoint: ContainerEndpoint;
  actionsDisabled: boolean;
}) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "error">("idle");
  const url = validHttpUrl(endpoint.url);
  const accessAddress = endpointAddress(endpoint);

  async function copyEndpoint() {
    try {
      await navigator.clipboard.writeText(accessAddress);
      setCopyState("copied");
    } catch {
      setCopyState("error");
    }
  }

  return (
    <li className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="font-mono text-sm font-semibold text-slate-800">
          Endereço: {accessAddress}
        </p>
        <p className="mt-1 text-xs text-slate-500">Porta interna {endpoint.containerPort} · {endpoint.protocol}</p>
        {isLoopbackAddress(endpoint.address) ? (
          <p className="mt-1 text-xs text-amber-700">Acesso local ao nó.</p>
        ) : null}
      </div>
      {actionsDisabled ? (
        <span className="text-xs font-medium text-amber-700">Acesso indisponível</span>
      ) : url ? (
        <a className="w-fit rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white transition hover:bg-blue-700" href={url.toString()} rel="noreferrer" target="_blank">
          Abrir aplicação
        </a>
      ) : (
        <div className="flex items-center gap-3">
          <button className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100" onClick={() => void copyEndpoint()} type="button">
            Copiar endpoint
          </button>
          <span aria-live="polite" className={`text-xs ${copyState === "error" ? "text-rose-700" : "text-slate-500"}`}>
            {copyState === "copied" ? "Copiado" : copyState === "error" ? "Não foi possível copiar" : ""}
          </span>
        </div>
      )}
    </li>
  );
}

export function ContainerList({
  containers,
  emptyMessage = "Nenhum container encontrado.",
  nodeNames,
  nodeStatuses,
  showNode = false,
}: {
  containers: CloudContainer[];
  emptyMessage?: string;
  nodeNames?: Map<string, string>;
  nodeStatuses?: Map<string, "ONLINE" | "OFFLINE">;
  showNode?: boolean;
}) {
  if (containers.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-12 text-center text-sm text-slate-500">
        {emptyMessage}
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      {containers.map((container) => (
        <article
          className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"
          key={container.id}
        >
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
            <div className="min-w-0">
              <p className="truncate font-mono text-base font-semibold text-slate-950">
                {container.imageName}
              </p>
              <p className="mt-1 truncate text-xs text-slate-400" title={container.id}>
                {container.id}
              </p>
            </div>
            <span
              className={`w-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClasses[container.status]}`}
            >
              {statusLabels[container.status]}
            </span>
          </div>

          <dl className="mt-5 grid grid-cols-2 gap-4 border-t border-slate-100 pt-5 text-sm sm:grid-cols-4">
            <div>
              <dt className="text-slate-400">CPU</dt>
              <dd className="mt-1 font-semibold text-slate-800">
                {container.cpuCores} {container.cpuCores === 1 ? "núcleo" : "núcleos"}
              </dd>
            </div>
            <div>
              <dt className="text-slate-400">RAM</dt>
              <dd className="mt-1 font-semibold text-slate-800">{container.memoryMb} MB</dd>
            </div>
            <div>
              <dt className="text-slate-400">Disco</dt>
              <dd className="mt-1 font-semibold text-slate-800">{container.diskMb} MB</dd>
            </div>
            <div>
              <dt className="text-slate-400">Criado em</dt>
              <dd className="mt-1 font-semibold text-slate-800">
                {formatCreatedAt(container.createdAt)}
              </dd>
            </div>
            {showNode ? (
              <div className="col-span-2 sm:col-span-4">
                <dt className="text-slate-400">Nó alocado</dt>
                <dd className="mt-1 font-semibold text-slate-800">
                  {container.nodeId
                    ? nodeNames?.get(container.nodeId) ?? container.nodeId
                    : "Aguardando agendamento"}
                </dd>
              </div>
            ) : null}
          </dl>

          {container.errorMessage ? (
            <p className="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {container.errorMessage}
            </p>
          ) : null}

          {(() => {
            const endpoints = container.endpoints ?? [];
            const nodeOffline = container.nodeId !== null && nodeStatuses?.get(container.nodeId) === "OFFLINE";
            const waitingForEndpoint = container.status === "PENDING" || container.status === "SCHEDULED";
            const actionsDisabled = nodeOffline || container.status !== "RUNNING";

            return (
              <section aria-label="Endpoints observados" className="mt-5 border-t border-slate-100 pt-5">
                <h3 className="text-sm font-semibold text-slate-800">Acesso</h3>
                {nodeOffline ? (
                  <p className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-800">
                    Nó offline: endpoints preservados apenas para diagnóstico; ações de acesso estão desabilitadas.
                  </p>
                ) : null}
                {endpoints.length > 0 ? (
                  <ul className="mt-3 space-y-2">
                    {endpoints.map((endpoint, index) => (
                      <EndpointRow actionsDisabled={actionsDisabled} endpoint={endpoint} key={`${endpoint.containerPort}-${endpoint.protocol}-${endpoint.address}-${endpoint.hostPort}-${index}`} />
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-sm text-slate-500">
                    {waitingForEndpoint ? "Aguardando a publicação das portas pelo nó." : container.status === "RUNNING" ? "Em execução, sem endpoint observado." : "Nenhum endpoint de acesso disponível."}
                  </p>
                )}
              </section>
            );
          })()}
        </article>
      ))}
    </div>
  );
}
