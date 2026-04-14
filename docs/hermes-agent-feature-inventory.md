# Hermes Agent 功能盘点

## 说明

本清单基于以下本地参考整理：

- `D:\projects\hermes-agent\README.md`
- `D:\projects\hermes-agent\AGENTS.md`
- `D:\projects\hermes-agent\toolsets.py`
- `D:\projects\hermes-agent\hermes_cli\commands.py`
- `D:\projects\hermes-agent\cli-config.yaml.example`
- `D:\projects\hermes-agent\RELEASE_v0.2.0.md` ~ `RELEASE_v0.8.0.md`

目标是区分：

- 用户可见的产品功能
- 支撑这些功能的工程机制

## 1. 统一 Agent 运行时

- 多轮对话主循环
- 工具调用与结果回注
- 最大迭代次数控制
- 会话消息历史管理
- reasoning / scratchpad 处理
- 工具并发与顺序执行控制
- 错误分类、重试、故障切换
- 轨迹保存与会话日志

## 2. 模型与 Provider 能力

- 多 Provider 支持
  OpenRouter、Nous、Anthropic、OpenAI、Codex、Copilot、Gemini、GLM、Kimi、MiniMax、HuggingFace、自定义 OpenAI 兼容端点
- 模型切换命令
- 模型别名与目录
- Provider 路由
- 智能模型路由
- 流式输出
- reasoning 等级控制
- 价格与 token 用量统计
- 上下文长度探测与缓存
- Prompt caching
- 辅助模型
  vision、web_extract、approval、compression 等侧任务模型

## 3. 会话、记忆与上下文

- 持久会话存储
- 会话标题
- 新建、重试、撤销、分支、恢复
- Context compression
- 工作区上下文文件注入
- 持久记忆
  MEMORY.md、USER.md
- 记忆 nudge 与 flush
- 历史会话搜索
  FTS5 + 摘要召回
- 用户画像 / Honcho 集成
- profile / 多实例 home 目录

## 4. CLI 与交互体验

- 交互式 CLI
- Slash 命令系统
- 命令补全、路径补全、上下文引用补全
- 多行输入
- 状态栏
- 皮肤 / 主题
- 模型切换
- 工具进度展示
- 后台任务
- 队列输入 / 中断重定向
- 粘贴图片 / 附加本地图片
- 语音模式
- 更新、诊断、配置查看
- setup 向导

当前命令面大致包括：

- 会话类：`/new`、`/retry`、`/undo`、`/branch`、`/compress`、`/background`、`/queue`、`/resume`
- 配置类：`/model`、`/provider`、`/personality`、`/reasoning`、`/voice`
- 工具类：`/tools`、`/toolsets`、`/skills`、`/cron`、`/reload-mcp`、`/browser`
- 信息类：`/help`、`/usage`、`/insights`、`/platforms`

## 5. 消息网关与多渠道接入

- 统一 Gateway 运行时
- 渠道适配器注册
- 统一 SessionStore 与 DeliveryRouter
- 危险命令审批回流到消息渠道
- home channel / pairing / route 维护

已支持或显式实现的渠道包括：

- Telegram
- Discord
- Slack
- WhatsApp
- Signal
- Email
- Home Assistant
- Matrix
- Mattermost
- DingTalk
- Feishu/Lark
- WeCom
- Weixin（个人微信，平台名 `weixin`）
- SMS
- Webhook
- BlueBubbles

## 6. 工具体系

### 6.1 文件与代码

- `read_file`
- `write_file`
- `patch`
- `search_files`
- 回滚 / checkpoint

### 6.2 终端与进程

- `terminal`
- `process`
- 危险命令审批
- 后台进程管理
- 多终端后端
  local、docker、ssh、singularity、modal、daytona

### 6.3 Web 与浏览器

- `web_search`
- `web_extract`
- 浏览器导航、快照、点击、输入、滚动、返回、按键、抓图、vision、console

### 6.4 多模态

- `vision_analyze`
- `image_generate`
- STT 转写
- TTS 朗读
- voice 模式

### 6.5 Agent 内部工具

- `todo`
- `memory`
- `session_search`
- `clarify`
- `execute_code`
- `delegate_task`
- `mixture_of_agents`

### 6.6 自动化与外发

- `cronjob`
- `send_message`
- Home Assistant 相关工具

### 6.7 扩展协议

- MCP 客户端工具发现与调用
- MCP sampling
- ACP 编辑器代理接入

## 7. 技能系统与插件

- 本地技能目录
- 技能浏览、查看、启用、管理
- 技能注入到当前会话
- Skills Hub 搜索、安装、管理
- 插件系统
- 平台级工具集 / toolset 配置
- 技能按平台启用或禁用

## 8. 自动化与计划任务

- cron job 创建、更新、暂停、恢复、立即执行、删除
- 输出保存
- 一次性 / 间隔 / cron 表达式调度
- 到期 catch-up 与 grace 处理
- 平台回投递
- 与技能结合执行

## 9. IDE 与 API 暴露

- ACP 适配器
- VS Code / Zed / JetBrains 集成
- OpenAI 兼容 API Server
- CLI、Gateway、ACP、API Server 使用不同 toolset 组合

## 10. 安全与可靠性

- 命令审批
- yolo 开关
- tirith 安全扫描
- URL 安全检查
- PII 脱敏
- 原子写文件
- 崩溃恢复
- 断管保护
- 重试与退避
- Windows / Termux / Nix / WSL 等兼容

## 11. 研究、评测与数据生成

- batch_runner
- trajectory_compressor
- RL 训练工具
- benchmark environments
- tinker-atropos 集成
- 数据集生成配置

## 12. 配套运维能力

- 安装脚本
- 更新命令
- doctor
- 配置迁移
- OpenClaw 迁移
- 发布说明与版本演进文档

## 总结

如果把 `hermes-agent` 拆开看，它实际上是四层能力叠加：

1. Agent 核心运行时
2. 多界面接入层
3. 工具 / 技能 / 自动化扩展层
4. 运维、研究与生态层

后续 `jimuqu-claw` 做范围裁剪时，建议只把第 1 层和第 2 层中的核心部分作为首期必做，把第 3 层按业务需要逐步引入，把第 4 层大部分延后。
