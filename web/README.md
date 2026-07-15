# 讯笺 Web

讯笺的 Next.js 站点和后端采集器。启动后会同时：

- 订阅 `NTFY_TOPICS` 中的 ntfy JSON 流。
- 将 `event=message` 的消息写入 MySQL，保留原 Python 程序的去重和附件剔除逻辑。
- 提供消息查询 API 和 SaaS 风格前端。

## 本地运行

```bash
cp .env.example .env
npm install
npm run dev
```

生产模式使用自定义 Node server：

```bash
npm run build
npm run start
```

## 远端部署

```bash
REMOTE_HOST=<remote-host> REMOTE_USER=<remote-user> REMOTE_PASSWORD=<remote-password> ACCESS_KEY=<access-key> ./deploy/install_remote.sh
```

部署目标地址、端口和凭据请在环境变量中配置，默认服务名为 `ntfy-message-hub.service`。
