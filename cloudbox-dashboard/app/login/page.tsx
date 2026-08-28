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
    <section className="mx-auto max-w-md rounded-3xl border border-slate-200 bg-white p-8 shadow-sm sm:p-10">
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
    </section>
  );
}
