from __future__ import annotations

import json
import logging
import os
import signal
import sys
import threading
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

import pymysql
from pymysql.connections import Connection


LOGGER = logging.getLogger("ntfy_store")
STOP_EVENT = threading.Event()
USER_AGENT = "ntfy-message-store/1.0"


@dataclass(frozen=True)
class Config:
    ntfy_base_url: str
    ntfy_topics: tuple[str, ...]
    ntfy_token: str
    recorder_user: str
    mysql_host: str
    mysql_port: int
    mysql_database: str
    mysql_user: str
    mysql_password: str
    reconnect_seconds: float
    auth_retry_seconds: float

    @classmethod
    def from_env(cls) -> "Config":
        token = require_env("NTFY_TOKEN")
        topics = tuple(
            topic.strip()
            for topic in os.getenv("NTFY_TOPICS", "reports,messages").split(",")
            if topic.strip()
        )
        if not topics:
            raise ValueError("NTFY_TOPICS must contain at least one topic")

        return cls(
            ntfy_base_url=os.getenv("NTFY_BASE_URL", "https://example.com").rstrip("/"),
            ntfy_topics=topics,
            ntfy_token=token,
            recorder_user=os.getenv("RECORDER_USER", "lvziw"),
            mysql_host=os.getenv("MYSQL_HOST", "127.0.0.1"),
            mysql_port=int(os.getenv("MYSQL_PORT", "3306")),
            mysql_database=os.getenv("MYSQL_DATABASE", "ntfy_message_store"),
            mysql_user=os.getenv("MYSQL_USER", "ntfy_store"),
            mysql_password=require_env("MYSQL_PASSWORD"),
            reconnect_seconds=float(os.getenv("RECONNECT_SECONDS", "5")),
            auth_retry_seconds=float(os.getenv("AUTH_RETRY_SECONDS", "300")),
        )


class MessageStore:
    def __init__(self, config: Config) -> None:
        self.config = config
        self._connection: Connection | None = None
        self._topic_ids: dict[str, int] = {}
        self._recorder_user_id: int | None = None

    def close(self) -> None:
        if self._connection:
            self._connection.close()
            self._connection = None

    def save_message(self, topic: str, payload: dict[str, Any]) -> bool:
        if payload.get("event") != "message":
            return False

        message_id = payload.get("id")
        if not message_id:
            LOGGER.warning("Skipping message without ntfy id: %s", payload)
            return False

        sanitized = strip_attachment(payload)
        topic_id = self._get_topic_id(topic)
        recorder_user_id = self._get_recorder_user_id()

        with self._connect().cursor() as cursor:
            affected = cursor.execute(
                """
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
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    message_id,
                    topic_id,
                    recorder_user_id,
                    sanitized.get("event", "message"),
                    sanitized.get("message"),
                    sanitized.get("title"),
                    sanitized.get("priority"),
                    json_dumps_or_null(sanitized.get("tags")),
                    sanitized.get("click"),
                    json_dumps_or_null(sanitized.get("actions")),
                    sanitized.get("time"),
                    json.dumps(sanitized, ensure_ascii=False, separators=(",", ":")),
                ),
            )
        self._connect().commit()
        return affected == 1

    def _get_recorder_user_id(self) -> int:
        if self._recorder_user_id is None:
            self._recorder_user_id = self._get_or_create_id(
                "users",
                "username",
                self.config.recorder_user,
            )
        return self._recorder_user_id

    def _get_topic_id(self, topic: str) -> int:
        if topic not in self._topic_ids:
            self._topic_ids[topic] = self._get_or_create_id("topics", "name", topic)
        return self._topic_ids[topic]

    def _get_or_create_id(self, table: str, column: str, value: str) -> int:
        with self._connect().cursor() as cursor:
            cursor.execute(
                f"INSERT IGNORE INTO {table} ({column}) VALUES (%s)",
                (value,),
            )
            cursor.execute(f"SELECT id FROM {table} WHERE {column} = %s", (value,))
            row = cursor.fetchone()
        self._connect().commit()
        if not row:
            raise RuntimeError(f"Failed to create or load {table}.{column}={value}")
        return int(row["id"])

    def _connect(self) -> Connection:
        if self._connection and self._connection.open:
            try:
                self._connection.ping(reconnect=True)
                return self._connection
            except pymysql.MySQLError:
                self.close()

        self._connection = pymysql.connect(
            host=self.config.mysql_host,
            port=self.config.mysql_port,
            user=self.config.mysql_user,
            password=self.config.mysql_password,
            database=self.config.mysql_database,
            charset="utf8mb4",
            autocommit=False,
            cursorclass=pymysql.cursors.DictCursor,
        )
        return self._connection


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise ValueError(f"{name} is required")
    return value


def json_dumps_or_null(value: Any) -> str | None:
    if value is None:
        return None
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def strip_attachment(payload: dict[str, Any]) -> dict[str, Any]:
    sanitized = dict(payload)
    sanitized.pop("attachment", None)
    return sanitized


def topic_stream_url(config: Config, topic: str) -> str:
    encoded_topic = quote(topic, safe="")
    return f"{config.ntfy_base_url}/{encoded_topic}/json?since=all"


def subscribe_topic(config: Config, topic: str) -> None:
    url = topic_stream_url(config, topic)
    headers = {
        "Authorization": f"Bearer {config.ntfy_token}",
        "User-Agent": USER_AGENT,
    }
    store = MessageStore(config)

    try:
        while not STOP_EVENT.is_set():
            retry_seconds = config.reconnect_seconds
            try:
                LOGGER.info("Subscribing to topic %s", topic)
                request = Request(url, headers=headers, method="GET")
                with urlopen(request, timeout=90) as response:
                    for raw_line in response:
                        if STOP_EVENT.is_set():
                            break

                        line = raw_line.decode("utf-8").strip()
                        if not line:
                            continue

                        try:
                            payload = json.loads(line)
                        except json.JSONDecodeError:
                            LOGGER.warning("Skipping invalid JSON from %s: %r", topic, line)
                            continue

                        if store.save_message(topic, payload):
                            LOGGER.info(
                                "Stored ntfy message topic=%s id=%s",
                                topic,
                                payload.get("id"),
                            )
            except HTTPError as exc:
                LOGGER.error("ntfy HTTP error topic=%s status=%s: %s", topic, exc.code, exc.reason)
                if exc.code in (401, 403):
                    retry_seconds = config.auth_retry_seconds
            except (URLError, TimeoutError, OSError) as exc:
                LOGGER.warning("ntfy stream disconnected topic=%s: %s", topic, exc)
            except pymysql.MySQLError as exc:
                LOGGER.error("MySQL error topic=%s: %s", topic, exc)
                store.close()
            except Exception:
                LOGGER.exception("Unexpected subscriber error topic=%s", topic)

            if not STOP_EVENT.wait(retry_seconds):
                LOGGER.info("Reconnecting topic %s", topic)
    finally:
        store.close()


def configure_logging() -> None:
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(
        level=getattr(logging, level_name, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(threadName)s %(message)s",
    )


def install_signal_handlers() -> None:
    def handle_signal(signum: int, _frame: object) -> None:
        LOGGER.info("Received signal %s, stopping", signum)
        STOP_EVENT.set()

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)


def main() -> int:
    configure_logging()
    install_signal_handlers()

    try:
        config = Config.from_env()
    except Exception as exc:
        LOGGER.error("Invalid configuration: %s", exc)
        return 2

    threads = [
        threading.Thread(
            target=subscribe_topic,
            name=f"ntfy-{topic}",
            args=(config, topic),
        )
        for topic in config.ntfy_topics
    ]

    for thread in threads:
        thread.start()

    while any(thread.is_alive() for thread in threads):
        for thread in threads:
            thread.join(timeout=1)

    return 0


if __name__ == "__main__":
    sys.exit(main())
