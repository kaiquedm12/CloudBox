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
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var t=localStorage.getItem('cloudbox-theme');var d=t==='dark'||(!t&&matchMedia('(prefers-color-scheme: dark)').matches);document.documentElement.classList.toggle('dark',d);document.documentElement.style.colorScheme=d?'dark':'light'}catch(e){}})()`,
          }}
        />
      </head>
      <body className="min-h-screen bg-slate-50 text-slate-950 antialiased dark:bg-slate-950 dark:text-slate-100">
        <Providers realtimeEnabled={isAuthenticated}>
          <div className="min-h-screen">
            <Header isAuthenticated={isAuthenticated} />
            <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-8 sm:py-12 lg:px-10">
              {children}
            </main>
          </div>
        </Providers>
      </body>
    </html>
  );
}
