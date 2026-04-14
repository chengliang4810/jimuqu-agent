# AGENTS

## 项目定位

`jimuqu-claw`（积木区龙虾）是一个基于 `Solon + Solon AI + Hutool + snack4` 的 Java Agent 项目。

目标不是把 `hermes-agent` 的 Python 代码逐文件翻译成 Java，而是：

- 产品能力、交互边界、工具命名优先参考 `D:\projects\hermes-agent`
- Java 实现优先复用 `D:\projects\solon-ai-main`
- 配置、生命周期、调度、路由、插件化优先遵循 `D:\projects\solon-main`
- 文件、时间、线程、HTTP、集合等通用能力优先使用 `D:\projects\hutool-v5-master`

当前仓库仍处于初始化阶段。后续代码、目录和配置应以本文件为第一份协作约束。

## 强制技术栈

- 应用骨架与渠道接入：`org.noear:solon`
- Agent / Skill / Flow / Team：`org.noear:solon-ai*`
- 通用工具：`cn.hutool:*`
- JSON 序列化与反序列化：`org.noear:snack4`，以及 `solon-serialization-snack4`

默认约束：

- 不引入 Jackson / Fastjson / Gson 作为主序列化方案，除非有明确外部兼容要求
- 不把 Python 专属运行时机制硬搬到 Java
- 能用 `Solon-AI` 现成抽象解决的问题，不重复造一套自定义 DSL
- 不为贴近别的 Java 参考项目而改写 Hermes 已稳定的产品命名

## 参考优先级

实现或设计时，优先按这个顺序找依据：

1. `D:\projects\hermes-agent`
   说明产品能力、交互方式、工具体系、消息网关、调度、记忆、扩展点，以及保留能力的名称定义
2. `D:\projects\solon-ai-main`
   说明 Agent、Skill、Flow、Team 等基础抽象的标准用法
3. `D:\projects\solon-main`
   说明配置装配、生命周期、路由、任务调度、插件化用法
4. `D:\projects\hutool-v5-master`
   说明文件、时间、线程、ID、HTTP、集合等工具类的优先选型

规则：

- `hermes-agent` 负责回答“保留什么能力、沿用什么名称”
- `solon-ai`、`solon`、`hutool` 负责回答“在 Java 里怎么实现”
- 不参考 `solonclaw`

## 架构原则

### 1. 先保留产品能力，再替换实现形态

需要对齐的是这些能力类型：

- 统一会话运行时
- 工作区驱动提示词
- 文件工具与受控命令执行
- 子任务拆解与回流
- 会话 / 运行持久化
- 定时任务
- 国内企业渠道接入
- 技能系统
- 主动通知
- 安全与可靠性

不需要对齐的是这些 Python 专属细节：

- `run_agent.py` 风格的手写循环
- `prompt_toolkit` / Rich 风格 CLI TUI
- Python 子脚本 RPC 工具执行
- Python 生态下的 provider / env / extras 组织方式

### 2. 统一运行时优先于零散控制器

所有外部消息、内部任务、定时触发，都应先进入统一运行时，再由运行时驱动：

- 去重
- 会话历史写入
- 运行状态跟踪
- 并发控制
- Agent 执行
- 回复路由与外发

不要把业务逻辑直接分散到控制器、渠道适配器或定时器回调里。

### 3. 工作区优先

默认需要保留工作区驱动模式。后续设计优先考虑这些工作区文件：

- `AGENTS.md`
- `SOUL.md`
- `IDENTITY.md`
- `USER.md`
- `TOOLS.md`
- `HEARTBEAT.md`
- `MEMORY.md`
- `memory/YYYY-MM-DD.md`
- `skills/`
- `jobs.json`
- `runtime/`

### 4. 工具名称优先沿用 Hermes

模型可见工具如果在 `hermes-agent` 中已有稳定名称，则直接沿用原名，不再额外重命名。

明确要求：

- 保留 `patch`，不要改成 `edit_file`
- 保留 `delegate_task`，不要改成 `spawn_task`
- 保留 `cronjob` 单工具形态，使用 `action` 管理任务，不拆成 `list_jobs`、`add_job`、`run_job_now` 之类别名
- Java 内部服务名可以自行设计，但对模型暴露的工具名以 Hermes 原名为准

## 首期范围

### 保留

- Agent 核心运行时
- 模型与 Provider 配置切换
- 会话、记忆、上下文
- 国内渠道消息网关
  仅 `DingTalk`、`Feishu`、`WeCom`
- 文件与代码工具
- 终端与进程工具
- 核心内部工具
  `todo`、`memory`、`delegate_task`
- 自动化与主动外发
- 技能系统
- 安全与可靠性

### 不内建

- `web_search`
- `web_extract`
- 全部 `browser_*`

说明：

- 这类能力后续如有需要，优先走技能扩展，不作为首期内建工具

### 明确不做

- CLI 交互
- 多模态能力
- `clarify`
- `execute_code`
- `mixture_of_agents`
- MCP / ACP / API Server
- Debug Web / 前端网页查看端
- 研究与训练能力

## 初始模块规划

项目初始化后，推荐围绕 `com.jimuqu.claw` 组织：

- `bootstrap` / `config`
  应用入口、配置绑定、Bean 装配
- `agent.runtime`
  统一运行时、会话调度、执行链路、状态模型
- `agent.store`
  会话、运行、去重、路由、日志等持久化
- `agent.workspace`
  工作区路径边界、模板初始化、提示词拼装
- `agent.tool`
  文件工具、运行时工具、任务工具、消息工具
- `agent.memory`
  长短期记忆、摘要、检索
- `agent.job`
  定时任务定义、恢复、执行
- `channel`
  钉钉、飞书、企微等渠道适配
- `integration`
  第三方模型提供方、外部服务接入

## 编码与依赖约束

### 配置

- 统一使用 Solon 配置绑定
- 业务配置前缀建议统一为 `jimuqu.claw` 或一次性确定的等价值
- 不要把大量配置分散写死在常量中

### JSON

- HTTP、配置、持久化对象默认使用 `snack4`
- 如果用了 `solon-serialization-snack4`，序列化行为要保持一致

### 工具类

优先从 Hutool 选：

- `StrUtil`
- `FileUtil`
- `DateUtil`
- `IdUtil`
- `ThreadUtil`
- `CollectionUtil`
- `BeanUtil`
- `HttpUtil`

除非 JDK 原生写法明显更清晰，否则不要重复封装同类工具。

### 持久化

初始化阶段优先采用“工作区可见”的轻量持久化：

- 文本文件
- JSON 文件
- 明确目录结构

是否引入数据库、全文检索、向量库，后续按功能分期决定。

### 安全

- 文件工具必须受工作区边界保护
- 命令执行必须可配置沙盒边界
- 外发消息必须依赖明确的回复路由或渠道配置
- 高风险操作要有审批、确认或显式策略

## 默认实现取向

当前建议优先实现：

- 统一运行时
- 工作区提示词系统
- 会话 / 运行持久化
- 文件工具
  `read_file`、`write_file`、`patch`、`search_files`
- 子任务编排
  `delegate_task`
- 定时任务
  `cronjob`
- 终端与进程工具
  `terminal`、`process`
- 国内渠道
  `DingTalk`、`Feishu`、`WeCom`
- 渠道主动通知
  `send_message`
- 技能系统

当前不建议一开始就投入大量精力的方向：

- 重型终端 TUI
- Debug Web
- Web / 浏览器内建工具
- 多模态
- MCP / ACP / API Server
- 海外消息平台全量接入
- RL / batch runner / benchmark / trajectory compression
- 与核心交付无关的皮肤、人格、花哨展示

## 测试要求

后续新增代码时，至少覆盖这些高风险区域：

- 工作区路径边界
- 会话并发控制
- 子任务回流
- 定时任务恢复
- 渠道路由
- 序列化兼容性
- 提示词拼装
- 工具名称与工具参数兼容性

优先写单元测试和小型集成测试，不要把核心行为只留给手工联调验证。
