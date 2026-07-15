import { createHash, timingSafeEqual } from "crypto";
import { cookies } from "next/headers";

export const SESSION_COOKIE = "ntfy_message_hub_session";

export function expectedSession() {
  return createHash("sha256").update(accessKey()).digest("hex");
}

export function isValidAccessKey(value: string) {
  return safeEqual(value, accessKey());
}

export async function isAuthenticated() {
  const cookieStore = await cookies();
  const session = cookieStore.get(SESSION_COOKIE)?.value;
  return Boolean(session && safeEqual(session, expectedSession()));
}

function accessKey() {
  const value = process.env.ACCESS_KEY?.trim();
  if (!value) {
    throw new Error("ACCESS_KEY is required");
  }
  return value;
}

function safeEqual(a: string, b: string) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}
