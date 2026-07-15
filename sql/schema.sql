CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(191) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS topics (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(191) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_topics_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ntfy_message_id VARCHAR(64) NOT NULL,
  topic_id BIGINT UNSIGNED NOT NULL,
  recorder_user_id BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  message_text TEXT NULL,
  title VARCHAR(512) NULL,
  priority TINYINT NULL,
  tags JSON NULL,
  click_url TEXT NULL,
  actions JSON NULL,
  ntfy_time INT UNSIGNED NULL,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  raw_json JSON NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_messages_topic_ntfy_id (topic_id, ntfy_message_id),
  KEY idx_messages_received_at (received_at),
  KEY idx_messages_ntfy_time (ntfy_time),
  KEY idx_messages_topic_user (topic_id, recorder_user_id),
  CONSTRAINT fk_messages_topic
    FOREIGN KEY (topic_id) REFERENCES topics (id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_messages_recorder_user
    FOREIGN KEY (recorder_user_id) REFERENCES users (id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
