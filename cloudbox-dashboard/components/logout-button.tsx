"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";

export function LogoutButton() {
  const { t } = useLanguage();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function logout() {
    setIsLoggingOut(true);

    try {
      await apiRequest<{ authenticated: false }>("/api/auth/logout", {
        method: "POST",
        redirectOnUnauthorized: false,
      });
    } finally {
      queryClient.clear();
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <button
      className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-60 sm:px-4"
      disabled={isLoggingOut}
      onClick={() => void logout()}
      type="button"
    >
      {isLoggingOut ? t("signingOut") : t("signOut")}
    </button>
  );
}
