import { NextRequest, NextResponse } from "next/server";

import { isAuthenticated } from "@/lib/auth";
import { listMessages } from "@/lib/queries";

export async function GET(request: NextRequest) {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const params = request.nextUrl.searchParams;
  const beforeId = Number(params.get("beforeId"));
  const limit = Number(params.get("limit"));

  const result = await listMessages({
    topic: optional(params.get("topic")),
    tags: tags(params),
    q: optional(params.get("q")),
    beforeId: Number.isFinite(beforeId) && beforeId > 0 ? beforeId : undefined,
    limit: Number.isFinite(limit) ? limit : undefined
  });

  return NextResponse.json(result);
}

function optional(value: string | null) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function tags(params: URLSearchParams) {
  return params
    .getAll("tag")
    .flatMap((value) => value.split(","))
    .map((value) => value.trim())
    .filter(Boolean);
}
