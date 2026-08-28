import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { AUTH_COOKIE_NAME, authCookieOptions } from "@/lib/auth";
import { orchestratorApiUrl } from "@/lib/orchestrator";

type ApiRouteContext = {
  params: Promise<{ path: string[] }>;
};

const requestHeadersToForward = [
  "accept",
  "content-type",
  "if-match",
  "if-none-match",
];

const responseHeadersToForward = [
  "cache-control",
  "content-disposition",
  "content-type",
  "etag",
  "last-modified",
];

async function forwardRequest(request: NextRequest, context: ApiRouteContext) {
  const token = request.cookies.get(AUTH_COOKIE_NAME)?.value;

  if (!token) {
    return NextResponse.json(
      { error: "Autenticação necessária." },
      { status: 401 },
    );
  }

  const { path } = await context.params;
  const encodedPath = path.map(encodeURIComponent).join("/");
  const upstreamHeaders = new Headers();

  for (const name of requestHeadersToForward) {
    const value = request.headers.get(name);
    if (value) {
      upstreamHeaders.set(name, value);
    }
  }

  upstreamHeaders.set("Authorization", `Bearer ${token}`);

  const hasBody = request.method !== "GET" && request.method !== "HEAD";

  try {
    const upstreamResponse = await fetch(
      orchestratorApiUrl(`/api/${encodedPath}`, request.nextUrl.search),
      {
        method: request.method,
        headers: upstreamHeaders,
        body: hasBody ? await request.arrayBuffer() : undefined,
        cache: "no-store",
        redirect: "manual",
      },
    );
    const responseHeaders = new Headers();

    for (const name of responseHeadersToForward) {
      const value = upstreamResponse.headers.get(name);
      if (value) {
        responseHeaders.set(name, value);
      }
    }

    const response = new NextResponse(upstreamResponse.body, {
      status: upstreamResponse.status,
      headers: responseHeaders,
    });

    if (upstreamResponse.status === 401) {
      response.cookies.set(AUTH_COOKIE_NAME, "", authCookieOptions(0));
    }

    return response;
  } catch {
    return NextResponse.json(
      { error: "Não foi possível conectar ao orquestrador." },
      { status: 502 },
    );
  }
}

export const GET = forwardRequest;
export const POST = forwardRequest;
export const PUT = forwardRequest;
export const PATCH = forwardRequest;
export const DELETE = forwardRequest;
