import type { Metadata } from "next";
import { LoginForm } from "@/components/login-form";

export const metadata: Metadata = {
  title: "Login",
};

function safeReturnPath(value: string | string[] | undefined) {
  const candidate = Array.isArray(value) ? value[0] : value;

  if (!candidate) {
    return "/";
  }

  const isDashboardPath =
    candidate === "/" ||
    candidate === "/containers" ||
    candidate.startsWith("/containers?") ||
    candidate.startsWith("/containers/") ||
    candidate.startsWith("/nodes/");

  return isDashboardPath ? candidate : "/";
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const params = await searchParams;

  return (
    <section className="relative mx-auto max-w-md overflow-hidden rounded-[2rem] border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/50 sm:p-10 dark:shadow-black/20">
      <div aria-hidden="true" className="absolute -right-16 -top-20 size-48 rounded-full bg-blue-100 blur-3xl dark:bg-blue-900/20" />
      <div className="relative">
      <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
        Acesso seguro
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950">
        Login
      </h1>
      <p className="mt-4 leading-7 text-slate-600">
        Entre com suas credenciais para acessar o painel de gerenciamento da
        CloudBox.
      </p>
      <LoginForm returnTo={safeReturnPath(params.next)} />
      </div>
    </section>
  );
}
