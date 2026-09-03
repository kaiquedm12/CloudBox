"use client";

import Link from "next/link";
import { ArrowRightIcon, BoxIcon } from "@/components/ui-icons";
import {
  MAX_SAFE_TEMPERATURE_CELSIUS,
  usedPercentage,
  utilizationTone,
} from "@/lib/cluster-metrics";
import { useLanguage } from "@/lib/i18n";
import type { ClusterNode } from "@/types/cluster-node";

type ResourceMeterProps = {
  free: number;
  label: string;
  shortLabel: string;
  total: number;
  value: string;
};

const toneClasses = {
  healthy: "bg-blue-500",
  warning: "bg-amber-500",
  critical: "bg-rose-500",
};

function formatMegabytes(megabytes: number, formatter: Intl.NumberFormat) {
  return megabytes >= 1024
    ? `${formatter.format(megabytes / 1024)} GB`
    : `${formatter.format(megabytes)} MB`;
}

function formatHeartbeat(
  value: string | null,
  locale: string,
  missing: string,
  unavailable: string,
) {
  if (!value) return missing;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return unavailable;

  const elapsedSeconds = Math.round((date.getTime() - Date.now()) / 1000);
  const absoluteSeconds = Math.abs(elapsedSeconds);
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });

  if (absoluteSeconds < 60) return formatter.format(elapsedSeconds, "second");
  if (absoluteSeconds < 3_600) return formatter.format(Math.round(elapsedSeconds / 60), "minute");
  if (absoluteSeconds < 86_400) return formatter.format(Math.round(elapsedSeconds / 3_600), "hour");
  return formatter.format(Math.round(elapsedSeconds / 86_400), "day");
}

function ResourceMeter({ free, label, shortLabel, total, value }: ResourceMeterProps) {
  const { locale, t } = useLanguage();
  const formatter = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 });
  const percentage = usedPercentage(free, total);
  const tone = utilizationTone(percentage);

  return (
    <div className="min-w-0">
      <div className="mb-2 flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <span className="grid h-6 min-w-8 place-items-center rounded-md bg-slate-100 px-1.5 text-[9px] font-extrabold tracking-wide text-slate-500 dark:bg-slate-800 dark:text-slate-400">{shortLabel}</span>
          <span className="truncate text-xs font-semibold text-slate-600 dark:text-slate-300">{label}</span>
        </div>
        <span className="shrink-0 text-xs font-bold tabular-nums text-slate-950 dark:text-white">{formatter.format(percentage)}%</span>
      </div>
      <div
        aria-label={`${label}: ${formatter.format(percentage)}% ${t("used")}`}
        aria-valuemax={100}
        aria-valuemin={0}
        aria-valuenow={Math.round(percentage)}
        className="h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"
        role="progressbar"
      >
        <div className={`h-full rounded-full transition-[width] duration-500 ${toneClasses[tone]}`} style={{ width: `${percentage}%` }} />
      </div>
      <p className="mt-1.5 truncate text-[11px] tabular-nums text-slate-400" title={value}>{value}</p>
    </div>
  );
}

export function NodeCard({
  containerCount = 0,
  node,
  variant = "grid",
}: {
  containerCount?: number;
  node: ClusterNode;
  variant?: "grid" | "list";
}) {
  const { locale, t } = useLanguage();
  const numberFormatter = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
  const isOnline = node.status === "ONLINE";
  const temperature = node.temperatureCelsius;
  const temperatureWarning =
    temperature !== null && temperature >= MAX_SAFE_TEMPERATURE_CELSIUS;
  const heartbeat = formatHeartbeat(
    node.lastHeartbeat,
    locale,
    t("heartbeatMissing"),
    t("heartbeatUnavailable"),
  );
  const containerLabel = containerCount === 1 ? t("containerSingular") : t("containerPlural");

  return (
    <Link
      aria-label={`${t("viewNode")} ${node.name}`}
      className="block rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600"
      href={`/nodes/${encodeURIComponent(node.id)}`}
    >
      <article className={`group overflow-hidden rounded-2xl border bg-white shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-lg dark:bg-slate-900 dark:hover:border-blue-800 ${isOnline ? "border-slate-200 dark:border-slate-800" : "border-slate-200 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900/60"} ${variant === "list" ? "lg:grid lg:grid-cols-[minmax(220px,0.75fr)_minmax(520px,2fr)_190px] lg:items-stretch" : ""}`}>
        <div className={`p-5 ${variant === "grid" ? "border-b border-slate-100 dark:border-slate-800" : "lg:border-r lg:border-slate-100 dark:lg:border-slate-800"}`}>
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <div className="mb-2.5 flex items-center gap-2">
                <span aria-hidden="true" className={`size-2.5 rounded-full ${isOnline ? "bg-emerald-500 shadow-[0_0_0_4px_#d1fae5] dark:shadow-[0_0_0_4px_#064e3b]" : "bg-slate-400"}`} />
                <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">{isOnline ? t("onlineNodesFilter") : t("offlineNodesFilter")}</span>
              </div>
              <h2 className="truncate text-lg font-bold tracking-tight text-slate-950 transition group-hover:text-blue-600 dark:text-white dark:group-hover:text-blue-400">{node.name}</h2>
              <p className="mt-1 text-xs text-slate-400">{isOnline ? t("lastHeartbeatAt") : t("lastReading")}: {heartbeat}</p>
            </div>
            <ArrowRightIcon className="mt-1 size-5 shrink-0 text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-blue-500" />
          </div>
        </div>

        <div className={`grid gap-4 p-5 ${variant === "list" ? "sm:grid-cols-3" : ""} ${isOnline ? "" : "opacity-60"}`}>
          <ResourceMeter free={node.cpuFree} label={t("cpuUsage")} shortLabel="CPU" total={node.cpuTotal} value={`${numberFormatter.format(node.cpuFree)} ${t("of")} ${numberFormatter.format(node.cpuTotal)} ${t("cores")} ${t("available")}`} />
          <ResourceMeter free={node.ramFreeMb} label={t("ramUsage")} shortLabel="RAM" total={node.ramTotalMb} value={`${formatMegabytes(node.ramFreeMb, numberFormatter)} ${t("of")} ${formatMegabytes(node.ramTotalMb, numberFormatter)} ${t("available")}`} />
          <ResourceMeter free={node.diskFreeMb} label={t("diskUsage")} shortLabel="SSD" total={node.diskTotalMb} value={`${formatMegabytes(node.diskFreeMb, numberFormatter)} ${t("of")} ${formatMegabytes(node.diskTotalMb, numberFormatter)} ${t("available")}`} />
        </div>

        <div className={`flex items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/70 px-5 py-3.5 dark:border-slate-800 dark:bg-slate-950/30 ${variant === "list" ? "lg:flex-col lg:items-start lg:justify-center lg:border-l lg:border-t-0" : ""}`}>
          <span className="flex items-center gap-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
            <BoxIcon className="size-4" /> {containerCount} {containerLabel}
          </span>
          <span className={`rounded-lg px-2 py-1 text-xs font-bold tabular-nums ${temperatureWarning ? "bg-rose-50 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300" : "text-slate-700 dark:text-slate-300"}`}>
            {temperature !== null ? `${numberFormatter.format(temperature)} °C` : t("noSensor")}
          </span>
        </div>
      </article>
    </Link>
  );
}
