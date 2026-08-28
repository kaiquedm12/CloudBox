import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Containers",
};

export default function ContainersPage() {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-8 shadow-sm sm:p-10">
      <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
        CloudBox
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950">
        Containers
      </h1>
      <p className="mt-4 max-w-2xl leading-7 text-slate-600">
        Esta área está preparada para receber a listagem e as ações de gerenciamento
        de containers nas próximas etapas.
      </p>
    </section>
  );
}
