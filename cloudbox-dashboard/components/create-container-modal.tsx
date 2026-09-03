"use client";

import { useEffect, useId, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { useCreateContainer } from "@/hooks/use-containers";
import { ApiError } from "@/lib/api";
import { createContainerSchema } from "@/lib/container-schema";
import { useLanguage, type MessageKey } from "@/lib/i18n";
import type { ClusterNode } from "@/types/cluster-node";
import type { CloudContainer, CreateContainerRequest } from "@/types/container";

type FieldErrors = Partial<Record<keyof CreateContainerRequest, string>>;

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
  const { language, t } = useLanguage();
  const titleId = useId();
  const dialogRef = useRef<HTMLElement>(null);
  const mutation = useCreateContainer();
  const isPendingRef = useRef(mutation.isPending);
  const onCloseRef = useRef(onClose);
  isPendingRef.current = mutation.isPending;
  onCloseRef.current = onClose;
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [createdContainer, setCreatedContainer] = useState<CloudContainer | null>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !isPendingRef.current) {
        onCloseRef.current();
      }

      if (event.key !== "Tab") return;
      const focusable = Array.from(
        dialogRef.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      );
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, []);

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
    });

    if (!parsed.success) {
      const errors: FieldErrors = {};
      const translatedErrors: Record<keyof CreateContainerRequest, MessageKey> = {
        imageName: "invalidImage",
        cpuCores: "invalidCpu",
        memoryMb: "invalidRam",
        diskMb: "invalidDisk",
      };
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as keyof CreateContainerRequest;
        errors[field] ??= language === "en" ? t(translatedErrors[field]) : issue.message;
      }
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
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

  const chosenNode = createdContainer?.nodeId
    ? nodes.find((node) => node.id === createdContainer.nodeId)
    : undefined;
  const requestError = mutation.error;
  const errorMessage =
    requestError instanceof ApiError && requestError.status === 409
      ? t("capacityError")
      : requestError instanceof Error
        ? requestError.message
        : t("requestFailed");

  return (
    <div
      className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-sm"
      onMouseDown={closeFromBackdrop}
    >
      <section
        aria-labelledby={titleId}
        aria-modal="true"
        className="my-6 w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-slate-900 sm:p-8"
        ref={dialogRef}
        role="dialog"
      >
        <div className="flex items-start justify-between gap-6">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-600">
              {t("scheduler")}
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950" id={titleId}>
              {t("newContainer")}
            </h2>
          </div>
          <button
            aria-label={t("close")}
            className="grid size-9 shrink-0 place-items-center rounded-lg text-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-white"
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
              <p className="font-semibold text-emerald-900">{t("createSuccess")}</p>
              <p className="mt-2 text-sm leading-6 text-emerald-700">
                {t("imageAllocated")} <strong>{createdContainer.imageName}</strong> {t("allocatedOnNode")}{" "}
                <strong>{chosenNode?.name ?? createdContainer.nodeId ?? t("selected")}</strong>.
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
                {t("finish")}
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
              label={t("dockerImage")}
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
                label={t("requestedCpus")}
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
                label={t("requestedRam")}
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
              label={t("requestedDisk")}
              min="1"
              name="diskMb"
              step="1"
              type="number"
            />

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
                {t("cancel")}
              </button>
              <button
                className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm shadow-blue-200 transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={mutation.isPending}
                type="submit"
              >
                {mutation.isPending ? t("requesting") : t("requestContainer")}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}
