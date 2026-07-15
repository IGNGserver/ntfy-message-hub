import { redirect } from "next/navigation";

import { isAuthenticated } from "@/lib/auth";

import HomeClient from "./page-client";

export default async function HomePage() {
  if (!(await isAuthenticated())) {
    redirect("/login");
  }

  return <HomeClient />;
}
