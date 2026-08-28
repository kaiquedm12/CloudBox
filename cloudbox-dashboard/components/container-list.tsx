import type { CloudContainer, ContainerStatus } from "@/types/container";

const statusLabels: Record<ContainerStatus, string> = {
  PENDING: "Pendente",
  SCHEDULED: "Agendado",
  RUNNING: "Em execução",
  STOPPED: "Parado",
  ERROR: "Erro",
  FAILED: "Falhou",
};

const statusClasses: Record<ContainerStatus, string> = {
  PENDING: "bg-amber-50 text-amber-700 ring-amber-200",
  SCHEDULED: "bg-blue-50 text-blue-700 ring-blue-200",
  RUNNING: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  STOPPED: "bg-slate-100 text-slate-600 ring-slate-200",
  ERROR: "bg-rose-50 text-rose-700 ring-rose-200",
  FAILED: "bg-rose-50 text-rose-700 ring-rose-200",
};

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

function formatCreatedAt(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Data indisponível" : dateFormatter.format(date);
}

export function ContainerList({
  containers,
  emptyMessage = "Nenhum container encontrado.",
  nodeNames,
  showNode = false,
}: {
  containers: CloudContainer[];
  emptyMessage?: string;
  nodeNames?: Map<string, string>;
  showNode?: boolean;
}) {
  if (containers.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-12 text-center text-sm text-slate-500">
        {emptyMessage}
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      {containers.map((container) => (
        <article
          className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"
          key={container.id}
        >
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
            <div className="min-w-0">
              <p className="truncate font-mono text-base font-semibold text-slate-950">
                {container.imageName}
              </p>
              <p className="mt-1 truncate text-xs text-slate-400" title={container.id}>
                {container.id}
              </p>
            </div>
            <span
              className={`w-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClasses[container.status]}`}
            >
              {statusLabels[container.status]}
            </span>
          </div>

          <dl className="mt-5 grid grid-cols-2 gap-4 border-t border-slate-100 pt-5 text-sm sm:grid-cols-4">
            <div>
              <dt className="text-slate-400">CPU</dt>
              <dd className="mt-1 font-semibold text-slate-800">
                {container.cpuCores} {container.cpuCores === 1 ? "núcleo" : "núcleos"}
              </dd>
            </div>
            <div>
              <dt className="text-slate-400">RAM</dt>
              <dd className="mt-1 font-semibold text-slate-800">{container.memoryMb} MB</dd>
            </div>
            <div>
              <dt className="text-slate-400">Disco</dt>
              <dd className="mt-1 font-semibold text-slate-800">{container.diskMb} MB</dd>
            </div>
            <div>
              <dt className="text-slate-400">Criado em</dt>
              <dd className="mt-1 font-semibold text-slate-800">
                {formatCreatedAt(container.createdAt)}
              </dd>
            </div>
            {showNode ? (
              <div className="col-span-2 sm:col-span-4">
                <dt className="text-slate-400">Nó alocado</dt>
                <dd className="mt-1 font-semibold text-slate-800">
                  {container.nodeId
                    ? nodeNames?.get(container.nodeId) ?? container.nodeId
                    : "Aguardando agendamento"}
                </dd>
              </div>
            ) : null}
          </dl>

          {container.errorMessage ? (
            <p className="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {container.errorMessage}
            </p>
          ) : null}
        </article>
      ))}
    </div>
  );
}
