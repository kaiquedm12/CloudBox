import Link from "next/link";

const foundations = [
  {
    title: "Orquestrador",
    description: "Client HTTP preparado para a integração com o serviço central.",
    tag: "Configurado",
  },
  {
    title: "Containers",
    description: "Área reservada para acompanhar e administrar seus workloads.",
    tag: "Próxima etapa",
  },
  {
    title: "Acesso",
    description: "Fluxo de autenticação pronto para evoluir na rota de login.",
    tag: "Próxima etapa",
  },
];

export default function Home() {
  return (
    <div className="space-y-10">
      <section className="relative overflow-hidden rounded-3xl border border-slate-200 bg-white px-6 py-10 shadow-[0_24px_60px_-32px_rgba(15,23,42,0.28)] sm:px-10 sm:py-14">
        <div
          aria-hidden="true"
          className="absolute -right-20 -top-24 size-72 rounded-full bg-blue-100/70 blur-3xl"
        />
        <div className="relative max-w-3xl">
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-700">
            <span className="size-2 rounded-full bg-emerald-500" />
            Estrutura base pronta
          </div>
          <p className="mb-3 text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
            Painel de controle
          </p>
          <h1 className="text-4xl font-semibold tracking-tight text-slate-950 sm:text-5xl">
            Visão geral
          </h1>
          <p className="mt-5 max-w-2xl text-base leading-7 text-slate-600 sm:text-lg">
            O ponto de partida para visualizar ambientes, gerenciar containers e
            acompanhar a operação da CloudBox em um só lugar.
          </p>
          <Link
            href="/containers"
            className="mt-8 inline-flex items-center rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Ir para containers
            <span aria-hidden="true" className="ml-2">
              →
            </span>
          </Link>
        </div>
      </section>

      <section aria-labelledby="foundation-title">
        <div className="mb-5 flex items-end justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-blue-600">Fundação do produto</p>
            <h2
              id="foundation-title"
              className="mt-1 text-2xl font-semibold tracking-tight text-slate-950"
            >
              Pronto para as próximas telas
            </h2>
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          {foundations.map((item, index) => (
            <article
              key={item.title}
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
            >
              <div className="mb-6 flex items-center justify-between">
                <span className="grid size-10 place-items-center rounded-xl bg-slate-950 text-sm font-semibold text-white">
                  {String(index + 1).padStart(2, "0")}
                </span>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
                  {item.tag}
                </span>
              </div>
              <h3 className="text-lg font-semibold text-slate-950">{item.title}</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                {item.description}
              </p>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
