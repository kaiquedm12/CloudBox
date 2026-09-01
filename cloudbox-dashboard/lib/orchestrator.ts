const DEFAULT_ORCHESTRATOR_URL =
  "http://cloudbox-production-55f7.up.railway.app";

export function orchestratorApiUrl(path: string, search = "") {
  const configuredUrl =
    process.env.ORCHESTRATOR_URL?.trim() ||
    process.env.NEXT_PUBLIC_ORCHESTRATOR_URL?.trim() ||
    DEFAULT_ORCHESTRATOR_URL;
  const baseUrl = configuredUrl.replace(/\/+$/, "");

  return new URL(`${path}${search}`, `${baseUrl}/`);
}
