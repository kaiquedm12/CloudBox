"use client";

import { useMemo, useState } from "react";
import { ContainerList } from "@/components/container-list";
import { CreateContainerModal } from "@/components/create-container-modal";
import { useContainers, useRemoveContainer, useStopContainer } from "@/hooks/use-containers";
import { useNodes } from "@/hooks/use-nodes";
import { useLanguage } from "@/lib/i18n";

export function ContainersOverview() {
  const { t } = useLanguage();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const containersQuery = useContainers();
  const nodesQuery = useNodes();
  const stopContainer = useStopContainer();
  const removeContainer = useRemoveContainer();
  const containers = [...(containersQuery.data ?? [])].sort(
    (first, second) =>
      new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime(),
  );
  const nodes = nodesQuery.data ?? [];
  const nodeNames = useMemo(
    () => new Map(nodes.map((node) => [node.id, node.name])),
    [nodes],
  );

  return (
    <div className="space-y-8">
      <div className="relative overflow-hidden rounded-3xl border border-blue-100 bg-white/70 p-6 shadow-sm sm:p-8 dark:border-blue-950 dark:bg-slate-900/60">
        <div aria-hidden="true" className="absolute -right-16 -top-20 size-52 rounded-full bg-blue-100/70 blur-3xl dark:bg-blue-900/20" />
        <div className="relative flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
              CloudBox
            </p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 sm:text-4xl">
              Containers
            </h1>
            <p className="mt-3 max-w-2xl leading-7 text-slate-600">
              {t("containersDescription")}
            </p>
          </div>
          <button
            className="shrink-0 rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white shadow-sm shadow-blue-200 transition hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            onClick={() => setIsModalOpen(true)}
            type="button"
          >
            {t("newContainer")}
          </button>
        </div>
      </div>

      {containersQuery.isPending ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
          {t("loadingContainers")}
        </div>
      ) : containersQuery.isError ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6" role="alert">
          <p className="font-semibold text-rose-900">{t("loadContainersFailed")}</p>
          <button
            className="mt-3 text-sm font-semibold text-rose-700 underline underline-offset-4"
            onClick={() => void containersQuery.refetch()}
            type="button"
          >
            {t("tryAgain")}
          </button>
        </div>
      ) : (
        <ContainerList
          containers={containers}
          nodeNames={nodeNames}
          showNode
          actionError={stopContainer.error?.message ?? removeContainer.error?.message}
          busyContainerId={
            stopContainer.isPending
              ? stopContainer.variables
              : removeContainer.isPending
                ? removeContainer.variables
                : undefined
          }
          onStop={(id) => stopContainer.mutate(id)}
          onRemove={(id) => removeContainer.mutate(id)}
        />
      )}

      {isModalOpen ? (
        <CreateContainerModal nodes={nodes} onClose={() => setIsModalOpen(false)} />
      ) : null}
    </div>
  );
}
