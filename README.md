# jimuqu-agent

基于 `Solon`、`Solon AI`、`Hutool`、`Snack4` 的单实例 Agent 服务，目标是以 Java 方式复刻 `hermes-agent` 的核心能力，并优先支持国内消息渠道。

当前已经打通的真实链路：

- `gateway -> command -> engine -> openai-responses -> reply`
- 钉钉 `stream mode` 入站
- 钉钉群聊机器人回消息
- 钉钉私聊机器人回消息

## 技术基线

- JDK 8
- Maven 单模块工程
- 包名：`com.jimuqu.agent`
- JSON：`org.noear:snack4`
- 模型协议：`openai`、`openai-responses`、`ollama`、`gemini`、`anthropic`

## 目录说明

- `src/main/java/com/jimuqu/agent/bootstrap`
- `src/main/java/com/jimuqu/agent/config`
- `src/main/java/com/jimuqu/agent/core`
- `src/main/java/com/jimuqu/agent/llm`
- `src/main/java/com/jimuqu/agent/engine`
- `src/main/java/com/jimuqu/agent/tool`
- `src/main/java/com/jimuqu/agent/context`
- `src/main/java/com/jimuqu/agent/storage`
- `src/main/java/com/jimuqu/agent/gateway`
- `src/main/java/com/jimuqu/agent/scheduler`
- `codex-skills/`

运行时目录：

- `runtime/state.db`
- `runtime/context/`
- `runtime/skills/`
- `runtime/cache/`

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
java -jar target/jimuqu-agent-0.1.0-SNAPSHOT.jar --server.port=8080
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

## 已验证测试

离线测试：

- [GatewayCommandFlowTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/GatewayCommandFlowTest.java)
- [StorageRepositoryTest](D:/projects/jimuqu-agent/src/test/java/com/jimuqu/agent/StorageRepositoryTest.java)

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
- SQLite 持久化
- 本地 skills
- cron 基础能力
- 钉钉真实渠道打通

未做或未完全做：

- 飞书真实端到端
- 企微真实端到端
- 微信真实端到端
- Web 搜索/提取
- 浏览器自动化
- 多模态、图像、TTS/转写
- 插件系统、Profiles、OpenAI 兼容 API Server
