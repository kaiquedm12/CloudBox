const DEFAULT_ORCHESTRATOR_URL = "http://localhost:8080";

export const orchestratorUrl =
  process.env.NEXT_PUBLIC_ORCHESTRATOR_URL ?? DEFAULT_ORCHESTRATOR_URL;

type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | Record<string, unknown> | null;
};

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { body, headers, ...requestOptions } = options;
  const isJsonBody =
    body !== null &&
    typeof body === "object" &&
    !(body instanceof Blob) &&
    !(body instanceof FormData) &&
    !(body instanceof URLSearchParams) &&
    !(body instanceof ArrayBuffer);
  const requestHeaders = new Headers(headers);

  if (!requestHeaders.has("Accept")) {
    requestHeaders.set("Accept", "application/json");
  }

  if (isJsonBody && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }

  const response = await fetch(new URL(path, `${orchestratorUrl}/`), {
    ...requestOptions,
    body: isJsonBody ? JSON.stringify(body) : (body as BodyInit | null | undefined),
    headers: requestHeaders,
  });

  if (!response.ok) {
    throw new Error(`Erro ${response.status} ao acessar o orquestrador.`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
