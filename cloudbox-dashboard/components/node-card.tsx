"use client";

import Link from "next/link";
import { useLanguage } from "@/lib/i18n";
import type { ClusterNode } from "@/types/cluster-node";

type ResourceMeterProps = { accentClassName: string; free: number; label: string; shortLabel: string; total: number; value: string };

function availablePercentage(free: number, total: number) {
  if (!Number.isFinite(free) || !Number.isFinite(total) || total <= 0) return 0;
  return Math.min(100, Math.max(0, (free / total) * 100));
}

function formatMegabytes(megabytes: number, formatter: Intl.NumberFormat) {
  return megabytes >= 1024 ? `${formatter.format(megabytes / 1024)} GB` : `${formatter.format(megabytes)} MB`;
}

function ResourceMeter({ accentClassName, free, label, shortLabel, total, value }: ResourceMeterProps) {
  const { locale, t } = useLanguage();
  const formatter = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
  const percentage = availablePercentage(free, total);

  return (
    <div>
      <div className="mb-2 flex items-start justify-between gap-4">
        <div className="flex items-center gap-2.5">
          <span className="grid h-7 min-w-9 place-items-center rounded-lg bg-slate-100 px-1.5 text-[10px] font-bold tracking-wide text-slate-500">{shortLabel}</span>
          <span className="text-sm font-medium text-slate-700">{label}</span>
        </div>
        <span className="text-right text-sm font-semibold tabular-nums text-slate-950">{value}</span>
      </div>
      <div aria-label={`${label}: ${formatter.format(percentage)}% ${t("available")}`} aria-valuemax={100} aria-valuemin={0} aria-valuenow={Math.round(percentage)} className="h-1.5 overflow-hidden rounded-full bg-slate-100" role="progressbar">
        <div className={`h-full rounded-full transition-[width] duration-500 ${accentClassName}`} style={{ width: `${percentage}%` }} />
      </div>
      <p className="mt-1.5 text-xs text-slate-400">{formatter.format(percentage)}% {t("available")}</p>
    </div>
  );
}

export function NodeCard({ node }: { node: ClusterNode }) {
  const { locale, t } = useLanguage();
  const numberFormatter = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
  const heartbeatFormatter = new Intl.DateTimeFormat(locale, { dateStyle: "short", timeStyle: "medium" });
  const isOnline = node.status === "ONLINE";
  const temperature = node.temperatureCelsius;
  const temperatureWarning = temperature !== null && temperature >= 80;
  const heartbeat = (() => {
    if (!node.lastHeartbeat) return t("heartbeatMissing");
    const date = new Date(node.lastHeartbeat);
    if (Number.isNaN(date.getTime())) return t("heartbeatUnavailable");
    return `${t("lastHeartbeatAt")} ${heartbeatFormatter.format(date)}`;
  })();

  return (
    <Link aria-label={`${t("viewNode")} ${node.name}`} className="block rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600" href={`/nodes/${encodeURIComponent(node.id)}`}>
      <article className={`group overflow-hidden rounded-3xl border bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:border-blue-200 hover:shadow-xl hover:shadow-slate-200/50 dark:hover:border-blue-800 dark:hover:shadow-black/20 ${isOnline ? "border-slate-200" : "border-slate-200 bg-slate-50/80"}`}>
        <div className="border-b border-slate-100 p-5 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <div className="mb-3 flex items-center gap-2">
                <span aria-hidden="true" className={`size-2.5 rounded-full ${isOnline ? "bg-emerald-500 shadow-[0_0_0_4px_#d1fae5]" : "bg-slate-400"}`} />
                <span className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-400">{t("clusterNode")}</span>
              </div>
              <h2 className="truncate text-xl font-semibold tracking-tight text-slate-950 transition group-hover:text-blue-600 dark:group-hover:text-blue-400">{node.name}</h2>
            </div>
            <span aria-label={`Status: ${isOnline ? "online" : "offline"}`} className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${isOnline ? "bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-200" : "bg-slate-100 text-slate-600 ring-1 ring-inset ring-slate-200"}`}>{isOnline ? "Online" : "Offline"}</span>
          </div>
          <p className="mt-3 text-xs text-slate-400">{heartbeat}</p>
        </div>
        <div className="space-y-5 p-5 sm:p-6">
          <ResourceMeter accentClassName="bg-blue-500" free={node.cpuFree} label={t("freeCpu")} shortLabel="CPU" total={node.cpuTotal} value={`${numberFormatter.format(node.cpuFree)} / ${numberFormatter.format(node.cpuTotal)} ${t("cores")}`} />
          <ResourceMeter accentClassName="bg-violet-500" free={node.ramFreeMb} label={t("freeRam")} shortLabel="RAM" total={node.ramTotalMb} value={`${formatMegabytes(node.ramFreeMb, numberFormatter)} / ${formatMegabytes(node.ramTotalMb, numberFormatter)}`} />
          <ResourceMeter accentClassName="bg-cyan-500" free={node.diskFreeMb} label={t("freeDisk")} shortLabel="SSD" total={node.diskTotalMb} value={`${formatMegabytes(node.diskFreeMb, numberFormatter)} / ${formatMegabytes(node.diskTotalMb, numberFormatter)}`} />
        </div>
        <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/70 px-5 py-4 sm:px-6">
          <span className="text-sm font-medium text-slate-500">{t("temperature")}</span>
          <span className={`text-sm font-semibold tabular-nums ${temperatureWarning ? "text-rose-600" : "text-slate-800"}`}>{temperature !== null ? `${numberFormatter.format(temperature)} °C` : t("noSensor")}</span>
        </div>
      </article>
    </Link>
  );
}
