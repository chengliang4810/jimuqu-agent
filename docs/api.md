# HTTP API

本文记录当前源码对外暴露的 HTTP API、认证方式和通用响应。接口仍处于快速迭代阶段；Controller 注解是路径与方法的最终依据，调用方不应依赖未记录的内部字段。

默认地址为 `http://127.0.0.1:8080`。除健康检查外，业务接口统一位于 `/api/`。

## 认证与浏览器边界

Dashboard API 支持两种认证方式：

- API 客户端使用 `Authorization: Bearer <dashboard-access-token>`。
- 浏览器先用长期 Bearer 调用 `POST /api/auth/session`，换取空闲超时 30 分钟、绝对最长 8 小时的 HttpOnly 短会话 Cookie；之后不再把长期令牌保存在浏览器持久存储中。

浏览器的非 GET/HEAD/OPTIONS 写请求必须同源。短会话写请求还必须携带 `Origin`；没有 `Origin` 的 CLI 或服务端客户端只能使用 Bearer。跨域预检只允许服务端认可的同源地址。

以下接口不要求 Dashboard Bearer 或短会话，但仍有各自的安全约束：

| 方法 | 路径                                              | 约束与用途                                                                   |
| ---- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| GET  | `/health`                                         | 公开存活检查                                                                 |
| GET  | `/health/detailed`                                | 公开运行摘要，不返回密钥                                                     |
| GET  | `/api/status`                                     | 公开的最小 Dashboard 状态                                                    |
| GET  | `/api/model/info`                                 | 公开的当前模型摘要                                                           |
| GET  | `/api/config/defaults`                            | 公开默认配置描述                                                             |
| GET  | `/api/config/schema`                              | 公开配置字段定义                                                             |
| POST | `/api/gateway/message`                            | 独立使用 HMAC 签名认证，见下文                                               |

其余 `/api/*` 默认均需 Dashboard Bearer 或有效短会话。

## 会话认证接口

| 方法   | 路径                | 说明                                    |
| ------ | ------------------- | --------------------------------------- |
| POST   | `/api/auth/session` | 用 Bearer 换取 HttpOnly 短会话          |
| GET    | `/api/auth/session` | 检查当前短会话；Bearer 不能替代会话检查 |
| DELETE | `/api/auth/session` | 撤销当前短会话并清除 Cookie             |

成功响应不包含长期令牌或会话票据：

```json
{
  "success": true,
  "data": {
    "authenticated": true,
    "auth_method": "session"
  }
}
```

## 网关消息注入

`POST /api/gateway/message` 用于可信外部适配器或调试工具向统一网关注入消息。它不使用 Dashboard Bearer，而是读取 `solonclaw.gateway.injectionSecret` 并校验以下请求头：

```text
X-solonclaw-Timestamp: <Unix 秒级时间戳>
X-solonclaw-Nonce: <重放窗口内唯一随机串>
X-solonclaw-Signature: sha256=<小写十六进制 HMAC>
```

签名原文必须与服务端完全一致：

```text
timestamp + "." + nonce + "." + 原始 HTTP 请求体
```

签名算法为 HMAC-SHA256。时间戳必须位于配置的重放窗口内，同一 nonce 不能重复使用，请求体还必须满足 `injectionMaxBodyBytes` 限制。

最小请求体：

```json
{
  "platform": "DINGTALK",
  "chatId": "chat-id",
  "userId": "user-id",
  "content": "hello"
}
```

认证失败使用 401/403/409/413 等 HTTP 状态；响应体是 `GatewayReply`，不是 Dashboard 通用响应。

## Dashboard 接口目录

下表覆盖当前 Controller 暴露的业务路径。`{id}`、`{runId}`、`{name}` 等表示路径参数；查询参数和请求体字段以相应页面调用和 Controller 校验为准。

| 领域           | 方法与路径                                                                                                                                                                                                                              | 用途                                    |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| 状态           | `GET /api/status`、`GET /api/model/info`                                                                                                                                                                                                | 运行状态与当前模型摘要                  |
| 配置           | `GET/PUT /api/config`、`GET/PUT /api/config/raw`、`GET /api/config/diagnostics`                                                                                                                                                         | 完整配置、原始配置和诊断                |
| 运行配置       | `GET/PUT/DELETE /api/workspace-config`、`POST /api/workspace-config/reveal`                                                                                                                                                             | 白名单运行配置读写、删除和受限密钥揭示  |
| Provider       | `GET/POST /api/providers`、`PUT/DELETE /api/providers/{providerKey}`、`POST /api/providers/models`、`POST /api/providers/validate`                                                                                                      | Provider 增删改查、模型发现和验证       |
| 模型路由       | `GET /api/models`、`GET /api/models/health`、`GET/PUT /api/model/task-routes`、`PUT /api/model/default`、`PUT /api/model/fallbacks`                                                                                                     | 模型目录、健康状态、任务路由和故障转移  |
| 会话           | `GET /api/sessions`、`GET /api/profiles/sessions`、`GET/PATCH/DELETE /api/sessions/{id}`                                                                                                                                                | 会话查询、元数据修改与删除              |
| 会话内容       | `GET /api/sessions/{id}/{messages\|recap\|trajectory\|tree\|latest-descendant}`、`POST /api/sessions/{id}/trajectory/save`                                                                                                              | 消息、回顾、轨迹保存和分支关系          |
| 检查点         | `GET /api/sessions/{id}/checkpoints`、`GET /api/checkpoints/{id}/preview`、`POST /api/checkpoints/{id}/rollback`                                                                                                                        | 检查点预览与回滚                        |
| 搜索           | `GET /api/search`                                                                                                                                                                                                                       | 跨会话搜索                              |
| Dashboard 对话 | `POST /api/chat/uploads`、`POST /api/chat/runs`、`GET /api/chat/runs/{runId}/events`、`POST /api/chat/runs/{runId}/cancel`                                                                                                              | 上传附件、启动对话、SSE 事件与取消      |
| Run            | `GET /api/runs/{runId}`、`GET /api/runs/{runId}/{detail\|events\|tools\|subagents\|recoveries\|commands}`                                                                                                                               | Run 状态、事件、工具与恢复信息          |
| Run 控制       | `POST /api/runs/{runId}/control`、`POST /api/runs/subagents/{subagentId}/control`                                                                                                                                                       | 中断、继续或控制活动运行                |
| Run 列表       | `GET /api/sessions/{sessionId}/runs`、`GET /api/runs/recoverable`、`GET /api/runs/subagents/active`                                                                                                                                     | 会话运行、可恢复运行和活动子 Agent      |
| Profile        | `GET/POST /api/profiles`、`GET/PATCH/DELETE /api/profiles/{name}`、`POST /api/profiles/import`、`POST /api/profiles/install`                                                                                                            | Profile 生命周期                        |
| Profile 详情   | `GET/PUT /api/profiles/{name}/soul`、`PUT /api/profiles/{name}/{description\|model\|alias}`、`DELETE /api/profiles/{name}/alias`                                                                                                        | Profile 身份、模型和别名                |
| Profile 描述   | `POST /api/profiles/{name}/describe-auto`                                                                                                                                                                                               | 自动生成 Profile 描述                   |
| Profile 分发   | `GET /api/profiles/{name}/distribution`、`POST /api/profiles/{name}/distribution/update`、`GET /api/profiles/{name}/export`                                                                                                             | 分发状态、更新和导出                    |
| Profile 任务   | `GET/POST /api/profile-tasks`、`GET /api/profile-tasks/{taskId}`、`POST /api/profile-tasks/{taskId}/{retry\|cancel}`                                                                                                                    | 专家任务创建、查询、重试和取消          |
| 渠道扫码       | `POST /api/gateway/setup/{platform}/qr`、`GET /api/gateway/setup/{platform}/qr/{ticket}`                                                                                                                                                | 微信、飞书、钉钉、企微和 QQBot 配置流程 |
| 渠道配对       | `GET /api/gateway/pairing`、`POST /api/gateway/pairing/{claim-owner\|primary}`、`POST /api/gateway/pairing/welcome/retry`、`DELETE /api/gateway/pairing/owner`                                                                          | 所有者、主渠道和欢迎消息配对            |
| 平台工具集     | `GET /api/tools/platform-toolsets`、`PUT /api/tools/platform-toolsets/{platform}`                                                                                                                                                       | 渠道可用工具集策略                      |
| 诊断           | `GET /api/diagnostics`、`GET /api/diagnostics/doctor`、`POST /api/diagnostics/{security-audit\|subprocess-environment/probe}`                                                                                                           | 系统、渠道与安全诊断                    |
| 审批           | `GET /api/diagnostics/approvals`、`GET /api/diagnostics/approvals/{history\|always}`、`POST /api/diagnostics/approvals/{resolve\|always/revoke}`、`GET /api/diagnostics/slash-confirms`、`POST /api/diagnostics/slash-confirms/resolve` | 待审批、历史、永久授权和 Slash 确认     |
| 审批事件       | `GET /api/approval/events`、`GET /api/approval/stats`                                                                                                                                                                                   | 审批事件流与统计                        |
| Cron           | `GET/POST /api/cron/jobs`、`GET /api/cron/jobs/{guide\|policy\|next\|status}`、`GET/PUT/DELETE /api/cron/jobs/{id}`、`GET /api/cron/jobs/{id}/{inspect\|runs}`、`POST /api/cron/jobs/{id}/{pause\|resume\|trigger\|retry}`              | 定时任务全生命周期                      |
| Skills         | `GET /api/skills`、`GET /api/skills/{hub/search\|view\|files}`、`PUT /api/skills/toggle`、`GET /api/tools/toolsets`                                                                                                                     | Skills 与工具集读取和开关               |
| 工作区         | `GET /api/workspace/files`、`GET/PUT /api/workspace/files/{key}`、`POST /api/workspace/files/{key}/restore`                                                                                                                             | 受控工作区文件读写与恢复                |
| 记忆           | `GET /api/workspace/diaries`、`GET /api/workspace/diaries/read`、`GET /api/workspace/memory/archive`、`POST /api/workspace/memory/archive/{run\|restore}`                                                                               | 日记与记忆归档                          |
| 媒体           | `GET /api/media`、`POST /api/media/index`、`GET /api/media/{mediaId}`、`POST /api/media/{mediaId}/{refresh\|download\|reference}`                                                                                                       | 媒体索引、刷新、下载与引用              |
| Curator        | `GET /api/curator`、`GET /api/curator/{status\|improvements}`、`GET /api/curator/{reportId}`、`POST /api/curator/{run\|pause\|resume\|apply\|ignore}`                                                                                   | 自我改进报告和应用控制                  |
| 可观测性       | `GET /api/logs`、`GET /api/analytics/usage`、`GET /api/insights/overview`、`GET /api/insights/skills`                                                                                                                                   | 日志、用量与洞察                        |
| TUI HTTP RPC   | `POST /api/tui/rpc`                                                                                                                                                                                                                     | 轻量 JSON-RPC 2.0 配置与渠道操作        |
| 下载           | `GET /api/solonclaw/download`                                                                                                                                                                                                           | 下载受控的当前运行制品                  |

## TUI WebSocket

`GET /api/tui/handshake` 要求有效 Dashboard Bearer 或短会话，认证成功后返回协议版本和包含短时一次性 `ticket` 的 `/ws/tui` 地址。服务不把 loopback peer、代理头或 Host 当作认证凭据；WebSocket 建连时消费该票据，票据不能复用。业务消息采用项目的 JSON-RPC/Event 信封，不应直接复用 Dashboard Bearer 作为 WebSocket 查询参数。

连接明确 loopback 后端时，官方 TUI 会从 `SOLONCLAW_WORKSPACE/config.yml`、原生安装目录或 Docker 工作区读取现有 `solonclaw.dashboard.accessToken`，只用于握手请求。连接远程后端时不会读取本机配置，必须使用 HTTPS 并显式设置 `SOLONCLAW_DASHBOARD_ACCESS_TOKEN`；明文 HTTP 服务应先通过 SSH 隧道映射到 loopback。

## 通用响应与错误

多数 Dashboard JSON 接口使用以下成功形态：

```json
{
  "success": true,
  "data": {}
}
```

受控错误使用稳定错误码：

```json
{
  "success": false,
  "code": "WORKSPACE_CONFIG_BAD_REQUEST",
  "error": "安全的客户端消息"
}
```

- 未认证请求通常返回 HTTP 401 和 `DASHBOARD_UNAUTHORIZED`。
- 未允许的浏览器 Origin 返回 HTTP 403。
- 参数或状态错误使用 400、404 或 409。
- 限流使用 429；依赖不可用可能使用 503。
- 服务端错误只返回固定公共消息，异常类型和细节只写服务端日志。
- 配置密钥 set/reveal 响应带 `X-Request-Id`，可与追加式审计事件对账。
- SSE、文件下载、`GatewayReply` 和 JSON-RPC 接口使用各自协议，不套用 Dashboard 通用 JSON。

错误分类、日志脱敏和重试语义见 [错误处理](error-handling.md)。
