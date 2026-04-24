# jimuqu-agent

基于 `Solon`、`Solon AI`、`Hutool`、`Snack4` 的单实例 Agent 服务，目标是以 Java 方式复刻 `hermes-agent` 的核心能力，并优先支持国内消息渠道。

当前已经打通的真实链路：

- `gateway -> command -> engine -> openai-responses -> reply`
- 钉钉 `stream mode` 入站
- 钉钉群聊机器人回消息
- 钉钉私聊机器人回消息

当前统一消息渠道体验已补齐的能力：

- 首个私聊用户认领平台唯一管理员
- `/pairing claim-admin`
- `/pairing pending`
- `/pairing approve <platform> <code>`
- `/pairing revoke <platform> <userId>`
- `/pairing approved [platform]`
- `/sethome`
- `/platforms` 展示管理员、home channel、pairing 状态
- `/version` 查看当前版本、部署方式与缓存的最新发布信息
- `/version check` 检查 GitHub 最新版本
- `/version update` 在 `java -jar` 部署下执行在线升级；Docker 部署下返回宿主机升级指引
- `/usage` 查看当前会话最近一轮与累计 token 用量
- `/reasoning [show|hide]` 控制当前来源键是否把 reasoning 作为中间态发回聊天窗口
- `send_message` 支持本地附件路径 `mediaPaths`
- `send_message` 支持可选 `channelExtrasJson`，用于钉钉 AI card 等渠道扩展参数
- Agent 主链支持附件感知：入站附件会注入统一附件清单与本地缓存路径
- Dashboard-first 渠道接入与 doctor：`/api/status`、`/api/gateway/doctor`
- Dashboard 会话页、状态页、分析页展示 session token usage
- 微信 iLink QR 登录：`POST /api/gateway/setup/weixin/qr` + `GET /api/gateway/setup/weixin/qr/{ticket}`
- 渠道中间态显示：飞书默认 `new` 级别工具进度，钉钉可选 AI card 长任务进度，企微/微信默认静默

当前已补齐的 Hermes 核心 Agent 能力：

- 自动上下文压缩、ReAct 工作记忆摘要守卫与 `/compress`
- 结构化文件快照与 `/rollback`
- `MEMORY.md` / `USER.md` 双存储长期记忆
- Hermes 风格技能目录与渐进披露技能索引
- `skills_list` / `skill_view` / `skill_manage`
- 隔离子会话委托与批量委托
- 主回复后的异步技能/记忆学习闭环

当前默认内置工具包含：

- `read_file`
- `write_file`
- `patch`
- `search_files`
- `exists_cmd`
- `list_files`
- `execute_shell`
- `execute_python`
- `execute_js`
- `get_current_time`
- `delegate_task`
- `todo`
- `memory`
- `session_search`
- `skills_list`
- `skill_view`
- `skill_manage`
- `send_message`
- `cronjob`
- `codesearch`
- `websearch`
- `webfetch`

当前默认接入的官方 Solon AI skills：

- `solon-ai-skill-web`
  提供 `codesearch`、`websearch`、`webfetch`
- `solon-ai-skill-pdf`
  提供 `pdf_create`、`pdf_parse`
- `solon-ai-skill-sys`
  提供 `exists_cmd`、`list_files`、`execute_shell`、`execute_python`、`execute_js`、`get_current_time`

## 技术基线

- JDK 8
- Maven 单模块工程
- 启动类：`com.jimuqu.agent.JimuquAgentApp`
- 包名：`com.jimuqu.agent`
- JSON：`org.noear:snack4`
- 官方 skills：`org.noear:solon-ai-skill-web`、`org.noear:solon-ai-skill-pdf`
- 模型协议：`openai`、`openai-responses`、`ollama`、`gemini`、`anthropic`
- 数据对象与配置对象已引入 Lombok，优先使用 `@Getter`、`@Setter`、`@NoArgsConstructor`

## 包结构

- `src/main/java/com/jimuqu/agent`
  启动入口
- `src/main/java/com/jimuqu/agent/bootstrap`
  Solon Bean 装配、健康检查、HTTP 网关入口
- `src/main/java/com/jimuqu/agent/config`
  配置加载、环境变量覆盖、路径标准化
- `src/main/java/com/jimuqu/agent/core/enums`
  枚举定义
- `src/main/java/com/jimuqu/agent/core/model`
  领域模型与传输对象
- `src/main/java/com/jimuqu/agent/core/repository`
  仓储接口
- `src/main/java/com/jimuqu/agent/core/service`
  核心服务接口
- `src/main/java/com/jimuqu/agent/context`
  `AGENTS.md`、`MEMORY.md`、`USER.md` 与本地 skills 处理
- `src/main/java/com/jimuqu/agent/engine`
  Agent 主循环
- `src/main/java/com/jimuqu/agent/gateway/authorization`
  管理员认领、pairing、授权判断
- `src/main/java/com/jimuqu/agent/gateway/command`
  slash 命令处理
- `src/main/java/com/jimuqu/agent/gateway/delivery`
  渠道投递与 home channel fallback
- `src/main/java/com/jimuqu/agent/gateway/platform`
  各渠道适配器实现
- `src/main/java/com/jimuqu/agent/gateway/service`
  网关主入口服务
- `src/main/java/com/jimuqu/agent/llm`
  Solon AI 模型网关
- `src/main/java/com/jimuqu/agent/scheduler`
  cron 调度执行
- `src/main/java/com/jimuqu/agent/storage/repository`
  SQLite 持久化实现
- `src/main/java/com/jimuqu/agent/support`
  通用辅助类
- `src/main/java/com/jimuqu/agent/support/constants`
  常量定义
- `src/main/java/com/jimuqu/agent/tool/runtime`
  工具注册与运行时工具实现
- `codex-skills/`
  项目专用 Codex skills

运行时目录：

- `runtime/state.db`
- `runtime/context/`
- `runtime/skills/`
- `runtime/cache/`
- `runtime/cache/media/<platform>/`

其中 PDF 技能默认使用：

- `runtime/cache/pdf/`

## 构建与运行

编译和测试：

```powershell
mvn test
```

打包：

```powershell
mvn clean package
```

启动：

```powershell
java -jar target/jimuqu-agent-0.0.1.jar --server.port=8080
```

健康检查：

```powershell
Invoke-WebRequest http://127.0.0.1:8080/health
```

## Docker 部署

All runtime configuration is now stored in `runtime/config.yml`. Create it before first start, or fill it in from the dashboard after startup. For Weixin, `accountId/token` may be left empty and written by dashboard QR login later.

当前推荐直接使用 GitHub Packages 镜像：

- `ghcr.io/chengliang4810/jimuqu-agent:latest`

单容器部署示例：

```bash
docker run -d \
  --name jimuqu-agent \
  --restart unless-stopped \
  -p 8080:8080 \
  -v ./runtime:/app/runtime \
  ghcr.io/chengliang4810/jimuqu-agent:latest
```

说明：

- 容器内默认运行目录为 `/app`
- 运行时数据目录挂载到 `/app/runtime`
- 应用默认监听 `8080`
- 若你使用 dashboard、SQLite、skills、媒体缓存或微信扫码登录，`./runtime` 必须持久化
- 运行镜像已补成更接近真机的完整环境，内置 `apt-get`、`bash`、`git`、`curl`、`wget`、`jq`
- 同时内置 `python3/pip`、`nodejs/npm`，可直接支撑官方 `solon-ai-skill-sys`
- 镜像内已安装 `fonts-arphic-gbsn00lp` 和 `fonts-noto-cjk`
- The image includes `fonts-arphic-gbsn00lp`; PDF font autodetection works by default. Override with `jimuqu.pdf.fontPath` in `runtime/config.yml` if needed.

健康检查：

```bash
curl http://127.0.0.1:8080/health
```

## Docker Compose 部署

仓库已包含可直接使用的 [docker-compose.yml](D:/projects/jimuqu-agent/docker-compose.yml)。

内容如下：

```yaml
services:
  jimuqu-agent:
    image: ghcr.io/chengliang4810/jimuqu-agent:latest
    container_name: jimuqu-agent
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - ./runtime:/app/runtime
```

启动：

```bash
docker compose up -d
```

查看日志：

```bash
docker compose logs -f jimuqu-agent
```

说明：

- 控制台日志会直接输出到 `docker logs`
- 滚动文件日志会写到 `runtime/logs/agent.log`、`runtime/logs/gateway.log`、`runtime/logs/errors.log`

停止：

```bash
docker compose down
```

## GitHub Releases 与 Packages

仓库已补 GitHub Actions 自动发布链路：

- [packages.yml](D:/projects/jimuqu-agent/.github/workflows/packages.yml)
  - `push main` 时自动构建并推送 `ghcr.io/chengliang4810/jimuqu-agent:latest`
  - `push v* tag` 时自动推送对应 tag 镜像
- [release.yml](D:/projects/jimuqu-agent/.github/workflows/release.yml)
  - `push v* tag` 时自动构建 jar、生成 GitHub Release、上传 jar 与 `sha256`

推荐发布流程：

```bash
git tag v0.0.1
git push origin v0.0.1
```

发布后可直接使用 Packages 镜像地址部署：

- `ghcr.io/chengliang4810/jimuqu-agent:latest`
- `ghcr.io/chengliang4810/jimuqu-agent:v0.0.1`

## 大模型配置

默认模型走 `openai-responses`，默认配置在 [app.yml](D:/projects/jimuqu-agent/src/main/resources/app.yml)。

建议通过环境变量注入密钥：

- `JIMUQU_LLM_API_KEY`

当前已验证可用的实时模型配置：

- `jimuqu.llm.provider=openai-responses`
- `jimuqu.llm.apiUrl=https://subapi.jimuqu.com/v1/responses`
- `jimuqu.llm.model=gpt-5.4`

新增的核心 Agent 配置：

- `jimuqu.llm.contextWindowTokens`
- `jimuqu.react.maxSteps`
- `jimuqu.react.retryMax`
- `jimuqu.react.retryDelayMs`
- `jimuqu.react.delegateMaxSteps`
- `jimuqu.react.delegateRetryMax`
- `jimuqu.react.delegateRetryDelayMs`
- `jimuqu.react.summarizationEnabled`
- `jimuqu.react.summarizationMaxMessages`
- `jimuqu.react.summarizationMaxTokens`
- `jimuqu.compression.enabled`
- `jimuqu.compression.thresholdPercent`
- `jimuqu.compression.summaryModel`
- `jimuqu.learning.enabled`
- `jimuqu.learning.toolCallThreshold`
- `jimuqu.rollback.enabled`
- `jimuqu.rollback.maxCheckpointsPerSource`

当前默认 ReAct 策略：

- 主代理：`maxSteps=12`、`retryMax=3`、`retryDelayMs=2000`
- 子代理：`delegateMaxSteps=18`、`delegateRetryMax=4`、`delegateRetryDelayMs=2500`
- 工作记忆摘要守卫：默认启用，`summarizationMaxMessages=40`、`summarizationMaxTokens=32000`
- 工作记忆摘要策略：直接使用 Solon 官方 `SummarizationInterceptor + KeyInfoExtractionStrategy + HierarchicalSummarizationStrategy`
- `compression.summaryModel` 若配置，则同时用于持久化压缩和 ReAct 工作记忆摘要
- `retryConfig` 作用于模型决策重试，不是工具重试

聊天窗口显示相关新增配置：

- `jimuqu.display.toolProgress`
- `jimuqu.display.showReasoning`
- `jimuqu.display.toolPreviewLength`
- `jimuqu.display.progressThrottleMs`
- `jimuqu.channels.feishu.toolProgress`
- `jimuqu.channels.dingtalk.toolProgress`
- `jimuqu.channels.dingtalk.progressCardTemplateId`
- `jimuqu.channels.wecom.toolProgress`
- `jimuqu.channels.weixin.toolProgress`

钉钉长任务进度卡说明：

- 需要先配置 `jimuqu.channels.dingtalk.progressCardTemplateId`
- 当前按固定模板参数写入：`title`、`status`、`summary`、`detail`、`updatedAt`
- 若未配置模板 ID，钉钉中间态会退回普通文本提示
- `maxSteps` 统计的是 ReAct 推理轮次（Reason 步），不是单纯工具调用次数

会话 token usage 说明：

- 基于 Solon AI `ReActResponse.getMetrics()` 统计单轮输入 / 输出 / 总 token
- 每次用户消息完成后会把本轮 usage 累加到 SQLite `sessions` 记录
- `/usage`、dashboard session API、analytics API 都基于这份持久化数据
- 当前累计维度可靠的是 `input/output/total`；`reasoning/cache_read` 先保留字段，尚未做跨 ReAct 全链路精确累计

官方 skill 接入说明：

- Web 搜索与抓取能力改为直接使用官方 `solon-ai-skill-web`
- PDF 生成与解析能力改为直接使用官方 `solon-ai-skill-pdf`
- PDF 默认输出目录为 `runtime/cache/pdf/`
- Docker 镜像内已默认配置中文 TrueType 字体，无需额外设置即可处理中文 PDF
- For non-Docker or custom environments, set `jimuqu.pdf.fontPath` in `runtime/config.yml` to a usable `ttf/otf` font file.

Version check and online update settings in `runtime/config.yml`:

- `jimuqu.update.repo`
  覆盖 GitHub 仓库，格式 `owner/repo`
- `jimuqu.update.releaseApiUrl`
  直接覆盖“最新版本”检查 API 地址
- `jimuqu.update.httpProxy`
  为版本检查请求设置 HTTP 代理，例如 `http://proxy.example:7890`
- `jimuqu.integrations.github.token` / `jimuqu.integrations.github.cliToken`
  可选，用于提高 GitHub API 速率限制

附件与媒体说明：

- `GatewayMessage` / `DeliveryRequest` 已支持统一附件模型：`kind`、`localPath`、`originalName`、`mimeType`、`fromQuote`、`transcribedText`
- Agent 当前采用“附件感知”而非多模态输入：附件路径与元信息会注入会话文本，供搜索、重试、压缩、记忆和工具链复用
- 当前不引入独立图像理解模型或独立语音转写服务

## 钉钉配置

钉钉当前只保留：

- 入站：`stream mode`
- 出站：官方机器人 OpenAPI + 官方会话文件发送

必须配置：

- `JIMUQU_DINGTALK_ENABLED=true`
- `JIMUQU_DINGTALK_CLIENT_ID`
- `JIMUQU_DINGTALK_CLIENT_SECRET`
- `JIMUQU_DINGTALK_ROBOT_CODE`

可选配置：

- `jimuqu.channels.dingtalk.coolAppCode`

说明：

- 群聊发送走 `OrgGroupSend + sampleMarkdown`
- 私聊发送走 `BatchSendOTO + sampleMarkdown`
- 私聊发送目标必须使用钉钉 `senderStaffId`
- 管理员由该平台首个私聊认领成功的用户自动固化，且只能有一个
- 管理员一旦建立，不能通过对话命令修改
- 当前已补入站附件下载码解析、会话上下文持久化与官方文件空间发送；图片 / 文件 / 语音走原生会话附件发送，视频仍按文件附件发送

## Dashboard 接入与 Doctor

- 当前 dashboard 负责国内渠道的 setup / doctor，不再补完整 CLI wizard
- 状态页会展示每个渠道的：
  - `setupState`
  - `connectionMode`
  - `missingEnv`
  - `features`
  - `lastErrorCode`
  - `lastErrorMessage`
- `/api/status` 额外返回：
  - `version`
  - `version_tag`
  - `deployment_mode`
  - `latest_version`
  - `latest_tag`
  - `update_available`
  - `release_url`
  - `release_api_url`
  - `update_error_message`
- Weixin dashboard iLink QR login writes to `runtime/config.yml`:
  - `jimuqu.channels.weixin.accountId`, `jimuqu.channels.weixin.token`
  - `jimuqu.channels.weixin.baseUrl` only when a non-default `baseUrl` is returned

## 飞书 / 企微 / 微信 / 钉钉渠道能力

- 飞书：仅保留官方 Java SDK websocket 入站；补齐文本 / 图片 / 音频 / 视频 / 文件 / post 富文本附件提取，群聊按 @mention 门控，并接入 card action / reaction 事件
- 飞书：支持基于官方 `application/v6` + `bot/v3/info` 的 bot identity 自动发现，用于更准确的群聊 @mention 判定；若权限不足则保持 best-effort 降级
- 企微 Bot：WebSocket 文本主链补齐 reply-mode `req_id`、per-group sender allowlist、quoted/mixed message 附件、图片 / 文件 / 视频 / AMR 语音上传发送；非 AMR 音频自动降级为文件
- 微信：保留 Hermes 原生 iLink 长轮询；补齐 `context_token` / `sync_buf` SQLite 持久化、quoted media、typing、文本分片 / 重试、dashboard QR 登录、加密 CDN 附件发送
- 钉钉：保留 Stream Mode 入站；出站文本仍走官方机器人消息，附件改走官方会话文件上传 / 发送主链，不再硬依赖最近一次 `session_webhook`；视频继续按文件附件发送
- 钉钉：新增官方 `SendRobotInteractiveCard` AI card 发送与 `CARD_CALLBACK_TOPIC` 回调接入；当前通过 `DeliveryRequest.channelExtras` / `send_message.channelExtrasJson` 显式触发
- 钉钉：emoji reaction 当前为 best-effort 入站解析，仅在 stream 事件里出现 `reaction` / `emoji` 类消息时转成统一文本事件；未宣称已验证原生官方 reaction API 出站

## 统一消息渠道授权与 home channel

当前所有消息渠道统一遵循以下规则：

- 每个平台只能有一个管理员
- 管理员由该平台首个私聊认领成功的用户自动成为
- 没有管理员时：
  - 群聊消息静默忽略
  - 私聊消息返回管理员认领提示
- 已有管理员后：
  - 未授权私聊用户收到 pairing code
  - 管理员在私聊中执行 `/pairing approve <platform> <code>`
- `/sethome` 只能由平台管理员执行，且必须在目标聊天里执行
- 当系统投递目标没有显式 `chatId` 时，会回退到该平台 home channel

授权相关配置：

- 平台级：
  - `jimuqu.channels.<platform>.allowedUsers`
  - `jimuqu.channels.<platform>.dmPolicy`
  - `jimuqu.channels.<platform>.groupPolicy`
  - `jimuqu.channels.<platform>.groupAllowedUsers`
  - `jimuqu.channels.<platform>.allowAllUsers`
  - `jimuqu.channels.<platform>.unauthorizedDmBehavior`
- 渠道扩展：
  - `jimuqu.channels.feishu.botOpenId`
  - `jimuqu.channels.feishu.botUserId`
  - `jimuqu.channels.feishu.botName`
  - `jimuqu.channels.wecom.groups.<groupId>.allowFrom`
  - `jimuqu.channels.weixin.splitMultilineMessages`
  - `jimuqu.channels.weixin.sendChunkDelaySeconds`
  - `jimuqu.channels.weixin.sendChunkRetries`
  - `jimuqu.channels.weixin.sendChunkRetryDelaySeconds`
- `send_message` 渠道扩展示例：
  - 钉钉 AI card：`channelExtrasJson={"mode":"ai_card","cardTemplateId":"tpl_xxx","cardData":{"title":"demo"}}`
- 全局：
  - `jimuqu.gateway.allowedUsers`
  - `jimuqu.gateway.allowAllUsers`

## 已验证测试

离线测试：

- [GatewayCommandFlowTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/GatewayCommandFlowTest.java)
- [StorageRepositoryTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/StorageRepositoryTest.java)
- [PlatformAdminBootstrapTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/PlatformAdminBootstrapTest.java)
- [GatewayAuthorizationFlowTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/GatewayAuthorizationFlowTest.java)
- [HomeChannelCommandTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/HomeChannelCommandTest.java)
- [DeliveryHomeChannelFallbackTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/DeliveryHomeChannelFallbackTest.java)
- [ToolRegistryExposureTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/ToolRegistryExposureTest.java)
- [AppConfigPathNormalizationTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/AppConfigPathNormalizationTest.java)
- [ChannelConfigPolicyLoadTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/ChannelConfigPolicyLoadTest.java)
- [WeixinQrSetupServiceTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/WeixinQrSetupServiceTest.java)
- [FeishuBotIdentityDiscoveryTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/FeishuBotIdentityDiscoveryTest.java)
- [DingTalkAiCardRoutingTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/DingTalkAiCardRoutingTest.java)

实时模型联调：

- [LiveGatewayIntegrationTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/LiveGatewayIntegrationTest.java)

钉钉 live 测试：

- [DingTalkStreamConnectionLiveTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/DingTalkStreamConnectionLiveTest.java)
- [DingTalkPrivateSendLiveTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/DingTalkPrivateSendLiveTest.java)

钉钉 live 测试需要额外环境变量：

- `JIMUQU_DINGTALK_PRIVATE_OPEN_CONVERSATION_ID`
- `JIMUQU_DINGTALK_PRIVATE_USER_ID`

## 当前能力边界

已做：

- slash 命令语义
- 会话、分支、重试、撤销
- 自动上下文压缩
- ReAct 工作记忆摘要守卫
- `/rollback` 文件回滚
- SQLite 持久化
- 本地 skills
- Hermes 风格 skills 渐进披露
- `MEMORY.md` / `USER.md` 长期记忆
- 子会话委托
- cron 基础能力
- 钉钉真实渠道打通
- Solon 内置 `codesearch` / `websearch` / `webfetch` 工具接入
- Solon 内置 `PdfSkill` 接入，支持 `pdf_create` / `pdf_parse`
- 统一附件模型、附件缓存目录与 SQLite 渠道状态仓储
- `send_message` 支持本地附件路径数组
- Agent 会话文本支持附件清单注入
- 企微 / 钉钉入站附件感知与附件缓存
- 飞书 / 企微 / 微信附件发送
- 飞书 websocket 入站文本链路
- 微信长轮询入站链路与附件感知
- dashboard-first 渠道 doctor / 微信 QR onboarding
- 飞书 websocket-only 主链，card action / reaction 事件接入
- 飞书官方 app info / bot info identity 自动发现
- 企微 reply-mode `req_id` 与 per-group allowlist
- 钉钉官方会话文件原生附件发送
- 钉钉 AI card 发送与 card callback 入站

未做或未完全做：

- 飞书真实端到端
- 企微真实端到端
- 微信真实端到端
- 飞书入站附件资源下载的更完整兼容
- 钉钉视频类原生附件发送
- 浏览器自动化
- 多模态、图像、TTS/转写
- 插件系统、Profiles、OpenAI 兼容 API Server
