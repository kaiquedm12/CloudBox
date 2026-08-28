import type { NextConfig } from "next";

const DEFAULT_ORCHESTRATOR_URL = "http://localhost:8080";
const orchestratorUrl = (
  process.env.NEXT_PUBLIC_ORCHESTRATOR_URL?.trim() || DEFAULT_ORCHESTRATOR_URL
).replace(/\/+$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${orchestratorUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
