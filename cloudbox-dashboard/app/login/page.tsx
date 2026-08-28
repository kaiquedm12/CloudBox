import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Login",
};

export default function LoginPage() {
  return (
    <section className="mx-auto max-w-xl rounded-3xl border border-slate-200 bg-white p-8 shadow-sm sm:p-10">
      <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
        Acesso seguro
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950">
        Login
      </h1>
      <p className="mt-4 leading-7 text-slate-600">
        O fluxo de autenticação será implementado aqui. Por enquanto, a rota valida o
        uso do layout compartilhado sem realizar chamadas externas.
      </p>
    </section>
  );
}
