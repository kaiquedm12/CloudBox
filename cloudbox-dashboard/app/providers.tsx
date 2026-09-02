"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { ClusterStatusProvider } from "@/hooks/use-cluster-status";
import { LanguageProvider } from "@/lib/i18n";

export function Providers({
  children,
  realtimeEnabled,
}: Readonly<{ children: React.ReactNode; realtimeEnabled: boolean }>) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: 1,
            staleTime: 30_000,
          },
        },
      }),
  );

  return (
    <LanguageProvider>
      <QueryClientProvider client={queryClient}>
        {realtimeEnabled ? (
          <ClusterStatusProvider>{children}</ClusterStatusProvider>
        ) : (
          children
        )}
      </QueryClientProvider>
    </LanguageProvider>
  );
}
