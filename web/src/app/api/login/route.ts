import { NextRequest, NextResponse } from "next/server";

import { expectedSession, isValidAccessKey, SESSION_COOKIE } from "@/lib/auth";

export async function POST(request: NextRequest) {
  const body = (await request.json().catch(() => null)) as { accessKey?: unknown } | null;
  const accessKey = typeof body?.accessKey === "string" ? body.accessKey : "";

  if (!isValidAccessKey(accessKey)) {
    return NextResponse.json({ ok: false, message: "密钥不正确" }, { status: 401 });
  }

  const response = NextResponse.json({ ok: true });
  response.cookies.set({
    name: SESSION_COOKIE,
    value: expectedSession(),
    httpOnly: true,
    sameSite: "lax",
    // Secure by default; set SESSION_COOKIE_SECURE=false only for deliberate plain-HTTP LAN deployments.
    secure: (process.env.SESSION_COOKIE_SECURE ?? "true") !== "false",
    path: "/",
    maxAge: 60 * 60 * 24 * 30
  });

  return response;
}
