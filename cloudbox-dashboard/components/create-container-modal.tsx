"use client";

import { useEffect, useId, useState, type FormEvent, type MouseEvent } from "react";
import { useCreateContainer } from "@/hooks/use-containers";
import { ApiError } from "@/lib/api";
import { createContainerSchema } from "@/lib/container-schema";
import type { ClusterNode } from "@/types/cluster-node";
import type {
  CloudContainer,
  CreateContainerRequest,
  PortExposure,
  PortProtocol,
} from "@/types/container";

type FieldErrors = Partial<Record<keyof CreateContainerRequest, string>>;
type PortFormValue = {
  containerPort: string;
  hostPort: string;
  protocol: PortProtocol;
  exposure: PortExposure;
  bindAddress: string;
};

const emptyPort = (): PortFormValue => ({
  containerPort: "",
  hostPort: "",
  protocol: "TCP",
  exposure: "INTERNAL",
  bindAddress: "",
});

function InputField({
  error,
  id,
  label,
  ...inputProps
}: {
  error?: string;
  id: string;
  label: string;
} & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="mb-2 block text-sm font-medium text-slate-700" htmlFor={id}>
        {label}
      </label>
      <input
        {...inputProps}
        aria-describedby={error ? `${id}-error` : undefined}
        aria-invalid={Boolean(error)}
        className={`w-full rounded-xl border bg-white px-4 py-3 text-slate-950 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
          error
            ? "border-rose-400 focus:border-rose-500 focus:ring-rose-100"
            : "border-slate-300 focus:border-blue-500 focus:ring-blue-100"
        }`}
        id={id}
      />
      {error ? (
        <p className="mt-1.5 text-xs text-rose-600" id={`${id}-error`}>
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function CreateContainerModal({
  nodes,
  onClose,
}: {
  nodes: ClusterNode[];
  onClose: () => void;
}) {
  const titleId = useId();
  const mutation = useCreateContainer();
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [portErrors, setPortErrors] = useState<Record<number, string>>({});
  const [ports, setPorts] = useState<PortFormValue[]>([]);
  const [createdContainer, setCreatedContainer] = useState<CloudContainer | null>(null);

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape" && !mutation.isPending) {
        onClose();
      }
    }

    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [mutation.isPending, onClose]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.reset();
    setCreatedContainer(null);

    const formData = new FormData(event.currentTarget);
    const parsed = createContainerSchema.safeParse({
      imageName: formData.get("imageName"),
      cpuCores: formData.get("cpuCores"),
      memoryMb: formData.get("memoryMb"),
      diskMb: formData.get("diskMb"),
      ports,
    });

    if (!parsed.success) {
      const errors: FieldErrors = {};
      const nextPortErrors: Record<number, string> = {};
      for (const issue of parsed.error.issues) {
        if (issue.path[0] === "ports" && typeof issue.path[1] === "number") {
          nextPortErrors[issue.path[1]] ??= issue.message;
          continue;
        }
        const field = issue.path[0] as keyof CreateContainerRequest;
        errors[field] ??= issue.message;
      }
      setFieldErrors(errors);
      setPortErrors(nextPortErrors);
      return;
    }

    setFieldErrors({});
    setPortErrors({});
    try {
      const container = await mutation.mutateAsync(parsed.data);
      setCreatedContainer(container);
    } catch {
      // O estado da mutation renderiza o erro abaixo do formulário.
    }
  }

  function closeFromBackdrop(event: MouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget && !mutation.isPending) {
      onClose();
    }
  }

  function updatePort(index: number, patch: Partial<PortFormValue>) {
    setPorts((current) =>
      current.map((port, currentIndex) =>
        currentIndex === index ? { ...port, ...patch } : port,
      ),
    );
    setPortErrors((current) => {
      const { [index]: _removed, ...remaining } = current;
      return remaining;
    });
  }

  const chosenNode = createdContainer?.nodeId
    ? nodes.find((node) => node.id === createdContainer.nodeId)
    : undefined;
  const requestError = mutation.error;
  const errorMessage =
    requestError instanceof ApiError && requestError.status === 409
      ? "Não há nenhum nó online com CPU e RAM suficientes para esta solicitação. Reduza os recursos ou tente novamente mais tarde."
      : requestError instanceof Error
        ? requestError.message
        : "Não foi possível solicitar o container.";

  return (
    <div
      className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-sm"
      onMouseDown={closeFromBackdrop}
    >
      <section
        aria-labelledby={titleId}
        aria-modal="true"
        className="my-6 w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl sm:p-8"
        role="dialog"
      >
        <div className="flex items-start justify-between gap-6">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-600">
              Agendador CloudBox
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950" id={titleId}>
              Novo container
            </h2>
          </div>
          <button
            aria-label="Fechar"
            className="grid size-9 shrink-0 place-items-center rounded-lg text-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            disabled={mutation.isPending}
            onClick={onClose}
            type="button"
          >
            ×
          </button>
        </div>

        {createdContainer ? (
          <div className="mt-7">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5" role="status">
              <p className="font-semibold text-emerald-900">Container criado com sucesso</p>
              <p className="mt-2 text-sm leading-6 text-emerald-700">
                A imagem <strong>{createdContainer.imageName}</strong> foi alocada no nó{" "}
                <strong>{chosenNode?.name ?? createdContainer.nodeId ?? "selecionado"}</strong>.
              </p>
              <p className="mt-3 break-all font-mono text-xs text-emerald-600">
                {createdContainer.id}
              </p>
            </div>
            <div className="mt-5 flex justify-end">
              <button
                className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700"
                onClick={onClose}
                type="button"
              >
                Concluir
              </button>
            </div>
          </div>
        ) : (
          <form className="mt-7 space-y-5" noValidate onSubmit={submit}>
            <InputField
              autoComplete="off"
              autoFocus
              disabled={mutation.isPending}
              error={fieldErrors.imageName}
              id="imageName"
              label="Imagem Docker"
              name="imageName"
              placeholder="nginx:1.27"
              type="text"
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <InputField
                defaultValue="1"
                disabled={mutation.isPending}
                error={fieldErrors.cpuCores}
                id="cpuCores"
                inputMode="numeric"
                label="CPUs solicitadas"
                min="1"
                name="cpuCores"
                step="1"
                type="number"
              />
              <InputField
                defaultValue="512"
                disabled={mutation.isPending}
                error={fieldErrors.memoryMb}
                id="memoryMb"
                inputMode="numeric"
                label="RAM solicitada (MB)"
                min="1"
                name="memoryMb"
                step="1"
                type="number"
              />
            </div>

            <InputField
              defaultValue="128"
              disabled={mutation.isPending}
              error={fieldErrors.diskMb}
              id="diskMb"
              inputMode="numeric"
              label="Disco solicitado (MB)"
              min="1"
              name="diskMb"
              step="1"
              type="number"
            />

            <section aria-labelledby="ports-title" className="rounded-2xl border border-slate-200 p-4 sm:p-5">
              <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                <div>
                  <h3 className="font-semibold text-slate-900" id="ports-title">
                    Portas e acesso
                  </h3>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    A porta do host em branco é atribuída pelo Docker. Portas internas não são publicadas.
                  </p>
                </div>
                <button
                  className="w-fit rounded-lg border border-blue-200 px-3 py-2 text-sm font-semibold text-blue-700 transition hover:bg-blue-50"
                  disabled={mutation.isPending || ports.length >= 32}
                  onClick={() => setPorts((current) => [...current, emptyPort()])}
                  type="button"
                >
                  Adicionar porta
                </button>
              </div>

              {ports.length === 0 ? (
                <p className="mt-4 rounded-xl bg-slate-50 px-3 py-2.5 text-sm text-slate-500">
                  Nenhuma porta publicada. O serviço ficará acessível apenas pela rede interna.
                </p>
              ) : (
                <div className="mt-5 space-y-4">
                  {ports.map((port, index) => {
                    const isInternal = port.exposure === "INTERNAL";
                    return (
                      <fieldset className="rounded-xl border border-slate-200 bg-slate-50 p-3" key={index}>
                        <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                          Porta {index + 1}
                        </legend>
                        <div className="grid gap-3 sm:grid-cols-2">
                          <InputField
                            disabled={mutation.isPending}
                            id={`containerPort-${index}`}
                            label="Porta interna"
                            min="1"
                            onChange={(event) => updatePort(index, { containerPort: event.target.value })}
                            type="number"
                            value={port.containerPort}
                          />
                          <InputField
                            disabled={mutation.isPending || isInternal}
                            id={`hostPort-${index}`}
                            label="Porta do host (opcional)"
                            min="1"
                            onChange={(event) => updatePort(index, { hostPort: event.target.value })}
                            placeholder="Automática"
                            type="number"
                            value={port.hostPort}
                          />
                          <div>
                            <label className="mb-2 block text-sm font-medium text-slate-700" htmlFor={`protocol-${index}`}>
                              Protocolo
                            </label>
                            <select
                              className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-950 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                              disabled={mutation.isPending}
                              id={`protocol-${index}`}
                              onChange={(event) => updatePort(index, { protocol: event.target.value as PortProtocol })}
                              value={port.protocol}
                            >
                              <option value="TCP">TCP</option>
                              <option value="UDP">UDP</option>
                            </select>
                          </div>
                          <div>
                            <label className="mb-2 block text-sm font-medium text-slate-700" htmlFor={`exposure-${index}`}>
                              Exposição
                            </label>
                            <select
                              className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-950 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                              disabled={mutation.isPending}
                              id={`exposure-${index}`}
                              onChange={(event) => {
                                const exposure = event.target.value as PortExposure;
                                updatePort(index, {
                                  exposure,
                                  ...(exposure === "HTTP" || exposure === "TCP" ? { protocol: "TCP" } : {}),
                                  ...(exposure === "UDP" ? { protocol: "UDP" } : {}),
                                  ...(exposure === "INTERNAL" ? { hostPort: "", bindAddress: "" } : {}),
                                });
                              }}
                              value={port.exposure}
                            >
                              <option value="INTERNAL">Interna</option>
                              <option value="HTTP">HTTP</option>
                              <option value="TCP">TCP</option>
                              <option value="UDP">UDP</option>
                            </select>
                          </div>
                          <div className="sm:col-span-2">
                            <InputField
                              disabled={mutation.isPending || isInternal}
                              id={`bindAddress-${index}`}
                              label="IP de publicação (opcional)"
                              onChange={(event) => updatePort(index, { bindAddress: event.target.value })}
                              placeholder="0.0.0.0 ou ::"
                              type="text"
                              value={port.bindAddress}
                            />
                          </div>
                        </div>
                        {portErrors[index] ? (
                          <p className="mt-3 text-xs text-rose-600" role="alert">{portErrors[index]}</p>
                        ) : null}
                        <button
                          className="mt-3 text-sm font-semibold text-rose-700 underline underline-offset-4 disabled:opacity-60"
                          disabled={mutation.isPending}
                          onClick={() => setPorts((current) => current.filter((_, currentIndex) => currentIndex !== index))}
                          type="button"
                        >
                          Remover porta
                        </button>
                      </fieldset>
                    );
                  })}
                </div>
              )}
            </section>

            {mutation.isError ? (
              <p
                className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700"
                role="alert"
              >
                {errorMessage}
              </p>
            ) : null}

            <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
              <button
                className="rounded-xl px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-100"
                disabled={mutation.isPending}
                onClick={onClose}
                type="button"
              >
                Cancelar
              </button>
              <button
                className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm shadow-blue-200 transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={mutation.isPending}
                type="submit"
              >
                {mutation.isPending ? "Solicitando..." : "Solicitar container"}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}
