import type { RowDataPacket } from "mysql2";

import { getPool, type MessageRow } from "./db";

export type MessageFilters = {
  topic?: string;
  tags?: string[];
  q?: string;
  beforeId?: number;
  limit?: number;
};

const SOURCE_USER_EXPRESSION = `
  CASE
    WHEN m.title IS NULL THEN NULL
    WHEN TRIM(BOTH '"' FROM TRIM(m.title)) IN ('', 'from', '[from]', '\${from}', '{{FROM}}', '{{from}}') THEN NULL
    ELSE TRIM(BOTH '"' FROM TRIM(m.title))
  END
`;

export async function listTopics() {
  const [rows] = await getPool().query<RowDataPacket[]>(
    `
    SELECT
      t.name,
      COUNT(m.id) AS message_count,
      MAX(m.received_at) AS latest_received_at
    FROM topics t
    LEFT JOIN messages m ON m.topic_id = t.id
    GROUP BY t.id, t.name
    ORDER BY latest_received_at DESC, t.name ASC
    `
  );

  return rows.map((row) => ({
    name: String(row.name),
    messageCount: Number(row.message_count ?? 0),
    latestReceivedAt: row.latest_received_at ? new Date(row.latest_received_at).toISOString() : null
  }));
}

export async function listTags() {
  const [rows] = await getPool().query<RowDataPacket[]>(
    `
    SELECT jt.tag, COUNT(*) AS message_count
    FROM messages m
    JOIN JSON_TABLE(
      m.tags,
      '$[*]' COLUMNS(tag VARCHAR(191) PATH '$')
    ) AS jt
    WHERE jt.tag IS NOT NULL AND jt.tag <> ''
    GROUP BY jt.tag
    ORDER BY jt.tag ASC
    `
  );

  return rows.map((row) => ({
    tag: String(row.tag),
    messageCount: Number(row.message_count ?? 0)
  }));
}

export async function listMessages(filters: MessageFilters) {
  const clauses: string[] = [];
  const values: Array<string | number> = [];

  if (filters.topic) {
    clauses.push("t.name = ?");
    values.push(filters.topic);
  }

  if (filters.tags?.length) {
    clauses.push(`(${filters.tags.map(() => "JSON_CONTAINS(m.tags, JSON_QUOTE(?), '$')").join(" OR ")})`);
    values.push(...filters.tags);
  }

  if (filters.q) {
    clauses.push("(m.message_text LIKE ? OR m.title LIKE ? OR CAST(m.tags AS CHAR) LIKE ? OR CAST(m.raw_json AS CHAR) LIKE ?)");
    values.push(`%${filters.q}%`, `%${filters.q}%`, `%${filters.q}%`, `%${filters.q}%`);
  }

  if (filters.beforeId) {
    clauses.push("m.id < ?");
    values.push(filters.beforeId);
  }

  const where = clauses.length ? `WHERE ${clauses.join(" AND ")}` : "";
  const limit = Math.min(Math.max(filters.limit ?? 30, 1), 80);
  values.push(limit);

  const [rows] = await getPool().query<Array<MessageRow & RowDataPacket>>(
    `
    SELECT
      CAST(m.id AS CHAR) AS id,
      m.ntfy_message_id,
      m.event_type,
      m.message_text,
      m.title,
      m.priority,
      m.tags,
      m.click_url,
      m.actions,
      m.ntfy_time,
      m.received_at,
      m.raw_json,
      t.name AS topic,
      u.username AS recorder_user,
      ${SOURCE_USER_EXPRESSION} AS source_user
    FROM messages m
    JOIN topics t ON t.id = m.topic_id
    JOIN users u ON u.id = m.recorder_user_id
    ${where}
    ORDER BY m.id DESC
    LIMIT ?
    `,
    values
  );

  const messages = rows.map((row) => ({
    id: row.id,
    ntfyMessageId: row.ntfy_message_id,
    eventType: row.event_type,
    messageText: row.message_text,
    title: row.title,
    priority: row.priority,
    tags: normalizeJson(row.tags),
    clickUrl: row.click_url,
    actions: normalizeJson(row.actions),
    ntfyTime: row.ntfy_time,
    receivedAt: new Date(row.received_at).toISOString(),
    rawJson: normalizeJson(row.raw_json),
    topic: row.topic,
    recorderUser: row.recorder_user,
    sourceUser: row.source_user
  }));

  return {
    messages,
    nextBeforeId: messages.length === limit ? messages[messages.length - 1]?.id ?? null : null
  };
}

function normalizeJson(value: unknown) {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}
