"use client";

import { LoginForm } from "@/components/login-form";
import { useLanguage } from "@/lib/i18n";

export function LoginPanel({ returnTo }: { returnTo: string }) {
  const { t } = useLanguage();

  return (
    <div className="relative">
      <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
        {t("secureAccess")}
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950">Login</h1>
      <p className="mt-4 leading-7 text-slate-600">{t("loginDescription")}</p>
      <LoginForm returnTo={returnTo} />
    </div>
  );
}
