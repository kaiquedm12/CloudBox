"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { LanguageToggle } from "@/components/language-toggle";
import { LogoutButton } from "@/components/logout-button";
import { ThemeToggle } from "@/components/theme-toggle";
import { MenuIcon, XIcon } from "@/components/ui-icons";
import { useLanguage } from "@/lib/i18n";

type NavigationItem = {
  active: boolean;
  href: string;
  label: string;
};

function NavigationLinks({
  items,
  mobile = false,
}: {
  items: NavigationItem[];
  mobile?: boolean;
}) {
  return (
    <ul className={mobile ? "space-y-1" : "flex items-center gap-1"}>
      {items.map((item) => (
        <li key={item.href}>
          <Link
            aria-current={item.active ? "page" : undefined}
            className={`block rounded-xl px-4 py-2.5 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
              item.active
                ? "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300"
                : "text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
            } ${mobile ? "w-full" : ""}`}
            href={item.href}
          >
            {item.label}
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function Header({ isAuthenticated }: { isAuthenticated: boolean }) {
  const { t } = useLanguage();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const navigation = [
    {
      label: t("overview"),
      href: "/",
      active: pathname === "/" || pathname.startsWith("/nodes/"),
    },
    {
      label: t("containers"),
      href: "/containers",
      active: pathname.startsWith("/containers"),
    },
  ];

  useEffect(() => setMenuOpen(false), [pathname]);

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/85 shadow-[0_1px_0_rgb(15_23_42/0.02)] backdrop-blur-xl dark:border-slate-800/80 dark:bg-slate-950/80">
      <div className="mx-auto flex min-h-16 w-full max-w-7xl items-center justify-between gap-3 px-4 py-2.5 sm:px-8 lg:px-10">
        <Link
          aria-label={t("homeLabel")}
          className="flex items-center gap-3 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600"
          href="/"
        >
          <span className="relative grid size-9 place-items-center overflow-hidden rounded-xl bg-gradient-to-br from-blue-500 to-blue-700 text-xs font-bold tracking-tight text-white shadow-lg shadow-blue-500/20">
            <span className="absolute -right-2 -top-2 size-5 rounded-full bg-cyan-300/50 blur-sm" />
            <span className="relative">CB</span>
          </span>
          <span>
            <span className="block text-base font-bold leading-5 tracking-tight text-slate-950 dark:text-white">CloudBox</span>
            <span className="hidden text-[9px] font-semibold uppercase tracking-[0.18em] text-slate-400 sm:block">Control center</span>
          </span>
        </Link>

        <div className="hidden items-center gap-2 md:flex">
          {isAuthenticated ? (
            <nav aria-label={t("mainNavigation")}><NavigationLinks items={navigation} /></nav>
          ) : (
            <Link className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800" href="/login">{t("signIn")}</Link>
          )}
          <div className="ml-1 h-6 w-px bg-slate-200 dark:bg-slate-800" />
          <LanguageToggle />
          <ThemeToggle />
          {isAuthenticated ? <LogoutButton /> : null}
        </div>

        <div className="flex items-center gap-2 md:hidden">
          <LanguageToggle />
          <ThemeToggle />
          {isAuthenticated ? (
            <button
              aria-expanded={menuOpen}
              aria-label={menuOpen ? t("closeMenu") : t("openMenu")}
              className="grid size-10 place-items-center rounded-xl border border-slate-200 bg-white text-slate-600 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300"
              onClick={() => setMenuOpen((open) => !open)}
              type="button"
            >
              {menuOpen ? <XIcon className="size-5" /> : <MenuIcon className="size-5" />}
            </button>
          ) : (
            <Link className="rounded-xl px-3 py-2 text-sm font-semibold text-blue-600" href="/login">{t("signIn")}</Link>
          )}
        </div>
      </div>

      {isAuthenticated && menuOpen ? (
        <div className="border-t border-slate-200 bg-white px-4 py-3 shadow-lg dark:border-slate-800 dark:bg-slate-950 md:hidden">
          <nav aria-label={t("mainNavigation")}><NavigationLinks items={navigation} mobile /></nav>
          <div className="mt-2 border-t border-slate-100 pt-2 dark:border-slate-800">
            <LogoutButton fullWidth />
          </div>
        </div>
      ) : null}
    </header>
  );
}
