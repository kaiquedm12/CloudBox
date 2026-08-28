import type { ClusterNode } from "@/types/cluster-node";

type NodeCardProps = {
  node: ClusterNode;
};

type ResourceMeterProps = {
  accentClassName: string;
  free: number;
  label: string;
  shortLabel: string;
  total: number;
  value: string;
};

const decimalFormatter = new Intl.NumberFormat("pt-BR", {
  maximumFractionDigits: 1,
});

const heartbeatFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "medium",
});

function availablePercentage(free: number, total: number) {
  if (!Number.isFinite(free) || !Number.isFinite(total) || total <= 0) {
    return 0;
  }

  return Math.min(100, Math.max(0, (free / total) * 100));
}

function formatMegabytes(megabytes: number) {
  if (megabytes >= 1024) {
    return `${decimalFormatter.format(megabytes / 1024)} GB`;
  }

  return `${decimalFormatter.format(megabytes)} MB`;
}

function formatHeartbeat(lastHeartbeat: string | null) {
  if (!lastHeartbeat) {
    return "Heartbeat ainda não recebido";
  }

  const date = new Date(lastHeartbeat);

  if (Number.isNaN(date.getTime())) {
    return "Horário do heartbeat indisponível";
  }

  return `Último heartbeat em ${heartbeatFormatter.format(date)}`;
}

function ResourceMeter({
  accentClassName,
  free,
  label,
  shortLabel,
  total,
  value,
}: ResourceMeterProps) {
  const percentage = availablePercentage(free, total);

  return (
    <div>
      <div className="mb-2 flex items-start justify-between gap-4">
        <div className="flex items-center gap-2.5">
          <span className="grid h-7 min-w-9 place-items-center rounded-lg bg-slate-100 px-1.5 text-[10px] font-bold tracking-wide text-slate-500">
            {shortLabel}
          </span>
          <span className="text-sm font-medium text-slate-700">{label}</span>
        </div>
        <span className="text-right text-sm font-semibold tabular-nums text-slate-950">
          {value}
        </span>
      </div>
      <div
        aria-label={`${label}: ${decimalFormatter.format(percentage)}% disponível`}
        aria-valuemax={100}
        aria-valuemin={0}
        aria-valuenow={Math.round(percentage)}
        className="h-1.5 overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
      >
        <div
          className={`h-full rounded-full transition-[width] duration-500 ${accentClassName}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
      <p className="mt-1.5 text-xs text-slate-400">
        {decimalFormatter.format(percentage)}% disponível
      </p>
    </div>
  );
}

export function NodeCard({ node }: NodeCardProps) {
  const isOnline = node.status === "ONLINE";
  const temperature = node.temperatureCelsius;
  const temperatureWarning = temperature !== null && temperature >= 80;

  return (
    <article
      className={`overflow-hidden rounded-2xl border bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md ${
        isOnline ? "border-slate-200" : "border-slate-200 bg-slate-50/80"
      }`}
    >
      <div className="border-b border-slate-100 p-5 sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="mb-3 flex items-center gap-2">
              <span
                aria-hidden="true"
                className={`size-2.5 rounded-full ${
                  isOnline ? "bg-emerald-500 shadow-[0_0_0_4px_#d1fae5]" : "bg-slate-400"
                }`}
              />
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-400">
                Nó do cluster
              </span>
            </div>
            <h2 className="truncate text-xl font-semibold tracking-tight text-slate-950">
              {node.name}
            </h2>
          </div>
          <span
            aria-label={`Status: ${isOnline ? "online" : "offline"}`}
            className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${
              isOnline
                ? "bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-200"
                : "bg-slate-100 text-slate-600 ring-1 ring-inset ring-slate-200"
            }`}
          >
            {isOnline ? "Online" : "Offline"}
          </span>
        </div>
        <p className="mt-3 text-xs text-slate-400">
          {formatHeartbeat(node.lastHeartbeat)}
        </p>
      </div>

      <div className="space-y-5 p-5 sm:p-6">
        <ResourceMeter
          accentClassName="bg-blue-500"
          free={node.cpuFree}
          label="CPU livre"
          shortLabel="CPU"
          total={node.cpuTotal}
          value={`${decimalFormatter.format(node.cpuFree)} / ${decimalFormatter.format(node.cpuTotal)} núcleos`}
        />
        <ResourceMeter
          accentClassName="bg-violet-500"
          free={node.ramFreeMb}
          label="RAM livre"
          shortLabel="RAM"
          total={node.ramTotalMb}
          value={`${formatMegabytes(node.ramFreeMb)} / ${formatMegabytes(node.ramTotalMb)}`}
        />
        <ResourceMeter
          accentClassName="bg-cyan-500"
          free={node.diskFreeMb}
          label="Disco livre"
          shortLabel="SSD"
          total={node.diskTotalMb}
          value={`${formatMegabytes(node.diskFreeMb)} / ${formatMegabytes(node.diskTotalMb)}`}
        />
      </div>

      <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/70 px-5 py-4 sm:px-6">
        <span className="text-sm font-medium text-slate-500">Temperatura</span>
        <span
          className={`text-sm font-semibold tabular-nums ${
            temperatureWarning ? "text-rose-600" : "text-slate-800"
          }`}
        >
          {temperature !== null
            ? `${decimalFormatter.format(temperature)} °C`
            : "Sem sensor"}
        </span>
      </div>
    </article>
  );
}
