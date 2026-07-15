"use client";

import { FormEvent, useEffect, useRef, useState, useTransition } from "react";

type Topic = {
  name: string;
  messageCount: number;
  latestReceivedAt: string | null;
};

type TagOption = {
  tag: string;
  messageCount: number;
};

type Message = {
  id: string;
  ntfyMessageId: string;
  messageText: string | null;
  title: string | null;
  priority: number | null;
  tags: unknown;
  clickUrl: string | null;
  actions: unknown;
  ntfyTime: number | null;
  receivedAt: string;
  rawJson: unknown;
  topic: string;
  recorderUser: string;
  sourceUser: string | null;
};

type Filters = {
  topic: string;
  tags: string[];
  q: string;
};

type ViewMode = "timeline" | "grouped";

const PAGE_SIZE = 30;

export default function HomeClient() {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [tags, setTags] = useState<TagOption[]>([]);
  const [filters, setFilters] = useState<Filters>({ topic: "", tags: [], q: "" });
  const [draft, setDraft] = useState<Filters>({ topic: "", tags: [], q: "" });
  const [messages, setMessages] = useState<Message[]>([]);
  const [nextBeforeId, setNextBeforeId] = useState<string | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "empty" | "error">("loading");
  const [viewMode, setViewMode] = useState<ViewMode>("timeline");
  const [openGroups, setOpenGroups] = useState<Set<string>>(new Set());
  const [selectedMessage, setSelectedMessage] = useState<Message | null>(null);
  const [isPending, startTransition] = useTransition();
  const [loadingMore, setLoadingMore] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      const response = await fetch("/api/bootstrap");
      if (!response.ok) throw new Error("Failed to load filters");
      const data = (await response.json()) as { topics: Topic[]; tags: TagOption[] };
      if (!active) return;
      setTopics(data.topics);
      setTags(data.tags);
    }

    bootstrap().catch(() => setStatus("error"));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    loadMessages(filters, controller.signal)
      .then((data) => {
        setMessages(data.messages.reverse());
        setNextBeforeId(data.nextBeforeId);
        setOpenGroups(new Set());
        setStatus(data.messages.length ? "ready" : "empty");
        queueMicrotask(() => {
          if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        });
      })
      .catch((error) => {
        if (error.name !== "AbortError") setStatus("error");
      });

    return () => controller.abort();
  }, [filters]);

  async function loadOlder() {
    if (!nextBeforeId || loadingMore) return;
    const container = scrollRef.current;
    const previousHeight = container?.scrollHeight ?? 0;
    setLoadingMore(true);

    try {
      const data = await loadMessages({ ...filters, beforeId: nextBeforeId });
      setMessages((current) => [...data.messages.reverse(), ...current]);
      setNextBeforeId(data.nextBeforeId);
      requestAnimationFrame(() => {
        if (container) container.scrollTop = container.scrollHeight - previousHeight + container.scrollTop;
      });
    } finally {
      setLoadingMore(false);
    }
  }

  function handleScroll() {
    const container = scrollRef.current;
    if (!container) return;
    if (container.scrollTop < 120) {
      void loadOlder();
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setStatus("loading");
    startTransition(() => setFilters(draft));
  }

  function selectTopic(topic: string) {
    const nextDraft = { ...draft, topic };
    setDraft(nextDraft);
    setStatus("loading");
    startTransition(() => setFilters(nextDraft));
  }

  function toggleGroup(title: string) {
    setOpenGroups((current) => {
      const next = new Set(current);
      if (next.has(title)) next.delete(title);
      else next.add(title);
      return next;
    });
  }

  const groupedMessages = groupByTitle(messages);

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div>
            <p>ntfy</p>
            <strong>讯笺</strong>
          </div>
        </div>

        <section className="channel-card">
          <div className="section-label">通道</div>
          <button className={!draft.topic ? "channel active" : "channel"} onClick={() => selectTopic("")}>
            <span>全部记录</span>
            <em>{topics.reduce((sum, topic) => sum + topic.messageCount, 0)}</em>
          </button>
          {topics.map((topic) => (
            <button
              className={draft.topic === topic.name ? "channel active" : "channel"}
              key={topic.name}
              onClick={() => selectTopic(topic.name)}
            >
              <span>{topic.name}</span>
              <em>{topic.messageCount}</em>
            </button>
          ))}
        </section>

        <div className="sidebar-note">标签来自 ntfy 消息 tags 字段。可多选标签，快速收窄当前通道里的消息。</div>
      </aside>

      <section className="workspace">
        <header className="hero">
          <div>
            <p className="eyebrow">Realtime archive</p>
            <h1>讯笺</h1>
          </div>
          <div className="stats">
            <strong>{messages.length}</strong>
            <span>已展示</span>
          </div>
        </header>

        <form className="filters" onSubmit={submit}>
          <MultiSelect
            label="标签"
            values={draft.tags}
            placeholder="全部标签"
            options={tags.map((tag) => ({
              value: tag.tag,
              label: tag.tag,
              meta: String(tag.messageCount)
            }))}
            onChange={(selectedTags) => setDraft({ ...draft, tags: selectedTags })}
          />
          <label className="search">
            <span>搜索</span>
            <input
              value={draft.q}
              onChange={(event) => setDraft({ ...draft, q: event.target.value })}
              placeholder="搜索正文、标题、标签和原始字段"
            />
          </label>
          <div className="view-switch" aria-label="视图切换">
            <button type="button" className={viewMode === "timeline" ? "active" : ""} onClick={() => setViewMode("timeline")}>
              记录
            </button>
            <button type="button" className={viewMode === "grouped" ? "active" : ""} onClick={() => setViewMode("grouped")}>
              分组
            </button>
          </div>
          <button className="submit-button" type="submit" disabled={isPending}>
            筛选
          </button>
        </form>

        <div className="timeline" ref={scrollRef} onScroll={handleScroll}>
          {loadingMore && <div className="load-more">正在加载更早记录...</div>}
          {status === "loading" && <StateCard title="正在连接数据" text="读取最新 ntfy 消息记录。" />}
          {status === "empty" && <StateCard title="暂无记录" text="换一个通道或筛选条件试试。" />}
          {status === "error" && <StateCard title="加载失败" text="请检查数据库连接或服务日志。" />}
          {status === "ready" && viewMode === "timeline" &&
            messages.map((message) => <MessageCard key={message.id} message={message} onOpen={setSelectedMessage} />)}
          {status === "ready" && viewMode === "grouped" &&
            groupedMessages.map((group) => (
              <section className="message-group" key={group.title}>
                <button className="group-head" type="button" onClick={() => toggleGroup(group.title)}>
                  <span>{group.title}</span>
                  <em>{group.items.length} 条</em>
                </button>
                {openGroups.has(group.title) ? (
                  <div className="group-body">
                    {group.items.map((message) => (
                      <MessageCard key={message.id} message={message} onOpen={setSelectedMessage} compact />
                    ))}
                  </div>
                ) : null}
              </section>
            ))}
        </div>
      </section>

      {selectedMessage ? <MessageModal message={selectedMessage} onClose={() => setSelectedMessage(null)} /> : null}
    </main>
  );
}

function MultiSelect({
  label,
  values,
  placeholder,
  options,
  onChange
}: {
  label: string;
  values: string[];
  placeholder: string;
  options: Array<{ value: string; label: string; meta?: string }>;
  onChange: (value: string[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const selected = new Set(values);
  const labelText =
    values.length === 0
      ? placeholder
      : values.length === 1
        ? options.find((option) => option.value === values[0])?.label ?? values[0]
        : `已选 ${values.length} 个标签`;

  function toggle(value: string) {
    const next = new Set(selected);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    onChange(Array.from(next));
  }

  return (
    <div className="custom-select">
      <span>{label}</span>
      <button type="button" className={open ? "select-trigger open" : "select-trigger"} onClick={() => setOpen(!open)}>
        <strong>{labelText}</strong>
        <i>⌄</i>
      </button>
      {open ? (
        <div className="select-menu">
          <button
            type="button"
            className={!values.length ? "selected" : ""}
            onClick={() => {
              onChange([]);
            }}
          >
            <span>{placeholder}</span>
          </button>
          {options.map((option) => (
            <button
              type="button"
              className={selected.has(option.value) ? "selected" : ""}
              key={option.value}
              onClick={() => {
                toggle(option.value);
              }}
            >
              <span>{selected.has(option.value) ? `✓ ${option.label}` : option.label}</span>
              {option.meta ? <em>{option.meta}</em> : null}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function MessageCard({ message, onOpen, compact = false }: { message: Message; onOpen: (message: Message) => void; compact?: boolean }) {
  return (
    <article className={compact ? "message compact" : "message"} role="button" tabIndex={0} onClick={() => onOpen(message)}>
      <div className="message-top">
        <span className="pill">{message.topic}</span>
        <time>{formatDate(message.receivedAt)}</time>
      </div>
      <h2>{displayTitle(message)}</h2>
      <p className="one-line">{message.messageText || "（空消息）"}</p>
      <div className="message-meta">
        {message.sourceUser ? <span>用户：{message.sourceUser}</span> : null}
        <span>ID：{message.ntfyMessageId}</span>
        {Array.isArray(message.tags) && message.tags.length ? <span>标签：{message.tags.join(", ")}</span> : null}
      </div>
    </article>
  );
}

function MessageModal({ message, onClose }: { message: Message; onClose: () => void }) {
  const parsed = parseContent(message.messageText);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="modal" role="dialog" aria-modal="true" aria-label="消息详情" onClick={(event) => event.stopPropagation()}>
        <header className="modal-head">
          <div>
            <p className="eyebrow">{message.topic}</p>
            <h2>{displayTitle(message)}</h2>
          </div>
          <button type="button" onClick={onClose}>
            关闭
          </button>
        </header>
        <div className="detail-grid">
          <span>时间：{formatFullDate(message.receivedAt)}</span>
          {message.sourceUser ? <span>用户：{message.sourceUser}</span> : null}
          {message.priority ? <span>优先级：{message.priority}</span> : null}
          {Array.isArray(message.tags) && message.tags.length ? <span>标签：{message.tags.join(", ")}</span> : null}
        </div>
        <ParsedContent parsed={parsed} />
        <details className="raw-panel">
          <summary>原始 JSON</summary>
          <pre>{JSON.stringify(message.rawJson, null, 2)}</pre>
        </details>
      </section>
    </div>
  );
}

function ParsedContent({ parsed }: { parsed: ReturnType<typeof parseContent> }) {
  if (parsed.kind === "json") {
    return <pre className="content-block">{JSON.stringify(parsed.value, null, 2)}</pre>;
  }

  if (parsed.kind === "pairs") {
    return (
      <dl className="pair-list">
        {parsed.value.map((pair) => (
          <div key={pair.key}>
            <dt>{pair.key}</dt>
            <dd>{renderTextWithLinks(pair.value)}</dd>
          </div>
        ))}
      </dl>
    );
  }

  return <div className="content-block text-content">{renderTextWithLinks(parsed.value)}</div>;
}

function StateCard({ title, text }: { title: string; text: string }) {
  return (
    <div className="state-card">
      <strong>{title}</strong>
      <span>{text}</span>
    </div>
  );
}

async function loadMessages(filters: Filters & { beforeId?: string }, signal?: AbortSignal) {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) });
  if (filters.topic) params.set("topic", filters.topic);
  if (filters.q) params.set("q", filters.q);
  for (const tag of filters.tags) params.append("tag", tag);
  if (filters.beforeId) params.set("beforeId", filters.beforeId);

  const response = await fetch(`/api/messages?${params.toString()}`, { signal });
  if (!response.ok) throw new Error("Failed to load messages");
  return (await response.json()) as { messages: Message[]; nextBeforeId: string | null };
}

function groupByTitle(messages: Message[]) {
  const groups = new Map<string, Message[]>();
  for (const message of messages) {
    const title = displayTitle(message);
    groups.set(title, [...(groups.get(title) ?? []), message]);
  }
  return Array.from(groups.entries()).map(([title, items]) => ({ title, items }));
}

function displayTitle(message: Message) {
  return normalizeTitle(message.title) || "无标题消息";
}

function normalizeTitle(title: string | null) {
  const normalized = title?.trim().replace(/^"|"$/g, "");
  if (!normalized || ["from", "[from]", "${from}", "{{FROM}}", "{{from}}"].includes(normalized)) return "";
  return normalized;
}

function parseContent(value: string | null) {
  const text = value?.trim() ?? "";
  if (!text) return { kind: "text" as const, value: "（空消息）" };

  if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
    try {
      return { kind: "json" as const, value: JSON.parse(text) as unknown };
    } catch {
      // Fall through to text parsing.
    }
  }

  const lines = text.split(/\r?\n/).filter(Boolean);
  const pairs = lines
    .map((line) => line.match(/^\s*([^:：=]{1,40})\s*[:：=]\s*(.+)\s*$/))
    .filter((match): match is RegExpMatchArray => Boolean(match))
    .map((match) => ({ key: match[1].trim(), value: match[2].trim() }));

  if (pairs.length >= 2 && pairs.length === lines.length) {
    return { kind: "pairs" as const, value: pairs };
  }

  return { kind: "text" as const, value: text };
}

function renderTextWithLinks(text: string) {
  const parts = text.split(/(https?:\/\/[^\s]+)/g);
  return parts.map((part, index) =>
    /^https?:\/\//.test(part) ? (
      <a href={part} target="_blank" rel="noreferrer" key={`${part}-${index}`}>
        {part}
      </a>
    ) : (
      <span key={`${part}-${index}`}>{part}</span>
    )
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatFullDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(value));
}
