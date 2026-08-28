import type { Metadata } from "next";
import { Header } from "@/components/header";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "CloudBox",
    template: "%s | CloudBox",
  },
  description: "Dashboard para gerenciamento da infraestrutura CloudBox.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body className="min-h-screen bg-slate-50 text-slate-950 antialiased">
        <Providers>
          <div className="min-h-screen">
            <Header />
            <main className="mx-auto w-full max-w-6xl px-5 py-10 sm:px-8 sm:py-14">
              {children}
            </main>
          </div>
        </Providers>
      </body>
    </html>
  );
}
