"use client";

import { useLanguage, type MessageKey } from "@/lib/i18n";
import type { CloudContainer, ContainerStatus } from "@/types/container";

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
  PENDING: "bg-amber-50 text-amber-700 ring-amber-200",
  SCHEDULED: "bg-blue-50 text-blue-700 ring-blue-200",
  RUNNING: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  STOPPING: "bg-amber-50 text-amber-700 ring-amber-200",
  STOPPED: "bg-slate-100 text-slate-600 ring-slate-200",
  REMOVING: "bg-amber-50 text-amber-700 ring-amber-200",
  REMOVED: "bg-slate-100 text-slate-500 ring-slate-200",
  ERROR: "bg-rose-50 text-rose-700 ring-rose-200",
  FAILED: "bg-rose-50 text-rose-700 ring-rose-200",
};

function formatCreatedAt(value: string, formatter: Intl.DateTimeFormat, unavailable: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? unavailable : formatter.format(date);
}

export function ContainerList({
  containers,
  emptyMessage,
  nodeNames,
  showNode = false,
  onStop,
  onRemove,
  busyContainerId,
  actionError,
}: {
  containers: CloudContainer[];
  emptyMessage?: string;
  nodeNames?: Map<string, string>;
  showNode?: boolean;
  onStop?: (containerId: string) => void;
  onRemove?: (containerId: string) => void;
  busyContainerId?: string;
  actionError?: string;
}) {
  const { locale, t } = useLanguage();
  const dateFormatter = new Intl.DateTimeFormat(locale, { dateStyle: "short", timeStyle: "short" });
  const resolvedEmptyMessage = emptyMessage ?? t("noContainers");

  if (containers.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-12 text-center text-sm text-slate-500">
        {resolvedEmptyMessage}
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      {actionError ? <p className="border-b border-rose-200 bg-rose-50 px-5 py-3 text-sm text-rose-700" role="alert">{actionError}</p> : null}
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr>
            <th className="px-5 py-3 font-semibold">{t("image")}</th>
            {showNode ? <th className="px-5 py-3 font-semibold">{t("node")}</th> : null}
            <th className="px-5 py-3 font-semibold">{t("status")}</th>
            <th className="px-5 py-3 font-semibold">{t("resources")}</th>
            <th className="px-5 py-3 font-semibold">{t("createdAt")}</th>
            {(onStop || onRemove) ? <th className="px-5 py-3 text-right font-semibold">{t("actions")}</th> : null}
          </tr></thead>
          <tbody className="divide-y divide-slate-100">
            {containers.map((container) => {
              const busy = busyContainerId === container.id;
              const removable = ["RUNNING", "STOPPED", "ERROR", "FAILED"].includes(container.status);
              return <tr className="align-middle" key={container.id}>
                <td className="px-5 py-4"><p className="font-mono font-semibold text-slate-950">{container.imageName}</p><p className="mt-1 max-w-52 truncate text-xs text-slate-400" title={container.id}>{container.id}</p>{container.errorMessage ? <p className="mt-2 text-xs text-rose-700">{container.errorMessage}</p> : null}</td>
                {showNode ? <td className="px-5 py-4 font-medium text-slate-700">{container.nodeId ? nodeNames?.get(container.nodeId) ?? container.nodeId : t("awaiting")}</td> : null}
                <td className="px-5 py-4"><span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClasses[container.status]}`}>{t(statusLabelKeys[container.status])}</span></td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-600">{container.cpuCores} CPU · {container.memoryMb} MB RAM · {container.diskMb} MB disco</td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-600">{formatCreatedAt(container.createdAt, dateFormatter, t("dateUnavailable"))}</td>
                {(onStop || onRemove) ? <td className="px-5 py-4"><div className="flex justify-end gap-2">
                  {onStop ? <button className="rounded-lg border border-slate-300 px-3 py-2 font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-40" disabled={busy || container.status !== "RUNNING"} onClick={() => onStop(container.id)} type="button">{t("stop")}</button> : null}
                  {onRemove ? <button className="rounded-lg border border-rose-200 px-3 py-2 font-semibold text-rose-700 disabled:cursor-not-allowed disabled:opacity-40" disabled={busy || !removable} onClick={() => onRemove(container.id)} type="button">{t("remove")}</button> : null}
                </div></td> : null}
              </tr>;
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
