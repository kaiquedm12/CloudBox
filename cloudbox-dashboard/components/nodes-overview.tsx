"use client";

import { NodeCard } from "@/components/node-card";
import {
  useClusterStatus,
  type RealtimeConnectionStatus,
} from "@/hooks/use-cluster-status";
import { useNodes } from "@/hooks/use-nodes";

const timeFormatter = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

function OverviewHeader({
  dataUpdatedAt,
  connectionStatus,
}: {
  dataUpdatedAt: number;
  connectionStatus: RealtimeConnectionStatus;
}) {
  const updateLabel = dataUpdatedAt
    ? `Atualizado às ${timeFormatter.format(dataUpdatedAt)}`
    : "Aguardando primeira leitura";

  return (
    <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
      <div>
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
          Visão geral
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 sm:text-4xl">
          Nós do cluster
        </h1>
        <p className="mt-3 max-w-2xl leading-7 text-slate-600">
          Acompanhe a disponibilidade e os recursos de todos os nós registrados no
          orquestrador.
        </p>
      </div>
      <div
        aria-live="polite"
        className="flex shrink-0 items-center gap-2 text-sm text-slate-500"
      >
        <span
          aria-hidden="true"
          className={`size-2 rounded-full ${
            connectionStatus === "connected"
              ? "bg-emerald-500"
              : "animate-pulse bg-amber-500"
          }`}
        />
        <span>
          {connectionStatus === "connected"
            ? "Conectado em tempo real"
            : connectionStatus === "reconnecting"
              ? "Reconectando..."
              : "Conectando..."}
          <span className="ml-2 hidden text-xs text-slate-400 lg:inline">
            · {updateLabel}
          </span>
        </span>
      </div>
    </div>
  );
}

function LoadingState() {
  return (
    <div
      aria-label="Carregando nós do cluster"
      className="grid gap-5 md:grid-cols-2 xl:grid-cols-3"
      role="status"
    >
      {[0, 1, 2].map((item) => (
        <div
          key={item}
          className="animate-pulse overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
        >
          <div className="flex items-center justify-between">
            <div className="h-6 w-32 rounded bg-slate-200" />
            <div className="h-6 w-16 rounded-full bg-slate-100" />
          </div>
          <div className="mt-8 space-y-6">
            {[0, 1, 2].map((resource) => (
              <div key={resource}>
                <div className="mb-3 h-4 w-full rounded bg-slate-100" />
                <div className="h-1.5 w-full rounded-full bg-slate-100" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export function NodesOverview() {
  const { connectionStatus } = useClusterStatus();
  const {
    data,
    dataUpdatedAt,
    error,
    isError,
    isPending,
    isRefetchError,
    refetch,
  } = useNodes();

  if (isPending) {
    return (
      <div className="space-y-8">
        <OverviewHeader
          connectionStatus={connectionStatus}
          dataUpdatedAt={dataUpdatedAt}
        />
        <LoadingState />
      </div>
    );
  }

  if (isError && !data) {
    return (
      <div className="space-y-8">
        <OverviewHeader
          connectionStatus={connectionStatus}
          dataUpdatedAt={dataUpdatedAt}
        />
        <section
          className="rounded-2xl border border-rose-200 bg-rose-50 p-6 sm:p-8"
          role="alert"
        >
          <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="font-semibold text-rose-900">
                Não foi possível conectar ao orquestrador
              </p>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-rose-700">
                Verifique se o serviço está em execução e se a URL configurada está
                acessível. {error instanceof Error ? error.message : ""}
              </p>
            </div>
            <button
              className="shrink-0 rounded-xl bg-rose-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-700"
              onClick={() => void refetch()}
              type="button"
            >
              Tentar novamente
            </button>
          </div>
        </section>
      </div>
    );
  }

  const nodes = [...(data ?? [])].sort((first, second) => {
    if (first.status !== second.status) {
      return first.status === "ONLINE" ? -1 : 1;
    }

    return first.name.localeCompare(second.name, "pt-BR");
  });
  const onlineCount = nodes.filter((node) => node.status === "ONLINE").length;
  const offlineCount = nodes.length - onlineCount;

  return (
    <div className="space-y-8">
      <OverviewHeader
        connectionStatus={connectionStatus}
        dataUpdatedAt={dataUpdatedAt}
      />

      <div className="flex flex-wrap gap-3" aria-label="Resumo do cluster">
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-600 shadow-sm">
          <strong className="mr-1.5 text-slate-950">{nodes.length}</strong>
          {nodes.length === 1 ? "nó registrado" : "nós registrados"}
        </div>
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-sm text-emerald-700">
          <strong className="mr-1.5">{onlineCount}</strong> online
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-100 px-4 py-2.5 text-sm text-slate-600">
          <strong className="mr-1.5">{offlineCount}</strong> offline
        </div>
        <div className="ml-auto hidden items-center text-xs text-slate-400 lg:flex">
          Atualizações recebidas via WebSocket
        </div>
      </div>

      {isRefetchError ? (
        <div
          className="flex flex-col justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 sm:flex-row sm:items-center"
          role="alert"
        >
          <span>A última atualização falhou. Os dados exibidos podem estar desatualizados.</span>
          <button
            className="font-semibold underline underline-offset-4"
            onClick={() => void refetch()}
            type="button"
          >
            Atualizar agora
          </button>
        </div>
      ) : null}

      {nodes.length > 0 ? (
        <section
          aria-label="Nós registrados"
          className="grid gap-5 md:grid-cols-2 xl:grid-cols-3"
        >
          {nodes.map((node) => (
            <NodeCard key={node.id} node={node} />
          ))}
        </section>
      ) : (
        <section className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-14 text-center">
          <div className="mx-auto grid size-12 place-items-center rounded-2xl bg-slate-100 text-xs font-bold text-slate-500">
            NÓ
          </div>
          <h2 className="mt-4 text-lg font-semibold text-slate-900">
            Nenhum nó registrado
          </h2>
          <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
            Assim que um agente se registrar no orquestrador, seus recursos aparecerão
            automaticamente nesta tela.
          </p>
        </section>
      )}
    </div>
  );
}
