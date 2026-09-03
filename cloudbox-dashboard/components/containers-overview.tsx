"use client";

import { useMemo, useState } from "react";
import { ContainerList } from "@/components/container-list";
import { CreateContainerModal } from "@/components/create-container-modal";
import { BoxIcon, PlusIcon } from "@/components/ui-icons";
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
    <div className="space-y-7">
      <header className="flex flex-col justify-between gap-5 border-b border-slate-200 pb-6 dark:border-slate-800 sm:flex-row sm:items-end">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-blue-600 dark:text-blue-400">
            <BoxIcon className="size-4" /> CloudBox
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950 dark:text-white sm:text-4xl">
            Containers
          </h1>
          <p className="mt-2 max-w-2xl text-sm text-slate-500 dark:text-slate-400 sm:text-base">
            {t("containersDescription")}
          </p>
        </div>
        <button
          className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm shadow-blue-200 transition hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 dark:shadow-none"
          onClick={() => setIsModalOpen(true)}
          type="button"
        >
          <PlusIcon className="size-4" />
          {t("newContainer")}
        </button>
      </header>

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
          busyAction={stopContainer.isPending ? "stop" : removeContainer.isPending ? "remove" : undefined}
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
