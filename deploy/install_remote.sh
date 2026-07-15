#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-replace-with-remote-host}"
REMOTE_USER="${REMOTE_USER:-replace-with-remote-user}"
REMOTE_PASSWORD="${REMOTE_PASSWORD:-replace-with-remote-password}"
APP_DIR="${APP_DIR:-/opt/ntfy-message-store}"
SERVICE_USER="${SERVICE_USER:-ntfy-store}"
DB_NAME="${DB_NAME:-ntfy_message_store}"
DB_USER="${DB_USER:-ntfy_store}"
DB_PASSWORD="${DB_PASSWORD:-replace-with-db-password}"
NTFY_BASE_URL="${NTFY_BASE_URL:-https://example.com}"
NTFY_TOPICS="${NTFY_TOPICS:-reports,messages}"
NTFY_TOKEN="${NTFY_TOKEN:-replace-with-ntfy-token}"
RECORDER_USER="${RECORDER_USER:-replace-with-recorder-user}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE="$(mktemp -t ntfy-message-store.XXXXXX.tar.gz)"

cleanup() {
  rm -f "$ARCHIVE"
}
trap cleanup EXIT

tar \
  --exclude='.git' \
  --exclude='.venv' \
  --exclude='__pycache__' \
  -czf "$ARCHIVE" \
  -C "$ROOT_DIR" .

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no "$REMOTE_USER@$REMOTE_HOST" "mkdir -p /tmp/ntfy-message-store-deploy"
sshpass -p "$REMOTE_PASSWORD" scp -o StrictHostKeyChecking=no "$ARCHIVE" "$REMOTE_USER@$REMOTE_HOST:/tmp/ntfy-message-store-deploy/app.tar.gz"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no "$REMOTE_USER@$REMOTE_HOST" \
  "REMOTE_PASSWORD='$REMOTE_PASSWORD' APP_DIR='$APP_DIR' SERVICE_USER='$SERVICE_USER' DB_NAME='$DB_NAME' DB_USER='$DB_USER' DB_PASSWORD='$DB_PASSWORD' NTFY_BASE_URL='$NTFY_BASE_URL' NTFY_TOPICS='$NTFY_TOPICS' NTFY_TOKEN='$NTFY_TOKEN' RECORDER_USER='$RECORDER_USER' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

sudo_cmd() {
  printf '%s\n' "$REMOTE_PASSWORD" | sudo -S "$@"
}

sudo_cmd apt-get update
sudo_cmd apt-get install -y python3-venv python3-pip

if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  sudo_cmd useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
fi

sudo_cmd mkdir -p "$APP_DIR"
sudo_cmd tar -xzf /tmp/ntfy-message-store-deploy/app.tar.gz -C "$APP_DIR"
sudo_cmd chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"

cat >/tmp/ntfy-message-store-init.sql <<SQL
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER, REFERENCES ON $DB_NAME.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL
sudo_cmd sh -c "mysql < /tmp/ntfy-message-store-init.sql"
rm -f /tmp/ntfy-message-store-init.sql

sudo_cmd sh -c "mysql '$DB_NAME' < '$APP_DIR/sql/schema.sql'"

cat >/tmp/ntfy-message-store.env <<ENV
NTFY_BASE_URL=$NTFY_BASE_URL
NTFY_TOPICS=$NTFY_TOPICS
NTFY_TOKEN=$NTFY_TOKEN
RECORDER_USER=$RECORDER_USER
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=$DB_NAME
MYSQL_USER=$DB_USER
MYSQL_PASSWORD=$DB_PASSWORD
LOG_LEVEL=INFO
RECONNECT_SECONDS=5
AUTH_RETRY_SECONDS=300
ENV
sudo_cmd install -m 600 -o root -g root /tmp/ntfy-message-store.env /etc/ntfy-message-store.env
rm -f /tmp/ntfy-message-store.env
sudo_cmd chmod 600 /etc/ntfy-message-store.env
sudo_cmd chown root:root /etc/ntfy-message-store.env

sudo_cmd cp "$APP_DIR/deploy/ntfy-message-store.service" /etc/systemd/system/ntfy-message-store.service

if [ ! -d "$APP_DIR/.venv" ]; then
  sudo_cmd -u "$SERVICE_USER" python3 -m venv "$APP_DIR/.venv"
fi
sudo_cmd -u "$SERVICE_USER" "$APP_DIR/.venv/bin/pip" install --upgrade pip
sudo_cmd -u "$SERVICE_USER" "$APP_DIR/.venv/bin/pip" install -r "$APP_DIR/requirements.txt"

sudo_cmd systemctl daemon-reload
sudo_cmd systemctl enable ntfy-message-store.service
sudo_cmd systemctl restart ntfy-message-store.service
sudo_cmd systemctl --no-pager --full status ntfy-message-store.service
REMOTE_SCRIPT
