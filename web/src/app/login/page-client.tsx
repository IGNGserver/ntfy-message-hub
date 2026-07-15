"use client";

import { FormEvent, useState, useTransition } from "react";
import { useRouter } from "next/navigation";

export default function LoginForm() {
  const router = useRouter();
  const [accessKey, setAccessKey] = useState("");
  const [error, setError] = useState("");
  const [isPending, startTransition] = useTransition();

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");

    const response = await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ accessKey })
    });

    if (!response.ok) {
      setError("密钥不正确");
      return;
    }

    startTransition(() => {
      router.replace("/");
      router.refresh();
    });
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <p className="eyebrow">Private archive</p>
        <h1>访问验证</h1>
        <span>输入访问密钥后才能查看 ntfy 消息记录和调用数据接口。</span>
        <form onSubmit={submit}>
          <label>
            <span>密钥</span>
            <input
              autoFocus
              value={accessKey}
              onChange={(event) => setAccessKey(event.target.value)}
              placeholder="请输入访问密钥"
              type="password"
            />
          </label>
          {error ? <strong className="login-error">{error}</strong> : null}
          <button disabled={isPending || !accessKey} type="submit">
            进入站点
          </button>
        </form>
      </section>
    </main>
  );
}
