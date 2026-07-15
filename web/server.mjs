import http from "node:http";
import next from "next";
import mysql from "mysql2/promise";

const dev = process.env.NODE_ENV !== "production";
const hostname = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 47183);
const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

const logLevel = (process.env.LOG_LEVEL || "info").toLowerCase();
const shouldDebug = logLevel === "debug";

const pool = mysql.createPool({
  host: process.env.MYSQL_HOST || "127.0.0.1",
  port: Number(process.env.MYSQL_PORT || 3306),
  database: process.env.MYSQL_DATABASE || "ntfy_message_store",
  user: process.env.MYSQL_USER || "ntfy_store",
  password: process.env.MYSQL_PASSWORD,
  waitForConnections: true,
  connectionLimit: 10,
  charset: "utf8mb4"
});

const topicIds = new Map();
let recorderUserId = null;
let shuttingDown = false;
const controllers = new Set();

function info(...args) {
  console.log(new Date().toISOString(), "INFO", ...args);
}

function warn(...args) {
  console.warn(new Date().toISOString(), "WARN", ...args);
}

function debug(...args) {
  if (shouldDebug) console.log(new Date().toISOString(), "DEBUG", ...args);
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function getConfig() {
  const topics = (process.env.NTFY_TOPICS || "reports,messages")
    .split(",")
    .map((topic) => topic.trim())
    .filter(Boolean);

  if (!topics.length) throw new Error("NTFY_TOPICS must contain at least one topic");

  return {
    ntfyBaseUrl: (process.env.NTFY_BASE_URL || "https://example.com").replace(/\/+$/, ""),
    ntfyTopics: topics,
    ntfyToken: requiredEnv("NTFY_TOKEN"),
    recorderUser: process.env.RECORDER_USER || "lvziw",
    reconnectSeconds: Number(process.env.RECONNECT_SECONDS || 5),
    authRetrySeconds: Number(process.env.AUTH_RETRY_SECONDS || 300)
  };
}

async function getOrCreateId(table, column, value) {
  await pool.execute(`INSERT IGNORE INTO ${table} (${column}) VALUES (?)`, [value]);
  const [rows] = await pool.execute(`SELECT id FROM ${table} WHERE ${column} = ?`, [value]);
  if (!rows.length) throw new Error(`Failed to create or load ${table}.${column}=${value}`);
  return Number(rows[0].id);
}

async function getTopicId(topic) {
  if (!topicIds.has(topic)) {
    topicIds.set(topic, await getOrCreateId("topics", "name", topic));
  }
  return topicIds.get(topic);
}

async function getRecorderUserId(config) {
  if (recorderUserId === null) {
    recorderUserId = await getOrCreateId("users", "username", config.recorderUser);
  }
  return recorderUserId;
}

function stripAttachment(payload) {
  const sanitized = { ...payload };
  delete sanitized.attachment;
  return sanitized;
}

function jsonOrNull(value) {
  return value === undefined || value === null ? null : JSON.stringify(value);
}

async function saveMessage(topic, payload, config) {
  if (payload.event !== "message") return false;
  if (!payload.id) {
    warn("Skipping message without ntfy id", payload);
    return false;
  }

  const sanitized = stripAttachment(payload);
  const topicId = await getTopicId(topic);
  const userId = await getRecorderUserId(config);
  const [result] = await pool.execute(
    `
    INSERT IGNORE INTO messages (
      ntfy_message_id,
      topic_id,
      recorder_user_id,
      event_type,
      message_text,
      title,
      priority,
      tags,
      click_url,
      actions,
      ntfy_time,
      raw_json
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `,
    [
      payload.id,
      topicId,
      userId,
      sanitized.event || "message",
      sanitized.message ?? null,
      sanitized.title ?? null,
      sanitized.priority ?? null,
      jsonOrNull(sanitized.tags),
      sanitized.click ?? null,
      jsonOrNull(sanitized.actions),
      sanitized.time ?? null,
      JSON.stringify(sanitized)
    ]
  );

  return result.affectedRows === 1;
}

async function subscribeTopic(topic, config) {
  const url = `${config.ntfyBaseUrl}/${encodeURIComponent(topic)}/json?since=all`;
  const decoder = new TextDecoder();
  let buffer = "";

  while (!shuttingDown) {
    let retrySeconds = config.reconnectSeconds;
    const controller = new AbortController();
    controllers.add(controller);

    try {
      info(`Subscribing to topic ${topic}`);
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${config.ntfyToken}`,
          "User-Agent": "ntfy-message-hub/1.0"
        },
        signal: controller.signal
      });

      if (!response.ok) {
        warn(`ntfy HTTP error topic=${topic} status=${response.status}`);
        if (response.status === 401 || response.status === 403) retrySeconds = config.authRetrySeconds;
        await delay(retrySeconds * 1000);
        continue;
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error("ntfy response body is not readable");

      while (!shuttingDown) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
          const line = buffer.slice(0, newlineIndex).trim();
          buffer = buffer.slice(newlineIndex + 1);
          if (!line) continue;

          try {
            const payload = JSON.parse(line);
            if (await saveMessage(topic, payload, config)) {
              info(`Stored ntfy message topic=${topic} id=${payload.id}`);
            }
          } catch (error) {
            warn(`Skipping invalid ntfy line topic=${topic}`, error);
          }
        }
      }
    } catch (error) {
      if (!shuttingDown) warn(`ntfy stream disconnected topic=${topic}`, error);
    } finally {
      controllers.delete(controller);
    }

    if (!shuttingDown) {
      debug(`Reconnecting topic ${topic} in ${retrySeconds}s`);
      await delay(retrySeconds * 1000);
    }
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function shutdown(server) {
  if (shuttingDown) return;
  shuttingDown = true;
  info("Stopping ntfy message hub");
  for (const controller of controllers) controller.abort();
  server.close();
  await pool.end();
  process.exit(0);
}

async function main() {
  const config = getConfig();
  await app.prepare();

  const server = http.createServer((req, res) => {
    handle(req, res);
  });

  server.listen(port, hostname, () => {
    info(`Next.js site ready on http://${hostname}:${port}`);
  });

  for (const topic of config.ntfyTopics) {
    subscribeTopic(topic, config).catch((error) => {
      warn(`Subscriber crashed topic=${topic}`, error);
    });
  }

  process.on("SIGINT", () => void shutdown(server));
  process.on("SIGTERM", () => void shutdown(server));
}

main().catch((error) => {
  console.error(new Date().toISOString(), "ERROR", error);
  process.exit(1);
});
