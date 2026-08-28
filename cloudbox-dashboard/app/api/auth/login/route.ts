import { NextResponse } from "next/server";
import { AUTH_COOKIE_NAME, authCookieOptions } from "@/lib/auth";
import { orchestratorApiUrl } from "@/lib/orchestrator";

type LoginResponse = {
  token: string;
  tokenType: string;
  expiresIn: number;
};

function isLoginResponse(value: unknown): value is LoginResponse {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const response = value as Partial<LoginResponse>;
  return (
    typeof response.token === "string" &&
    response.token.length > 0 &&
    response.tokenType === "Bearer" &&
    typeof response.expiresIn === "number" &&
    Number.isFinite(response.expiresIn) &&
    response.expiresIn > 0
  );
}

function errorMessage(value: unknown, fallback: string) {
  if (typeof value !== "object" || value === null) {
    return fallback;
  }

  const error = (value as { error?: unknown }).error;
  return typeof error === "string" && error.trim() ? error : fallback;
}

export async function POST(request: Request) {
  let credentials: unknown;

  try {
    credentials = await request.json();
  } catch {
    return NextResponse.json(
      { error: "Informe um e-mail e uma senha válidos." },
      { status: 400 },
    );
  }

  if (
    typeof credentials !== "object" ||
    credentials === null ||
    typeof (credentials as { email?: unknown }).email !== "string" ||
    typeof (credentials as { password?: unknown }).password !== "string"
  ) {
    return NextResponse.json(
      { error: "Informe um e-mail e uma senha válidos." },
      { status: 400 },
    );
  }

  try {
    const loginRequest = credentials as { email: string; password: string };
    const upstreamResponse = await fetch(
      orchestratorApiUrl("/api/auth/login"),
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: loginRequest.email,
          password: loginRequest.password,
        }),
        cache: "no-store",
      },
    );
    const payload: unknown = await upstreamResponse.json().catch(() => null);

    if (!upstreamResponse.ok) {
      const fallback =
        upstreamResponse.status === 401
          ? "E-mail ou senha inválidos."
          : "Não foi possível realizar o login.";

      return NextResponse.json(
        { error: errorMessage(payload, fallback) },
        { status: upstreamResponse.status },
      );
    }

    if (!isLoginResponse(payload)) {
      return NextResponse.json(
        { error: "O orquestrador retornou uma resposta de login inválida." },
        { status: 502 },
      );
    }

    const response = NextResponse.json({ authenticated: true });
    response.cookies.set(
      AUTH_COOKIE_NAME,
      payload.token,
      authCookieOptions(Math.floor(payload.expiresIn)),
    );

    return response;
  } catch {
    return NextResponse.json(
      { error: "Não foi possível conectar ao orquestrador." },
      { status: 502 },
    );
  }
}
