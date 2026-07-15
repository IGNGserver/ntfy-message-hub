# 讯笺

把散落在 ntfy 里的通知，收进一处安静、清晰的消息空间。

讯笺会持续收集你关注的话题消息，帮你按频道、标签和关键词整理记录。你可以在浏览器里查阅，也可以用 Android 应用随时打开最近的消息。

## 你可以用讯笺做什么

- 把多个 ntfy 话题汇总到一个站点和一个应用里
- 按时间浏览消息，也可以按标题分组查看
- 用频道、标签和关键词快速找到旧消息
- 打开消息详情，查看正文、标签、来源和原始 JSON
- 在 Android 端缓存消息，网络暂时不可用时继续查看已有记录
- 只保存消息文本和元数据，不下载或保存附件内容

## 开始使用

### 使用 Android 应用

从 [v1.0.0 Release](https://github.com/IGNGserver/ntfy-message-hub/releases/tag/v1.0.0) 下载 `xunjian-1.0.0-release.apk`，安装后填写你的讯笺站点地址和访问密钥即可使用。

### 使用 Web 站点

讯笺站点需要连接 MySQL，并通过环境变量连接 ntfy。先复制配置示例：

```bash
cp web/.env.example web/.env
```

然后填写以下信息：

- `NTFY_BASE_URL`：你的 ntfy 服务地址
- `NTFY_TOPICS`：要收集的话题，多个话题用逗号分隔
- `NTFY_TOKEN`：访问 ntfy 所需的 Bearer token
- `ACCESS_KEY`：登录讯笺站点时使用的访问密钥
- `MYSQL_*`：MySQL 连接信息

本地运行 Web 站点：

```bash
cd web
npm install
npm run dev
```

浏览器打开终端显示的本地地址即可。

## 数据与隐私

讯笺的公开仓库不包含任何个人站点地址、账号、密码或访问令牌。请把自己的配置保存在 `.env` 文件中，不要提交到 Git。

默认情况下，讯笺保存消息正文和必要的消息元数据；消息中的 `attachment` 字段会被剔除，附件本身不会被下载。

## 给开发者

项目由三个部分组成：

- `web/`：Web 站点和消息采集服务
- `android/`：Jetpack Compose Android 应用
- `sql/`：MySQL 表结构

根目录的 `VERSION` 是站点和 Android 应用共用的版本源。每次发布用户可见更新时，请修改它，并使用对应的 `v<version>` Git tag 创建 Release。

提交前运行公开仓库审计：

```powershell
pwsh -File scripts/audit-public.ps1
```

当前版本：`1.0.0`
