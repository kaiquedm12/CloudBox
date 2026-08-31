import Link from "next/link";
import { LogoutButton } from "@/components/logout-button";
import { ThemeToggle } from "@/components/theme-toggle";

const authenticatedNavigation = [
  { label: "Visão geral", href: "/" },
  { label: "Containers", href: "/containers" },
];

export function Header({ isAuthenticated }: { isAuthenticated: boolean }) {
  return (
    <header className="sticky top-0 z-50 border-b border-slate-200/70 bg-white/80 shadow-[0_1px_0_rgb(15_23_42/0.02)] backdrop-blur-xl dark:border-slate-800/80 dark:bg-slate-950/75">
      <div className="mx-auto flex min-h-18 w-full max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-8 lg:px-10">
        <Link
          href="/"
          className="flex items-center gap-3 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600"
          aria-label="CloudBox — página inicial"
        >
          <span className="relative grid size-10 place-items-center overflow-hidden rounded-xl bg-gradient-to-br from-blue-500 to-blue-700 text-sm font-bold tracking-tight text-white shadow-lg shadow-blue-500/20">
            <span className="absolute -right-2 -top-2 size-5 rounded-full bg-cyan-300/50 blur-sm" />
            <span className="relative">CB</span>
          </span>
          <span>
            <span className="block text-lg font-semibold leading-5 tracking-tight text-slate-950 dark:text-white">CloudBox</span>
            <span className="hidden text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400 sm:block">Control center</span>
          </span>
        </Link>

        <div className="flex items-center gap-2">
        <nav aria-label="Navegação principal">
          <ul className="flex items-center gap-0.5 text-sm font-medium text-slate-600 dark:text-slate-300 sm:gap-1">
            {(isAuthenticated ? authenticatedNavigation : []).map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="block rounded-xl px-3 py-2 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 dark:hover:bg-slate-800 dark:hover:text-white sm:px-4"
                >
                  {item.label}
                </Link>
              </li>
            ))}
            <li>
              {isAuthenticated ? (
                <LogoutButton />
              ) : (
                <Link
                  href="/login"
                  className="block rounded-xl px-3 py-2 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 dark:hover:bg-slate-800 dark:hover:text-white sm:px-4"
                >
                  Entrar
                </Link>
              )}
            </li>
          </ul>
        </nav>
        <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
