"use client";

import { NodeCard } from "@/components/node-card";
import {
  useClusterStatus,
  type RealtimeConnectionStatus,
} from "@/hooks/use-cluster-status";
import { useNodes } from "@/hooks/use-nodes";
import { useLanguage } from "@/lib/i18n";

function OverviewHeader({
  dataUpdatedAt,
  connectionStatus,
}: {
  dataUpdatedAt: number;
  connectionStatus: RealtimeConnectionStatus;
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
    <div className="relative overflow-hidden rounded-3xl border border-blue-100 bg-white/70 p-6 shadow-sm backdrop-blur sm:p-8 dark:border-blue-950 dark:bg-slate-900/60">
      <div aria-hidden="true" className="absolute -right-16 -top-20 size-52 rounded-full bg-blue-100/70 blur-3xl dark:bg-blue-900/20" />
      <div className="relative flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
            {t("overview")}
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 sm:text-4xl">
            {t("clusterNodes")}
          </h1>
          <p className="mt-3 max-w-2xl leading-7 text-slate-600">
            {t("clusterDescription")}
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
              ? t("realtimeConnected")
              : connectionStatus === "reconnecting"
                ? t("reconnecting")
                : t("connecting")}
            <span className="ml-2 hidden text-xs text-slate-400 lg:inline">
              · {updateLabel}
            </span>
          </span>
        </div>
      </div>
    </div>
  );
}

function LoadingState() {
  const { t } = useLanguage();
  return (
    <div
      aria-label={t("loadingNodes")}
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
  const { locale, t } = useLanguage();
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
                {t("orchestratorError")}
              </p>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-rose-700">
                {t("orchestratorHelp")} {error instanceof Error ? error.message : ""}
              </p>
            </div>
            <button
              className="shrink-0 rounded-xl bg-rose-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-700"
              onClick={() => void refetch()}
              type="button"
            >
              {t("tryAgain")}
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

    return first.name.localeCompare(second.name, locale);
  });
  const onlineCount = nodes.filter((node) => node.status === "ONLINE").length;
  const offlineCount = nodes.length - onlineCount;

  return (
    <div className="space-y-7">
      <OverviewHeader
        connectionStatus={connectionStatus}
        dataUpdatedAt={dataUpdatedAt}
      />

      <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-[repeat(3,minmax(0,1fr))_auto]" aria-label={t("clusterSummary")}>
        <div className="rounded-2xl border border-slate-200 bg-white px-5 py-4 text-sm text-slate-600 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
          <strong className="mr-1.5 text-slate-950">{nodes.length}</strong>
          {nodes.length === 1 ? t("registeredNode") : t("registeredNodes")}
        </div>
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm text-emerald-700 transition hover:-translate-y-0.5 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300">
          <strong className="mr-1.5">{onlineCount}</strong> online
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-100 px-5 py-4 text-sm text-slate-600 transition hover:-translate-y-0.5">
          <strong className="mr-1.5">{offlineCount}</strong> offline
        </div>
        <div className="hidden items-center px-3 text-xs text-slate-400 lg:flex">
          {t("websocketUpdates")}
        </div>
      </div>

      {isRefetchError ? (
        <div
          className="flex flex-col justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 sm:flex-row sm:items-center"
          role="alert"
        >
          <span>{t("staleData")}</span>
          <button
            className="font-semibold underline underline-offset-4"
            onClick={() => void refetch()}
            type="button"
          >
            {t("updateNow")}
          </button>
        </div>
      ) : null}

      {nodes.length > 0 ? (
        <section
          aria-label={t("registeredNodesLabel")}
          className="grid gap-5 md:grid-cols-2 xl:grid-cols-3"
        >
          {nodes.map((node) => (
            <NodeCard key={node.id} node={node} />
          ))}
        </section>
      ) : (
        <section className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-14 text-center">
          <div className="mx-auto grid size-12 place-items-center rounded-2xl bg-slate-100 text-xs font-bold text-slate-500">
            {t("nodeAbbreviation")}
          </div>
          <h2 className="mt-4 text-lg font-semibold text-slate-900">
            {t("noNodes")}
          </h2>
          <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
            {t("noNodesDescription")}
          </p>
        </section>
      )}
    </div>
  );
}
