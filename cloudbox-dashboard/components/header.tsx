import Link from "next/link";
import { LogoutButton } from "@/components/logout-button";

const authenticatedNavigation = [
  { label: "Visão geral", href: "/" },
  { label: "Containers", href: "/containers" },
];

export function Header({ isAuthenticated }: { isAuthenticated: boolean }) {
  return (
    <header className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/90 backdrop-blur">
      <div className="mx-auto flex min-h-16 w-full max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-3 sm:px-8">
        <Link
          href="/"
          className="flex items-center gap-3 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600"
          aria-label="CloudBox — página inicial"
        >
          <span className="grid size-9 place-items-center rounded-xl bg-blue-600 text-sm font-bold tracking-tight text-white shadow-sm shadow-blue-200">
            CB
          </span>
          <span className="text-lg font-semibold tracking-tight text-slate-950">
            CloudBox
          </span>
        </Link>

        <nav aria-label="Navegação principal">
          <ul className="flex items-center gap-1 text-sm font-medium text-slate-600 sm:gap-2">
            {(isAuthenticated ? authenticatedNavigation : []).map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="block rounded-lg px-3 py-2 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 sm:px-4"
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
                  className="block rounded-lg px-3 py-2 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 sm:px-4"
                >
                  Entrar
                </Link>
              )}
            </li>
          </ul>
        </nav>
      </div>
    </header>
  );
}
