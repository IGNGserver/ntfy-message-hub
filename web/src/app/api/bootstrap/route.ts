import { NextResponse } from "next/server";

import { isAuthenticated } from "@/lib/auth";
import { listTags, listTopics } from "@/lib/queries";

export async function GET() {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const [topics, tags] = await Promise.all([listTopics(), listTags()]);

  return NextResponse.json({ topics, tags });
}
