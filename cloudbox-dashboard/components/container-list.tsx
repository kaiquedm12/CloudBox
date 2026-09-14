"use client";

import { useState } from "react";
import { BoxIcon } from "@/components/ui-icons";
import { useLanguage, type MessageKey } from "@/lib/i18n";
import type { CloudContainer, ContainerEndpoint, ContainerStatus } from "@/types/container";

const statusLabelKeys: Record<ContainerStatus, MessageKey> = {
  PENDING: "pending",
  SCHEDULED: "scheduled",
  RUNNING: "running",
  STOPPING: "stopping",
  STOPPED: "stopped",
  REMOVING: "removing",
  REMOVED: "removed",
  ERROR: "error",
  FAILED: "failed",
};

const statusClasses: Record<ContainerStatus, string> = {
  PENDING: "bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:ring-amber-900",
  SCHEDULED: "bg-blue-50 text-blue-700 ring-blue-200 dark:bg-blue-950/50 dark:text-blue-300 dark:ring-blue-900",
  RUNNING: "bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950/50 dark:text-emerald-300 dark:ring-emerald-900",
  STOPPING: "bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:ring-amber-900",
  STOPPED: "bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700",
  REMOVING: "bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:ring-amber-900",
  REMOVED: "bg-slate-100 text-slate-500 ring-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:ring-slate-700",
  ERROR: "bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-950/50 dark:text-rose-300 dark:ring-rose-900",
  FAILED: "bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-950/50 dark:text-rose-300 dark:ring-rose-900",
};

function formatCreatedAt(value: string, formatter: Intl.DateTimeFormat, unavailable: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? unavailable : formatter.format(date);
}

function StatusBadge({ status }: { status: ContainerStatus }) {
  const { t } = useLanguage();
  return <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${statusClasses[status]}`}>{t(statusLabelKeys[status])}</span>;
}

function ContainerActions({
  busyAction,
  busyContainerId,
  container,
  onRemove,
  onStop,
}: {
  busyAction?: "remove" | "stop";
  busyContainerId?: string;
  container: CloudContainer;
  onRemove?: (containerId: string) => void;
  onStop?: (containerId: string) => void;
}) {
  const { t } = useLanguage();
  const busy = busyContainerId === container.id;
  const removable = ["RUNNING", "STOPPED", "ERROR", "FAILED"].includes(container.status);
  if (!onStop && !onRemove) return null;

  function remove() {
    if (window.confirm(t("confirmRemoveContainer"))) onRemove?.(container.id);
  }

  return (
    <div className="flex flex-wrap justify-end gap-2">
      {onStop ? (
        <button className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-bold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300" disabled={busy || container.status !== "RUNNING"} onClick={() => onStop(container.id)} type="button">{busy && busyAction === "stop" ? t("stoppingAction") : t("stop")}</button>
      ) : null}
      {onRemove ? (
        <button className="rounded-lg border border-rose-200 bg-white px-3 py-2 text-xs font-bold text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-rose-900 dark:bg-slate-900 dark:text-rose-300" disabled={busy || !removable} onClick={remove} type="button">{busy && busyAction === "remove" ? t("removingAction") : t("remove")}</button>
      ) : null}
    </div>
  );
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
  const { t } = useLanguage();
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
    <li className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-950/40 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="font-mono text-sm font-semibold text-slate-800 dark:text-slate-200">
          {t("endpointAddress")}: {accessAddress}
        </p>
        <p className="mt-1 text-xs text-slate-500">{t("internalPort")} {endpoint.containerPort} · {endpoint.protocol}</p>
        {isLoopbackAddress(endpoint.address) ? (
          <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">{t("localNodeAccess")}</p>
        ) : null}
      </div>
      {actionsDisabled ? (
        <span className="text-xs font-medium text-amber-700 dark:text-amber-300">{t("accessUnavailable")}</span>
      ) : url ? (
        <a className="w-fit rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white transition hover:bg-blue-700" href={url.toString()} rel="noreferrer" target="_blank">
          {t("openApplication")}
        </a>
      ) : (
        <div className="flex items-center gap-3">
          <button className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800" onClick={() => void copyEndpoint()} type="button">
            {t("copyEndpoint")}
          </button>
          <span aria-live="polite" className={`text-xs ${copyState === "error" ? "text-rose-700" : "text-slate-500"}`}>
            {copyState === "copied" ? t("copied") : copyState === "error" ? t("copyFailed") : ""}
          </span>
        </div>
      )}
    </li>
  );
}

function ContainerEndpoints({
  compact = false,
  container,
  nodeStatuses,
}: {
  compact?: boolean;
  container: CloudContainer;
  nodeStatuses?: Map<string, "ONLINE" | "OFFLINE">;
}) {
  const { t } = useLanguage();
  const endpoints = container.endpoints ?? [];
  const nodeOffline = container.nodeId !== null && nodeStatuses?.get(container.nodeId) === "OFFLINE";
  const waitingForEndpoint = container.status === "PENDING" || container.status === "SCHEDULED";
  const actionsDisabled = nodeOffline || container.status !== "RUNNING";

  return (
    <section aria-label={t("observedEndpoints")} className={compact ? "min-w-64" : "mt-4 border-t border-slate-100 pt-4 dark:border-slate-800"}>
      <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">{t("access")}</h3>
      {nodeOffline ? (
        <p className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-300">
          {t("offlineEndpoints")}
        </p>
      ) : null}
      {endpoints.length > 0 ? (
        <ul className="mt-3 space-y-2">
          {endpoints.map((endpoint, index) => (
            <EndpointRow actionsDisabled={actionsDisabled} endpoint={endpoint} key={`${endpoint.containerPort}-${endpoint.protocol}-${endpoint.address}-${endpoint.hostPort}-${index}`} />
          ))}
        </ul>
      ) : (
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          {waitingForEndpoint
            ? t("waitingForEndpoints")
            : container.status === "RUNNING"
              ? t("runningWithoutEndpoint")
              : t("noEndpoint")}
        </p>
      )}
    </section>
  );
}

export function ContainerList({
  containers,
  emptyMessage,
  nodeNames,
  nodeStatuses,
  showNode = false,
  onStop,
  onRemove,
  busyAction,
  busyContainerId,
  actionError,
}: {
  containers: CloudContainer[];
  emptyMessage?: string;
  nodeNames?: Map<string, string>;
  nodeStatuses?: Map<string, "ONLINE" | "OFFLINE">;
  showNode?: boolean;
  onStop?: (containerId: string) => void;
  onRemove?: (containerId: string) => void;
  busyAction?: "remove" | "stop";
  busyContainerId?: string;
  actionError?: string;
}) {
  const { locale, t } = useLanguage();
  const dateFormatter = new Intl.DateTimeFormat(locale, { dateStyle: "short", timeStyle: "short" });
  const resolvedEmptyMessage = emptyMessage ?? t("noContainers");

  function resourceLabel(container: CloudContainer) {
    return `${container.cpuCores} CPU · ${container.memoryMb} MB RAM · ${container.diskMb} MB`;
  }

  function nodeLabel(container: CloudContainer) {
    return container.nodeId ? nodeNames?.get(container.nodeId) ?? container.nodeId : t("awaiting");
  }

  if (containers.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-12 text-center dark:border-slate-700 dark:bg-slate-900/60">
        <BoxIcon className="mx-auto size-8 text-slate-300" />
        <p className="mt-3 text-sm font-semibold text-slate-500 dark:text-slate-400">{resolvedEmptyMessage}</p>
      </div>
    );
  }

  return (
    <div>
      {actionError ? <p className="mb-3 rounded-xl border border-rose-200 bg-rose-50 px-5 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-300" role="alert">{actionError}</p> : null}

      <div className="space-y-3 md:hidden">
        {containers.map((container) => (
          <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900" key={container.id}>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0"><h3 className="truncate font-mono text-sm font-bold text-slate-950 dark:text-white">{container.imageName}</h3><p className="mt-1 truncate text-[11px] text-slate-400" title={container.id}>{container.id}</p></div>
              <StatusBadge status={container.status} />
            </div>
            {container.errorMessage ? <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{container.errorMessage}</p> : null}
            <dl className="mt-4 grid grid-cols-2 gap-3 text-xs">
              {showNode ? <div><dt className="text-slate-400">{t("node")}</dt><dd className="mt-1 truncate font-semibold text-slate-700 dark:text-slate-300">{nodeLabel(container)}</dd></div> : null}
              <div><dt className="text-slate-400">{t("resources")}</dt><dd className="mt-1 font-semibold text-slate-700 dark:text-slate-300">{resourceLabel(container)}</dd></div>
              <div className={showNode ? "col-span-2" : ""}><dt className="text-slate-400">{t("createdAt")}</dt><dd className="mt-1 font-semibold text-slate-700 dark:text-slate-300">{formatCreatedAt(container.createdAt, dateFormatter, t("dateUnavailable"))}</dd></div>
            </dl>
            <ContainerEndpoints container={container} nodeStatuses={nodeStatuses} />
            {(onStop || onRemove) ? <div className="mt-4 border-t border-slate-100 pt-3 dark:border-slate-800"><ContainerActions busyAction={busyAction} busyContainerId={busyContainerId} container={container} onRemove={onRemove} onStop={onStop} /></div> : null}
          </article>
        ))}
      </div>

      <div className="hidden overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900 md:block">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm dark:divide-slate-800">
            <thead className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500 dark:bg-slate-950/50 dark:text-slate-400"><tr>
              <th className="px-5 py-3.5 font-bold">{t("image")}</th>
              {showNode ? <th className="px-5 py-3.5 font-bold">{t("node")}</th> : null}
              <th className="px-5 py-3.5 font-bold">{t("status")}</th>
              <th className="px-5 py-3.5 font-bold">{t("resources")}</th>
              <th className="px-5 py-3.5 font-bold">{t("createdAt")}</th>
              <th className="px-5 py-3.5 font-bold">{t("access")}</th>
              {(onStop || onRemove) ? <th className="px-5 py-3.5 text-right font-bold">{t("actions")}</th> : null}
            </tr></thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {containers.map((container) => (
                <tr className="align-middle transition hover:bg-slate-50/80 dark:hover:bg-slate-800/40" key={container.id}>
                  <td className="px-5 py-4"><p className="font-mono font-bold text-slate-950 dark:text-white">{container.imageName}</p><p className="mt-1 max-w-52 truncate text-xs text-slate-400" title={container.id}>{container.id}</p>{container.errorMessage ? <p className="mt-2 max-w-xs text-xs text-rose-700 dark:text-rose-300">{container.errorMessage}</p> : null}</td>
                  {showNode ? <td className="px-5 py-4 font-semibold text-slate-700 dark:text-slate-300">{nodeLabel(container)}</td> : null}
                  <td className="px-5 py-4"><StatusBadge status={container.status} /></td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-600 dark:text-slate-300">{resourceLabel(container)}</td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-600 dark:text-slate-300">{formatCreatedAt(container.createdAt, dateFormatter, t("dateUnavailable"))}</td>
                  <td className="px-5 py-4"><ContainerEndpoints compact container={container} nodeStatuses={nodeStatuses} /></td>
                  {(onStop || onRemove) ? <td className="px-5 py-4"><ContainerActions busyAction={busyAction} busyContainerId={busyContainerId} container={container} onRemove={onRemove} onStop={onStop} /></td> : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
