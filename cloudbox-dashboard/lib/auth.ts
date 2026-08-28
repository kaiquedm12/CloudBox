export const AUTH_COOKIE_NAME = "cloudbox_access_token";

export function authCookieOptions(maxAge?: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    priority: "high" as const,
    ...(maxAge === undefined ? {} : { maxAge }),
  };
}
