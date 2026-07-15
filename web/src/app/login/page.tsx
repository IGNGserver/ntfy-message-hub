import { redirect } from "next/navigation";

import { isAuthenticated } from "@/lib/auth";

import LoginForm from "./page-client";

export default async function LoginPage() {
  if (await isAuthenticated()) {
    redirect("/");
  }

  return <LoginForm />;
}
