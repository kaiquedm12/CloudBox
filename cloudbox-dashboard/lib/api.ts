type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | Record<string, unknown> | null;
  redirectOnUnauthorized?: boolean;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function responseErrorMessage(response: Response) {
  const fallback = `Erro ${response.status} ao acessar o orquestrador.`;

  try {
    const payload: unknown = await response.json();
    if (typeof payload === "object" && payload !== null) {
      const error = (payload as { error?: unknown }).error;
      if (typeof error === "string" && error.trim()) {
        return error;
      }
    }
  } catch {
    // Respostas sem JSON usam a mensagem padrão.
  }

  return fallback;
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  if (!path.startsWith("/api/")) {
    throw new Error("O client HTTP aceita apenas rotas internas em /api/.");
  }

  const {
    body,
    headers,
    redirectOnUnauthorized = true,
    ...requestOptions
  } = options;
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

  const response = await fetch(path, {
    ...requestOptions,
    body: isJsonBody ? JSON.stringify(body) : (body as BodyInit | null | undefined),
    credentials: requestOptions.credentials ?? "same-origin",
    headers: requestHeaders,
  });

  if (!response.ok) {
    const message = await responseErrorMessage(response);

    if (
      response.status === 401 &&
      redirectOnUnauthorized &&
      typeof window !== "undefined"
    ) {
      const currentPath = `${window.location.pathname}${window.location.search}`;
      const loginUrl = new URL("/login", window.location.origin);

      if (currentPath !== "/login") {
        loginUrl.searchParams.set("next", currentPath);
      }

      window.location.assign(`${loginUrl.pathname}${loginUrl.search}`);
    }

    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
