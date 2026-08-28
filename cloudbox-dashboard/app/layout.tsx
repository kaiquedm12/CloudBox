import type { Metadata } from "next";
import { cookies } from "next/headers";
import { Header } from "@/components/header";
import { AUTH_COOKIE_NAME } from "@/lib/auth";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "CloudBox",
    template: "%s | CloudBox",
  },
  description: "Dashboard para gerenciamento da infraestrutura CloudBox.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const isAuthenticated = (await cookies()).has(AUTH_COOKIE_NAME);

  return (
    <html lang="pt-BR">
      <body className="min-h-screen bg-slate-50 text-slate-950 antialiased">
        <Providers realtimeEnabled={isAuthenticated}>
          <div className="min-h-screen">
            <Header isAuthenticated={isAuthenticated} />
            <main className="mx-auto w-full max-w-6xl px-5 py-10 sm:px-8 sm:py-14">
              {children}
            </main>
          </div>
        </Providers>
      </body>
    </html>
  );
}
