"use client";

import { useState, type FormEvent } from "react";
import { ApiError, apiRequest } from "@/lib/api";

export function LoginForm({ returnTo }: { returnTo: string }) {
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    const formData = new FormData(event.currentTarget);

    try {
      await apiRequest<{ authenticated: true }>("/api/auth/login", {
        method: "POST",
        body: {
          email: String(formData.get("email") ?? "").trim(),
          password: String(formData.get("password") ?? ""),
        },
        redirectOnUnauthorized: false,
      });
      window.location.assign(returnTo);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "Não foi possível realizar o login.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
      <div>
        <label
          className="mb-2 block text-sm font-medium text-slate-700"
          htmlFor="email"
        >
          E-mail
        </label>
        <input
          autoComplete="email"
          autoFocus
          className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
          disabled={isSubmitting}
          id="email"
          name="email"
          placeholder="voce@empresa.com"
          required
          type="email"
        />
      </div>

      <div>
        <label
          className="mb-2 block text-sm font-medium text-slate-700"
          htmlFor="password"
        >
          Senha
        </label>
        <input
          autoComplete="current-password"
          className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
          disabled={isSubmitting}
          id="password"
          minLength={1}
          name="password"
          placeholder="Digite sua senha"
          required
          type="password"
        />
      </div>

      {error ? (
        <p
          aria-live="polite"
          className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
          role="alert"
        >
          {error}
        </p>
      ) : null}

      <button
        className="flex w-full items-center justify-center rounded-xl bg-blue-600 px-4 py-3 font-semibold text-white shadow-sm shadow-blue-200 transition hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-60"
        disabled={isSubmitting}
        type="submit"
      >
        {isSubmitting ? "Entrando..." : "Entrar"}
      </button>
    </form>
  );
}
