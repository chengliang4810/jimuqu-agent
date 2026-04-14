# jimuqu-claw 功能取舍结论

## 最新参考基线

当前本地 `D:\projects\hermes-agent` 已与远端 `origin/main` 同步：

- 本地 `HEAD` = `af9caec44fdab7a1b883dede16fe1ce8c2d60fb9`
- 远端 `origin/main` = `af9caec44fdab7a1b883dede16fe1ce8c2d60fb9`

因此首期范围文档以该版本的 Hermes 主线事实为准，不再按旧的 `0e315a6f...` 基线推导。

## 设计前提

`jimuqu-claw` 参考的是 `D:\projects\hermes-agent` 的产品能力边界，而不是别的 Java 项目现成结构。

实现层约束保持为：

- `Solon` 负责应用骨架、配置、生命周期、调度、渠道入口
- `Solon-AI` 负责 Agent、Skill、Flow、Team 等抽象
- `Hutool` 负责文件、线程、时间、HTTP、集合等通用工具
- `snack4` 负责统一 JSON 序列化

额外原则：

- 不参考 `solonclaw`
- 不为了贴近其他项目而改 Hermes 的产品命名
- 保留能力按 Hermes 原名定义，Java 侧只调整实现方式

## 最终功能保留/删除表

| 类别 | 最终决定 | 备注 |
|---|---|---|
| 1. Agent 核心运行时 | 保留 | 作为主干能力 |
| 2. 模型与 Provider | 保留 | 保留多模型与配置切换 |
| 3. 会话、记忆、上下文 | 保留 | 保留工作区驱动提示词与记忆 |
| 4. CLI 交互 | 删除 | 完全不要 |
| 5. 消息网关与多渠道 | 修改后保留 | 首期按最新 Hermes 事实纳入 `weixin`、`wecom`、`dingtalk`、`feishu` |
| 6. 文件与代码工具 | 保留 | 首期核心能力 |
| 7. 终端与进程工具 | 保留 | 作为受控执行能力 |
| 8. Web 与浏览器工具 | 删除 | 不内建，需要时走技能扩展 |
| 9. 多模态能力 | 删除 | 全部砍掉 |
| 10. Agent 内部工具 | 修改后保留 | 只保留核心内部工具 |
| 11. 自动化与主动外发 | 保留 | 保留定时任务与主动通知 |
| 12. 技能系统 | 保留 | 作为后续扩展主通道 |
| 13. 扩展协议 | 删除 | 完全不要 |
| 14. 安全与可靠性 | 保留 | 能力保留，但首期默认关闭审计和审批 |
| 15. 研究与训练 | 删除 | 完全不要 |
| 前端网页查看端 | 删除 | Debug Web 也不要 |

## 首期保留能力

### 1. 统一运行时

所有入站消息、内部任务、定时触发都先进入统一运行时，再由运行时负责：

- 去重
- 会话持久化
- 运行状态追踪
- 并发控制
- 回复路由
- Agent 执行

### 2. 工作区与上下文

继续采用工作区驱动模式，优先保留：

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

### 3. 首期内建工具范围

模型可见工具按 Hermes 原名收敛为：

- 文件工具：`read_file`、`write_file`、`patch`、`search_files`
- 运行时工具：`todo`、`memory`、`delegate_task`
- 自动化工具：`cronjob`
- 终端工具：`terminal`、`process`
- 渠道工具：`send_message`
- 技能工具：`skills_list`、`skill_view`、`skill_manage`

### 4. 国内渠道

首期消息网关只做：

- `weixin`
- `wecom`
- `dingtalk`
- `feishu`

其中个人微信按 Hermes 主线稳定命名统一使用 `weixin`，不再额外发明 `wechat` 对外名。

### 5. 自动化与主动通知

保留：

- 定时任务创建、查看、更新、暂停、恢复、立即触发、删除
- 运行结果回投递
- 主动消息发送

但模型侧仍沿用 Hermes 的单工具形态：

- `cronjob(action="create" | "list" | "update" | "pause" | "resume" | "run" | "remove")`

### 6. 安全与审计默认值

能力保留，但默认值改为开放：

- `security.auditEnabled=false`
- `security.requireTerminalApproval=false`
- `security.allowExplicitSendTargets=true`
- `terminal.allowedCommands=[]`

文件工具仍然必须受工作区边界保护，但不额外增加首期开箱即用的风险拦截。

### 7. 技能系统

技能系统保留，作为后续扩展主通道。

对于当前不内建的能力，优先通过技能扩展接入，而不是继续扩大内建工具面。

## 不内建，但允许后续通过技能扩展

- `web_search`
- `web_extract`
- 全部 `browser_*`

## 明确不做

### 1. 交互与界面

- CLI 交互
- Debug Web
- 前端网页查看端

### 2. 浏览器与多模态

- `web_search`、`web_extract` 的内建实现
- 全部 `browser_*`
- `vision_analyze`
- 图像生成
- TTS / STT

### 3. 内部工具

- `clarify`
- `execute_code`
- `mixture_of_agents`

### 4. 协议与服务暴露

- MCP
- ACP
- API Server

### 5. 研究与训练

- RL
- batch runner
- trajectory compression
- benchmark / eval 类能力

## 命名原则

只要 Hermes 已有稳定工具名或平台名，就直接沿用原名。

明确约束：

- 用 `patch`，不要改成 `edit_file`
- 用 `delegate_task`，不要改成 `spawn_task`
- 用 `cronjob`，不要拆成 `list_jobs`、`add_job`、`start_job`、`run_job_now` 等独立工具名
- 个人微信平台统一用 `weixin`
- Java 内部类名和服务名可以自行组织，但模型看到的工具名和平台名以 Hermes 为准
