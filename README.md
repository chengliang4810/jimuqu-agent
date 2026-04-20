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
- `send_message` 支持本地附件路径 `mediaPaths`
- Agent 主链支持附件感知：入站附件会注入统一附件清单与本地缓存路径

当前已补齐的 Hermes 核心 Agent 能力：

- 自动上下文压缩与 `/compress`
- 结构化文件快照与 `/rollback`
- `MEMORY.md` / `USER.md` 双存储长期记忆
- Hermes 风格技能目录与渐进披露技能索引
- `skills_list` / `skill_view` / `skill_manage`
- 隔离子会话委托与批量委托
- 主回复后的异步技能/记忆学习闭环

当前默认内置工具包含：

- `terminal`
- `process`
- `read_file`
- `write_file`
- `patch`
- `search_files`
- `execute_code`
- `delegate_task`
- `todo`
- `memory`
- `session_search`
- `skills_list`
- `skill_view`
- `skill_manage`
- `send_message`
- `cronjob`
- `approval`
- `codesearch`
- `websearch`
- `webfetch`

当前默认接入的官方 Solon AI skills：

- `solon-ai-skill-web`
  提供 `codesearch`、`websearch`、`webfetch`
- `solon-ai-skill-pdf`
  提供 `pdf_create`、`pdf_parse`

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
- `jimuqu.compression.enabled`
- `jimuqu.compression.thresholdPercent`
- `jimuqu.compression.summaryModel`
- `jimuqu.learning.enabled`
- `jimuqu.learning.toolCallThreshold`
- `jimuqu.rollback.enabled`
- `jimuqu.rollback.maxCheckpointsPerSource`

官方 skill 接入说明：

- Web 搜索与抓取能力改为直接使用官方 `solon-ai-skill-web`
- PDF 生成与解析能力改为直接使用官方 `solon-ai-skill-pdf`
- PDF 默认输出目录为 `runtime/cache/pdf/`
- 若部署环境缺少中文字体，可选设置 `JIMUQU_PDF_FONT_PATH` 指向可用的 `ttf/otf` 字体文件

附件与媒体说明：

- `GatewayMessage` / `DeliveryRequest` 已支持统一附件模型：`kind`、`localPath`、`originalName`、`mimeType`、`fromQuote`、`transcribedText`
- Agent 当前采用“附件感知”而非多模态输入：附件路径与元信息会注入会话文本，供搜索、重试、压缩、记忆和工具链复用
- 当前不引入独立图像理解模型或独立语音转写服务

## 钉钉配置

钉钉当前只保留：

- 入站：`stream mode`
- 出站：官方机器人 OpenAPI

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
- 当前已补入站附件下载码解析与附件感知；出站附件暂按文本降级提示展示

## 飞书 / 企微 / 微信 / 钉钉附件能力

- 飞书：已补 webhook 入站文本链路，文本 + 本地附件投递，图片走原生图片消息，其他附件走文件类消息
- 企微 Bot：已补 WebSocket 文本主链上的附件上传/发送，支持图片、文件、视频与 AMR 语音；非 AMR 音频自动降级为文件
- 微信：已补 iLink 长轮询入站、上下文 token / sync 游标状态持久化，以及加密 CDN 附件发送；支持图片、文件、视频；语音默认按文件附件发送
- 钉钉：已补 stream mode 入站附件下载码解析，并在存在最近 `session_webhook` 时支持原生图片 / 文件 / 语音发送；视频当前仍按文件处理

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
  - `jimuqu.channels.<platform>.allowAllUsers`
  - `jimuqu.channels.<platform>.unauthorizedDmBehavior`
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
- 飞书 webhook 入站文本链路
- 微信长轮询入站链路与附件感知
- 钉钉基于 `session_webhook` 的原生图片 / 文件 / 语音发送

未做或未完全做：

- 飞书真实端到端
- 企微真实端到端
- 微信真实端到端
- 飞书入站附件资源下载的更完整兼容
- 钉钉视频类原生附件发送
- 浏览器自动化
- 多模态、图像、TTS/转写
- 插件系统、Profiles、OpenAI 兼容 API Server
