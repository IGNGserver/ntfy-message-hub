# 讯笺

讯笺是一个 ntfy 消息归档与浏览系统，包含消息采集服务、Web 站点和 Android 应用。它订阅配置的话题，将消息按话题和记录用户写入 MySQL。服务只记录 ntfy 消息文本和元数据，不下载、不保存附件内容，也会从原始 JSON 中剔除 `attachment` 字段。

当前版本：`1.0.0`。站点和 Android 应用共用根目录 `VERSION` 文件，每次用户可见更新都应同步修改该文件。

## 目录

- `src/ntfy_store/`：Python 常驻订阅服务
- `sql/schema.sql`：MySQL 表结构
- `deploy/ntfy-message-store.service`：systemd 单元模板
- `deploy/install_remote.sh`：部署到 Ubuntu 目标机的脚本

## 本地配置示例

复制 `.env.example` 为 `.env` 后修改：

```bash
cp .env.example .env
```

关键配置：

- `NTFY_BASE_URL`：ntfy 服务地址，例如 `https://example.com`
- `NTFY_TOPICS`：逗号分隔的话题列表，默认 `reports,messages`
- `NTFY_TOKEN`：Bearer token
- `RECORDER_USER`：这套存储系统的记录用户标识
- `MYSQL_*`：MySQL 连接信息

## 运行

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python -m ntfy_store
```

## 远端部署

部署脚本只接受通过环境变量传入的主机、账号和凭据，仓库不保存任何个人部署信息。

请先通过环境变量提供目标机和凭据，再执行：

```bash
./deploy/install_remote.sh
```

脚本会在远端创建：

- MySQL 数据库：`ntfy_message_store`
- MySQL 用户：`ntfy_store`
- 应用目录：`/opt/ntfy-message-store`
- 配置文件：`/etc/ntfy-message-store.env`
- systemd 服务：`ntfy-message-store.service`

部署后查看状态：

```bash
ssh <remote-user>@<remote-host> 'systemctl status ntfy-message-store --no-pager'
```
