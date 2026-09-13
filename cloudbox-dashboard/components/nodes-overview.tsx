"use client";

import { useMemo, useState, type ReactNode } from "react";
import { CreateContainerModal } from "@/components/create-container-modal";
import { NodeCard } from "@/components/node-card";
import {
  ActivityIcon,
  AlertIcon,
  BoxIcon,
  CheckCircleIcon,
  GridIcon,
  ListIcon,
  PlusIcon,
  RefreshIcon,
  SearchIcon,
  ServerIcon,
} from "@/components/ui-icons";
import {
  aggregateResource,
  CRITICAL_USAGE_PERCENTAGE,
  MAX_SAFE_TEMPERATURE_CELSIUS,
  nodeHasAlert,
  usedPercentage,
  utilizationTone,
} from "@/lib/cluster-metrics";
import {
  useClusterStatus,
  type RealtimeConnectionStatus,
} from "@/hooks/use-cluster-status";
import { useContainers } from "@/hooks/use-containers";
import { useNodes } from "@/hooks/use-nodes";
import { useLanguage, type MessageKey } from "@/lib/i18n";
import type { ClusterNode } from "@/types/cluster-node";
import type { CloudContainer } from "@/types/container";

type NodeFilter = "all" | "online" | "offline" | "alerts";
type NodeSort = "name" | "usage";
type ViewMode = "grid" | "list";

const EMPTY_NODES: ClusterNode[] = [];
const EMPTY_CONTAINERS: CloudContainer[] = [];

const meterToneClasses = {
  healthy: "bg-blue-500",
  warning: "bg-amber-500",
  critical: "bg-rose-500",
};

function OverviewHeader({
  connectionStatus,
  dataUpdatedAt,
  isRefreshing,
  onCreate,
  onRefresh,
}: {
  connectionStatus: RealtimeConnectionStatus;
  dataUpdatedAt: number;
  isRefreshing: boolean;
  onCreate: () => void;
  onRefresh: () => void;
}) {
  const { locale, t } = useLanguage();
  const timeFormatter = new Intl.DateTimeFormat(locale, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  const updateLabel = dataUpdatedAt
    ? `${t("updatedAt")} ${timeFormatter.format(dataUpdatedAt)}`
    : t("awaitingFirstRead");

  return (
    <header className="flex flex-col gap-5 border-b border-slate-200 pb-6 dark:border-slate-800 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <div className="mb-2 flex items-center gap-2.5">
          <span className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600 dark:text-blue-400">CloudBox</span>
          <span className="size-1 rounded-full bg-slate-300" />
          <span aria-live="polite" className="flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
            <span aria-hidden="true" className={`size-2 rounded-full ${connectionStatus === "connected" ? "bg-emerald-500" : "animate-pulse bg-amber-500"}`} />
            {connectionStatus === "connected"
              ? t("realtimeConnected")
              : connectionStatus === "reconnecting"
                ? t("reconnecting")
                : t("connecting")}
          </span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-950 dark:text-white sm:text-4xl">{t("clusterDashboard")}</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 sm:text-base">{t("operationalDescription")} <span className="hidden lg:inline">· {updateLabel}</span></p>
      </div>
      <div className="flex flex-col-reverse gap-2 sm:flex-row">
        <button
          className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 shadow-sm transition hover:border-blue-300 hover:text-blue-700 disabled:cursor-wait disabled:opacity-60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          disabled={isRefreshing}
          onClick={onRefresh}
          type="button"
        >
          <RefreshIcon className={`size-4 ${isRefreshing ? "animate-spin" : ""}`} />
          {isRefreshing ? t("refreshing") : t("refresh")}
        </button>
        <button
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm shadow-blue-200 transition hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 dark:shadow-none"
          onClick={onCreate}
          type="button"
        >
          <PlusIcon className="size-4" /> {t("newContainer")}
        </button>
      </div>
    </header>
  );
}

function SummaryCard({
  detail,
  icon,
  label,
  percentage,
  tone = "neutral",
  value,
}: {
  detail?: string;
  icon: ReactNode;
  label: string;
  percentage?: number;
  tone?: "neutral" | "success" | "warning";
  value: string | number;
}) {
  const toneClasses = {
    neutral: "border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900",
    success: "border-emerald-200 bg-emerald-50/60 dark:border-emerald-900 dark:bg-emerald-950/25",
    warning: "border-amber-200 bg-amber-50/70 dark:border-amber-900 dark:bg-amber-950/25",
  };
  const iconClasses = {
    neutral: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300",
    success: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/60 dark:text-emerald-300",
    warning: "bg-amber-100 text-amber-700 dark:bg-amber-900/60 dark:text-amber-300",
  };
  const meterTone = percentage === undefined ? "healthy" : utilizationTone(percentage);

  return (
    <div className={`rounded-2xl border p-4 shadow-sm ${toneClasses[tone]}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">{label}</p>
          <p className="mt-1 text-2xl font-bold tabular-nums tracking-tight text-slate-950 dark:text-white">{value}</p>
        </div>
        <span className={`grid size-9 place-items-center rounded-xl ${iconClasses[tone]}`}>{icon}</span>
      </div>
      {percentage !== undefined ? (
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
          <div className={`h-full rounded-full ${meterToneClasses[meterTone]}`} style={{ width: `${percentage}%` }} />
        </div>
      ) : detail ? (
        <p className="mt-2 truncate text-[11px] text-slate-400">{detail}</p>
      ) : null}
    </div>
  );
}

function getAlertReasons(node: ClusterNode): MessageKey[] {
  const reasons: MessageKey[] = [];
  if (node.status === "OFFLINE") reasons.push("offlineNodeAlert");
  if (node.temperatureCelsius !== null && node.temperatureCelsius >= MAX_SAFE_TEMPERATURE_CELSIUS) reasons.push("temperatureAlert");
  if (usedPercentage(node.cpuFree, node.cpuTotal) >= CRITICAL_USAGE_PERCENTAGE) reasons.push("cpuAlert");
  if (usedPercentage(node.ramFreeMb, node.ramTotalMb) >= CRITICAL_USAGE_PERCENTAGE) reasons.push("ramAlert");
  if (usedPercentage(node.diskFreeMb, node.diskTotalMb) >= CRITICAL_USAGE_PERCENTAGE) reasons.push("diskAlert");
  return reasons;
}

function LoadingState() {
  const { t } = useLanguage();
  return (
    <div aria-label={t("loadingNodes")} className="space-y-6" role="status">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((item) => <div className="h-28 animate-pulse rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900" key={item} />)}
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {[0, 1, 2].map((item) => <div className="h-72 animate-pulse rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900" key={item} />)}
      </div>
    </div>
  );
}

export function NodesOverview() {
  const { locale, t } = useLanguage();
  const { connectionStatus } = useClusterStatus();
  const nodesQuery = useNodes();
  const containersQuery = useContainers();
  const [filter, setFilter] = useState<NodeFilter>("all");
  const [sort, setSort] = useState<NodeSort>("name");
  const [view, setView] = useState<ViewMode>("grid");
  const [search, setSearch] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);

  const nodes = nodesQuery.data ?? EMPTY_NODES;
  const containers = containersQuery.data ?? EMPTY_CONTAINERS;
  const onlineNodes = nodes.filter((node) => node.status === "ONLINE");
  const offlineCount = nodes.length - onlineNodes.length;
  const alertNodes = nodes.filter(nodeHasAlert);
  const activeContainers = containers.filter((container) => ["PENDING", "SCHEDULED", "RUNNING", "STOPPING"].includes(container.status));
  const cpu = aggregateResource(onlineNodes, (node) => node.cpuFree, (node) => node.cpuTotal);
  const ram = aggregateResource(onlineNodes, (node) => node.ramFreeMb, (node) => node.ramTotalMb);
  const disk = aggregateResource(onlineNodes, (node) => node.diskFreeMb, (node) => node.diskTotalMb);
  const containersByNode = useMemo(() => {
    const counts = new Map<string, number>();
    for (const container of containers) {
      if (container.nodeId && container.status !== "REMOVED") {
        counts.set(container.nodeId, (counts.get(container.nodeId) ?? 0) + 1);
      }
    }
    return counts;
  }, [containers]);

  const visibleNodes = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase(locale);
    const filtered = nodes.filter((node) => {
      const matchesSearch = !normalizedSearch || node.name.toLocaleLowerCase(locale).includes(normalizedSearch) || node.id.toLocaleLowerCase(locale).includes(normalizedSearch);
      const matchesFilter =
        filter === "all" ||
        (filter === "online" && node.status === "ONLINE") ||
        (filter === "offline" && node.status === "OFFLINE") ||
        (filter === "alerts" && nodeHasAlert(node));
      return matchesSearch && matchesFilter;
    });

    return filtered.sort((first, second) => {
      if (sort === "name") return first.name.localeCompare(second.name, locale);
      const firstUsage = Math.max(usedPercentage(first.cpuFree, first.cpuTotal), usedPercentage(first.ramFreeMb, first.ramTotalMb), usedPercentage(first.diskFreeMb, first.diskTotalMb));
      const secondUsage = Math.max(usedPercentage(second.cpuFree, second.cpuTotal), usedPercentage(second.ramFreeMb, second.ramTotalMb), usedPercentage(second.diskFreeMb, second.diskTotalMb));
      return secondUsage - firstUsage;
    });
  }, [filter, locale, nodes, search, sort]);

  const dataUpdatedAt = Math.max(nodesQuery.dataUpdatedAt, containersQuery.dataUpdatedAt);
  const isRefreshing = nodesQuery.isFetching || containersQuery.isFetching;

  function refresh() {
    void Promise.all([nodesQuery.refetch(), containersQuery.refetch()]);
  }

  function clearFilters() {
    setFilter("all");
    setSearch("");
  }

  if (nodesQuery.isPending) {
    return (
      <div className="space-y-7">
        <OverviewHeader connectionStatus={connectionStatus} dataUpdatedAt={dataUpdatedAt} isRefreshing={isRefreshing} onCreate={() => setIsModalOpen(true)} onRefresh={refresh} />
        <LoadingState />
      </div>
    );
  }

  if (nodesQuery.isError && !nodesQuery.data) {
    return (
      <div className="space-y-7">
        <OverviewHeader connectionStatus={connectionStatus} dataUpdatedAt={dataUpdatedAt} isRefreshing={isRefreshing} onCreate={() => setIsModalOpen(true)} onRefresh={refresh} />
        <section className="rounded-2xl border border-rose-200 bg-rose-50 p-6 dark:border-rose-900 dark:bg-rose-950/30 sm:p-8" role="alert">
          <p className="font-bold text-rose-900 dark:text-rose-200">{t("orchestratorError")}</p>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-rose-700 dark:text-rose-300">{t("orchestratorHelp")} {nodesQuery.error instanceof Error ? nodesQuery.error.message : ""}</p>
          <button className="mt-5 rounded-xl bg-rose-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-rose-800" onClick={refresh} type="button">{t("tryAgain")}</button>
        </section>
      </div>
    );
  }

  const filters: Array<{ key: NodeFilter; label: string; count?: number }> = [
    { key: "all", label: t("allNodes"), count: nodes.length },
    { key: "online", label: t("onlineNodesFilter"), count: onlineNodes.length },
    { key: "offline", label: t("offlineNodesFilter"), count: offlineCount },
    { key: "alerts", label: t("alertsFilter"), count: alertNodes.length },
  ];

  return (
    <div className="space-y-7">
      <OverviewHeader connectionStatus={connectionStatus} dataUpdatedAt={dataUpdatedAt} isRefreshing={isRefreshing} onCreate={() => setIsModalOpen(true)} onRefresh={refresh} />

      <section aria-label={t("clusterSummary")}>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-bold text-slate-800 dark:text-slate-200">{t("clusterCapacity")}</h2>
          <span className="text-xs text-slate-400">{onlineNodes.length} / {nodes.length} {t("onlineNodesFilter").toLocaleLowerCase(locale)}</span>
        </div>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
          <SummaryCard detail={`${nodes.length} ${t("registeredNodes")}`} icon={<ServerIcon className="size-5" />} label={t("onlineNodes")} tone={offlineCount > 0 ? "warning" : "success"} value={`${onlineNodes.length}/${nodes.length}`} />
          <SummaryCard detail={containersQuery.isError ? t("loadContainersFailed") : undefined} icon={<BoxIcon className="size-5" />} label={t("activeContainers")} value={containersQuery.isPending || containersQuery.isError ? "—" : activeContainers.length} />
          <SummaryCard icon={<ActivityIcon className="size-5" />} label={t("cpuUsage")} percentage={onlineNodes.length ? cpu.usedPercentage : undefined} value={onlineNodes.length ? `${Math.round(cpu.usedPercentage)}%` : "—"} />
          <SummaryCard icon={<ActivityIcon className="size-5" />} label={t("ramUsage")} percentage={onlineNodes.length ? ram.usedPercentage : undefined} value={onlineNodes.length ? `${Math.round(ram.usedPercentage)}%` : "—"} />
          <SummaryCard icon={<ActivityIcon className="size-5" />} label={t("diskUsage")} percentage={onlineNodes.length ? disk.usedPercentage : undefined} value={onlineNodes.length ? `${Math.round(disk.usedPercentage)}%` : "—"} />
          <SummaryCard detail={alertNodes.length ? t("alertsDescription") : t("healthyClusterDescription")} icon={alertNodes.length ? <AlertIcon className="size-5" /> : <CheckCircleIcon className="size-5" />} label={t("activeAlerts")} tone={alertNodes.length ? "warning" : "success"} value={alertNodes.length} />
        </div>
      </section>

      {nodesQuery.isRefetchError ? (
        <div className="flex flex-col justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200 sm:flex-row sm:items-center" role="alert">
          <span>{t("staleData")}</span><button className="font-bold underline underline-offset-4" onClick={refresh} type="button">{t("updateNow")}</button>
        </div>
      ) : null}

      <section className={`rounded-2xl border p-4 sm:p-5 ${alertNodes.length ? "border-amber-200 bg-amber-50/60 dark:border-amber-900 dark:bg-amber-950/20" : "border-emerald-200 bg-emerald-50/50 dark:border-emerald-900 dark:bg-emerald-950/20"}`}>
        <div className="flex items-start gap-3">
          <span className={`grid size-9 shrink-0 place-items-center rounded-xl ${alertNodes.length ? "bg-amber-100 text-amber-700 dark:bg-amber-900/60 dark:text-amber-300" : "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/60 dark:text-emerald-300"}`}>
            {alertNodes.length ? <AlertIcon className="size-5" /> : <CheckCircleIcon className="size-5" />}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div><h2 className="font-bold text-slate-950 dark:text-white">{alertNodes.length ? t("alertsTitle") : t("healthyCluster")}</h2><p className="mt-1 text-sm text-slate-600 dark:text-slate-300">{alertNodes.length ? t("alertsDescription") : t("healthyClusterDescription")}</p></div>
              {alertNodes.length ? <button className="shrink-0 text-left text-sm font-bold text-amber-800 underline-offset-4 hover:underline dark:text-amber-300" onClick={() => setFilter("alerts")} type="button">{t("viewAlerts")} ({alertNodes.length})</button> : null}
            </div>
            {alertNodes.length ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {alertNodes.slice(0, 4).map((node) => <span className="rounded-lg border border-amber-200/80 bg-white/70 px-2.5 py-1 text-xs text-amber-900 dark:border-amber-900 dark:bg-slate-900/60 dark:text-amber-200" key={node.id}><strong>{node.name}</strong> · {getAlertReasons(node).map((reason) => t(reason)).join(" · ")}</span>)}
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <section aria-label={t("registeredNodesLabel")}>
        <div className="mb-4 space-y-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex gap-1 overflow-x-auto pb-1" role="group" aria-label={t("clusterSummary")}>
              {filters.map((item) => <button aria-pressed={filter === item.key} className={`whitespace-nowrap rounded-xl px-3 py-2 text-xs font-bold transition ${filter === item.key ? "bg-slate-900 text-white dark:bg-white dark:text-slate-950" : "border border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"}`} key={item.key} onClick={() => setFilter(item.key)} type="button">{item.label} <span className={filter === item.key ? "text-slate-300 dark:text-slate-500" : "text-slate-400"}>{item.count}</span></button>)}
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <label className="relative flex-1 sm:w-64">
                <span className="sr-only">{t("searchNodes")}</span><SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <input className="h-10 w-full rounded-xl border border-slate-200 bg-white pl-9 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-3 focus:ring-blue-100 dark:border-slate-800 dark:bg-slate-900 dark:text-white dark:focus:ring-blue-950" onChange={(event) => setSearch(event.target.value)} placeholder={t("searchNodes")} type="search" value={search} />
              </label>
              <select aria-label={t("sortBy")} className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-600 outline-none focus:border-blue-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300" onChange={(event) => setSort(event.target.value as NodeSort)} value={sort}><option value="name">{t("sortName")}</option><option value="usage">{t("sortHighestUsage")}</option></select>
              <div className="flex rounded-xl border border-slate-200 bg-white p-1 dark:border-slate-800 dark:bg-slate-900">
                <button aria-label={t("gridView")} aria-pressed={view === "grid"} className={`grid size-8 place-items-center rounded-lg ${view === "grid" ? "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-white" : "text-slate-400"}`} onClick={() => setView("grid")} type="button"><GridIcon className="size-4" /></button>
                <button aria-label={t("listView")} aria-pressed={view === "list"} className={`grid size-8 place-items-center rounded-lg ${view === "list" ? "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-white" : "text-slate-400"}`} onClick={() => setView("list")} type="button"><ListIcon className="size-4" /></button>
              </div>
            </div>
          </div>
        </div>

        {nodes.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900/60"><ServerIcon className="mx-auto size-9 text-slate-300" /><h2 className="mt-4 text-lg font-bold text-slate-900 dark:text-white">{t("noNodes")}</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">{t("noNodesDescription")}</p></div>
        ) : visibleNodes.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-12 text-center dark:border-slate-700 dark:bg-slate-900/60"><p className="text-sm font-semibold text-slate-600 dark:text-slate-300">{t("noMatchingNodes")}</p><button className="mt-3 text-sm font-bold text-blue-600 hover:underline" onClick={clearFilters} type="button">{t("clearFilters")}</button></div>
        ) : (
          <div className={view === "grid" ? "grid gap-4 md:grid-cols-2 xl:grid-cols-3" : "space-y-3"}>
            {visibleNodes.map((node) => <NodeCard containerCount={containersByNode.get(node.id) ?? 0} key={node.id} node={node} variant={view} />)}
          </div>
        )}
      </section>

      {isModalOpen ? <CreateContainerModal nodes={nodes} onClose={() => setIsModalOpen(false)} /> : null}
    </div>
  );
}
