#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-replace-with-remote-host}"
REMOTE_USER="${REMOTE_USER:-replace-with-remote-user}"
REMOTE_PASSWORD="${REMOTE_PASSWORD:-replace-with-remote-password}"
APP_DIR="${APP_DIR:-/opt/ntfy-message-hub}"
SERVICE_USER="${SERVICE_USER:-ntfy-hub}"
SERVICE_NAME="${SERVICE_NAME:-ntfy-message-hub}"
PORT="${PORT:-47183}"
DB_NAME="${DB_NAME:-ntfy_message_store}"
DB_USER="${DB_USER:-ntfy_store}"
DB_PASSWORD="${DB_PASSWORD:-replace-with-db-password}"
NTFY_BASE_URL="${NTFY_BASE_URL:-https://example.com}"
NTFY_TOPICS="${NTFY_TOPICS:-reports,messages}"
NTFY_TOKEN="${NTFY_TOKEN:-replace-with-ntfy-token}"
RECORDER_USER="${RECORDER_USER:-replace-with-recorder-user}"
ACCESS_KEY="${ACCESS_KEY:-replace-with-access-key}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_DIR="$(cd "$ROOT_DIR/.." && pwd)"
ARCHIVE="$(mktemp -t ntfy-message-hub.XXXXXX.tar.gz)"

cleanup() {
  rm -f "$ARCHIVE"
}
trap cleanup EXIT

tar \
  --exclude='.git' \
  --exclude='.next' \
  --exclude='node_modules' \
  --exclude='npm-debug.log*' \
  -czf "$ARCHIVE" \
  -C "$ROOT_DIR" .

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no "$REMOTE_USER@$REMOTE_HOST" "mkdir -p /tmp/ntfy-message-hub-deploy"
sshpass -p "$REMOTE_PASSWORD" scp -o StrictHostKeyChecking=no "$ARCHIVE" "$REMOTE_USER@$REMOTE_HOST:/tmp/ntfy-message-hub-deploy/app.tar.gz"
sshpass -p "$REMOTE_PASSWORD" scp -o StrictHostKeyChecking=no "$PROJECT_DIR/sql/schema.sql" "$REMOTE_USER@$REMOTE_HOST:/tmp/ntfy-message-hub-deploy/schema.sql"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no "$REMOTE_USER@$REMOTE_HOST" \
  "REMOTE_PASSWORD='$REMOTE_PASSWORD' APP_DIR='$APP_DIR' SERVICE_USER='$SERVICE_USER' SERVICE_NAME='$SERVICE_NAME' PORT='$PORT' DB_NAME='$DB_NAME' DB_USER='$DB_USER' DB_PASSWORD='$DB_PASSWORD' NTFY_BASE_URL='$NTFY_BASE_URL' NTFY_TOPICS='$NTFY_TOPICS' NTFY_TOKEN='$NTFY_TOKEN' RECORDER_USER='$RECORDER_USER' ACCESS_KEY='$ACCESS_KEY' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

sudo_cmd() {
  printf '%s\n' "$REMOTE_PASSWORD" | sudo -S "$@"
}

sudo_cmd apt-get update
sudo_cmd apt-get install -y mysql-client
if ! command -v node >/dev/null 2>&1; then
  sudo_cmd apt-get install -y nodejs
fi
if ! command -v npm >/dev/null 2>&1; then
  sudo_cmd apt-get install -y npm
fi

if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  sudo_cmd useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
fi

for old_service in ntfy-store ntfy-message-store; do
  if systemctl list-unit-files "${old_service}.service" >/dev/null 2>&1 || systemctl status "${old_service}.service" >/dev/null 2>&1; then
    sudo_cmd systemctl disable --now "${old_service}.service" || true
    sudo_cmd rm -f "/etc/systemd/system/${old_service}.service"
  fi
done

OLD_APP_DIR="${OLD_APP_DIR:-/opt/ntfy-storage}"
sudo_cmd rm -rf "$OLD_APP_DIR" /opt/ntfy-message-store
sudo_cmd rm -f /etc/ntfy-message-store.env

sudo_cmd mkdir -p "$APP_DIR"
sudo_cmd rm -rf "$APP_DIR"/*
sudo_cmd tar -xzf /tmp/ntfy-message-hub-deploy/app.tar.gz -C "$APP_DIR"
sudo_cmd chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"

cat >/tmp/ntfy-message-hub-init.sql <<SQL
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER, REFERENCES ON $DB_NAME.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL
sudo_cmd sh -c "mysql < /tmp/ntfy-message-hub-init.sql"
rm -f /tmp/ntfy-message-hub-init.sql
sudo_cmd sh -c "mysql '$DB_NAME' < /tmp/ntfy-message-hub-deploy/schema.sql"

cat >/tmp/ntfy-message-hub.env <<ENV
NTFY_BASE_URL=$NTFY_BASE_URL
NTFY_TOPICS=$NTFY_TOPICS
NTFY_TOKEN=$NTFY_TOKEN
RECORDER_USER=$RECORDER_USER
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=$DB_NAME
MYSQL_USER=$DB_USER
MYSQL_PASSWORD=$DB_PASSWORD
HOST=0.0.0.0
PORT=$PORT
ACCESS_KEY=$ACCESS_KEY
LOG_LEVEL=info
RECONNECT_SECONDS=5
AUTH_RETRY_SECONDS=300
ENV
sudo_cmd install -m 600 -o root -g root /tmp/ntfy-message-hub.env /etc/ntfy-message-hub.env
rm -f /tmp/ntfy-message-hub.env

sudo_cmd cp "$APP_DIR/deploy/ntfy-message-hub.service" "/etc/systemd/system/${SERVICE_NAME}.service"
sudo_cmd chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"
sudo_cmd -u "$SERVICE_USER" npm --prefix "$APP_DIR" ci
sudo_cmd -u "$SERVICE_USER" npm --prefix "$APP_DIR" run build

sudo_cmd systemctl daemon-reload
sudo_cmd systemctl enable "${SERVICE_NAME}.service"
sudo_cmd systemctl restart "${SERVICE_NAME}.service"
sudo_cmd systemctl --no-pager --full status "${SERVICE_NAME}.service"
sudo_cmd ss -ltnp | grep ":${PORT} " || true
REMOTE_SCRIPT
