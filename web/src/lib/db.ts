import mysql from "mysql2/promise";

declare global {
  var ntfyMessagePool: mysql.Pool | undefined;
}

function numberEnv(name: string, fallback: number) {
  const value = process.env[name];
  if (!value) return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`${name} must be a number`);
  }
  return parsed;
}

export function getPool() {
  if (!global.ntfyMessagePool) {
    global.ntfyMessagePool = mysql.createPool({
      host: process.env.MYSQL_HOST ?? "127.0.0.1",
      port: numberEnv("MYSQL_PORT", 3306),
      database: process.env.MYSQL_DATABASE ?? "ntfy_message_store",
      user: process.env.MYSQL_USER ?? "ntfy_store",
      password: process.env.MYSQL_PASSWORD,
      waitForConnections: true,
      connectionLimit: 10,
      charset: "utf8mb4"
    });
  }

  return global.ntfyMessagePool;
}

export type MessageRow = {
  id: string;
  ntfy_message_id: string;
  event_type: string;
  message_text: string | null;
  title: string | null;
  priority: number | null;
  tags: unknown | null;
  click_url: string | null;
  actions: unknown | null;
  ntfy_time: number | null;
  received_at: Date;
  raw_json: unknown;
  topic: string;
  recorder_user: string;
  source_user: string | null;
};
