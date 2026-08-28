import type { NextConfig } from "next";

const DEFAULT_ORCHESTRATOR_URL = "http://localhost:8080";
const orchestratorUrl = (
  process.env.ORCHESTRATOR_URL?.trim() || DEFAULT_ORCHESTRATOR_URL
).replace(/\/+$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/ws/:path*",
        destination: `${orchestratorUrl}/ws/:path*`,
      },
    ];
  },
};

export default nextConfig;
