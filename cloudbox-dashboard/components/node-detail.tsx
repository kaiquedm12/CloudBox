"use client";

import Link from "next/link";
import { ContainerList } from "@/components/container-list";
import { useClusterStatus } from "@/hooks/use-cluster-status";
import { useContainers } from "@/hooks/use-containers";
import { useNode } from "@/hooks/use-nodes";

const numberFormatter = new Intl.NumberFormat("pt-BR", {
  maximumFractionDigits: 1,
});

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "long",
  timeStyle: "medium",
});

function megabytes(value: number) {
  return value >= 1024
    ? `${numberFormatter.format(value / 1024)} GB`
    : `${numberFormatter.format(value)} MB`;
}

function heartbeat(value: string | null) {
  if (!value) {
    return "Ainda não recebido";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Data indisponível" : dateFormatter.format(date);
}

function Metric({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="mt-2 text-xl font-semibold tracking-tight text-slate-950">{value}</dd>
    </div>
  );
}

export function NodeDetail({ nodeId }: { nodeId: string }) {
  const { connectionStatus } = useClusterStatus();
  const nodeQuery = useNode(nodeId);
  const containersQuery = useContainers();

  if (nodeQuery.isPending) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
        Carregando dados do nó...
      </div>
    );
  }

  if (nodeQuery.isError) {
    return (
      <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6" role="alert">
        <p className="font-semibold text-rose-900">Não foi possível carregar o nó</p>
        <button
          className="mt-3 text-sm font-semibold text-rose-700 underline underline-offset-4"
          onClick={() => void nodeQuery.refetch()}
          type="button"
        >
          Tentar novamente
        </button>
      </div>
    );
  }

  const node = nodeQuery.data;

  if (!node) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-slate-950">Nó não encontrado</h1>
        <p className="mt-3 text-slate-600">
          O identificador informado não corresponde a um nó registrado.
        </p>
        <Link className="mt-5 inline-block font-semibold text-blue-600" href="/">
          Voltar para a visão geral
        </Link>
      </div>
    );
  }

  const isOnline = node.status === "ONLINE";
  const containers = (containersQuery.data ?? [])
    .filter((container) => container.nodeId === node.id)
    .sort(
      (first, second) =>
        new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime(),
    );

  return (
    <div className="space-y-8">
      <div>
        <Link
          className="text-sm font-semibold text-blue-600 transition hover:text-blue-700"
          href="/"
        >
          ← Voltar para a visão geral
        </Link>
        <div className="mt-5 flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
          <div>
            <div className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className={`size-3 rounded-full ${
                  isOnline
                    ? "bg-emerald-500 shadow-[0_0_0_5px_#d1fae5]"
                    : "bg-slate-400 shadow-[0_0_0_5px_#e2e8f0]"
                }`}
              />
              <span className="text-sm font-semibold uppercase tracking-[0.16em] text-slate-400">
                {isOnline ? "Online" : "Offline"}
              </span>
            </div>
            <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950 sm:text-4xl">
              {node.name}
            </h1>
            <p className="mt-2 break-all font-mono text-xs text-slate-400">{node.id}</p>
            <p className="mt-2 break-all text-sm text-slate-600">
              Endereço anunciado: <span className="font-mono">{node.advertiseAddress ?? "não informado"}</span>
            </p>
          </div>
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <span
              aria-hidden="true"
              className={`size-2 rounded-full ${
                connectionStatus === "connected"
                  ? "bg-emerald-500"
                  : "animate-pulse bg-amber-500"
              }`}
            />
            {connectionStatus === "connected" ? "Tempo real conectado" : "Reconectando..."}
          </div>
        </div>
      </div>

      <section aria-labelledby="metrics-title">
        <h2 className="text-xl font-semibold text-slate-950" id="metrics-title">
          Métricas atuais
        </h2>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Metric
            label="CPU disponível"
            value={`${numberFormatter.format(node.cpuFree)} de ${numberFormatter.format(node.cpuTotal)} núcleos`}
          />
          <Metric
            label="RAM disponível"
            value={`${megabytes(node.ramFreeMb)} de ${megabytes(node.ramTotalMb)}`}
          />
          <Metric
            label="Disco disponível"
            value={`${megabytes(node.diskFreeMb)} de ${megabytes(node.diskTotalMb)}`}
          />
          <Metric
            label="Temperatura"
            value={
              node.temperatureCelsius === null
                ? "Sem sensor"
                : `${numberFormatter.format(node.temperatureCelsius)} °C`
            }
          />
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:col-span-2">
            <dt className="text-sm text-slate-500">Último heartbeat</dt>
            <dd className="mt-2 text-base font-semibold text-slate-950">
              {heartbeat(node.lastHeartbeat)}
            </dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="node-containers-title">
        <div className="mb-4 flex items-end justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold text-slate-950" id="node-containers-title">
              Containers alocados
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {containers.length} {containers.length === 1 ? "container" : "containers"} neste nó
            </p>
          </div>
        </div>

        {containersQuery.isPending ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
            Carregando containers...
          </div>
        ) : containersQuery.isError ? (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-5" role="alert">
            <p className="text-sm text-rose-700">Não foi possível carregar os containers deste nó.</p>
            <button
              className="mt-2 text-sm font-semibold text-rose-700 underline underline-offset-4"
              onClick={() => void containersQuery.refetch()}
              type="button"
            >
              Tentar novamente
            </button>
          </div>
        ) : (
          <ContainerList
            containers={containers}
            emptyMessage="Este nó ainda não possui containers alocados."
            nodeStatuses={new Map([[node.id, node.status]])}
          />
        )}
      </section>
    </div>
  );
}
