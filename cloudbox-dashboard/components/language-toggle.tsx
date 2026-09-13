"use client";

import { useLanguage } from "@/lib/i18n";

export function LanguageToggle() {
  const { language, setLanguage, t } = useLanguage();
  const nextLanguage = language === "pt-BR" ? "en" : "pt-BR";
  const label = nextLanguage === "en" ? t("switchToEnglish") : t("switchToPortuguese");

  return (
    <button
      aria-label={label}
      className="h-10 rounded-xl border border-slate-200 bg-white/80 px-3 text-xs font-bold text-slate-600 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-300 hover:text-blue-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 dark:border-slate-700 dark:bg-slate-900/80 dark:text-slate-300 dark:hover:border-blue-700 dark:hover:text-blue-400"
      onClick={() => setLanguage(nextLanguage)}
      title={label}
      type="button"
    >
      {nextLanguage === "en" ? "EN" : "PT"}
    </button>
  );
}
