# jimuqu-claw 最终工具范围

## 说明

本清单只定义“首期模型可见的内建工具面”。

范围前提：

- 保留统一 Agent 运行时
- 保留工作区驱动提示词
- 保留国内渠道：`weixin`、`dingtalk`、`feishu`、`wecom`
- 不做 CLI 交互
- 不做 Debug Web
- 不做 Web / 浏览器内建工具
- 不做多模态
- 不做 MCP / ACP / API Server
- 不做研究与训练能力

命名前提：

- 工具名称按 `hermes-agent` 原名定义
- 不参考 `solonclaw`
- 不再引入 `edit_file`、`spawn_task`、`run_job_now` 这类替代名

## 一、首期内建工具

### 1. 文件工具

这是最核心的一组，必须内建。

保留：

- `read_file`
  读取工作区文件，支持分页、偏移、限制返回大小
- `write_file`
  新建或覆盖工作区文件
- `patch`
  对已有文件做局部编辑，兼容 Hermes 的 replace / patch 语义
- `search_files`
  按文件名或按内容搜索工作区文件

实现要求：

- 全部受工作区边界保护
- 默认不允许越出工作区
- 默认不允许写系统敏感路径
- 大文件读取要限流
- 二进制文件默认不直接返回给模型

### 2. 运行时工具

这一组负责让 Agent 管理规划、记忆和子任务。

保留：

- `todo`
  简单任务规划与待办追踪
- `memory`
  记忆写入与读取
- `delegate_task`
  创建子任务并回收结果

说明：

- 子任务状态、运行跟踪、结果汇总可以作为运行时内部能力存在
- 但模型侧不再额外发明 `spawn_task`、`list_child_runs`、`get_run_status`、`get_child_summary`、`notify_user` 这一套新名字
- 主动对外发送消息仍由 `send_message` 负责

### 3. 定时任务工具

首期保留定时任务能力，但模型侧沿用 Hermes 的压缩式单工具。

保留：

- `cronjob`

支持的核心动作：

- `create`
- `list`
- `update`
- `pause`
- `resume`
- `run`
- `remove`

说明：

- 不拆成 `list_jobs`、`get_job`、`add_job`、`update_job`、`remove_job`、`start_job`、`stop_job`、`run_job_now`
- Java 内部仍然可以有对应服务方法，但模型工具面保持 `cronjob(action=...)`

### 4. 终端与进程工具

这类工具保留，但要强约束。

保留：

- `terminal`
  执行受控命令
- `process`
  查询或管理后台进程

首期最小能力：

- 执行单条命令
- 读取输出
- 支持超时
- 支持后台运行
- 支持查询后台任务状态
- 支持停止后台任务
- `process` 首期动作为 `list`、`poll`、`log`、`wait`、`kill`

强约束：

- 默认工作区内执行
- 默认不要求审批，但保留 `security.requireTerminalApproval`
- 默认不启用命令白名单，但保留 `terminal.allowedCommands`
- 不追求 Hermes 的多后端矩阵

首期不建议做：

- Docker / SSH / Modal / Daytona / Singularity 等多执行后端
- 复杂 sudo 流程
- 过强的远程执行能力

### 5. 渠道工具

由于保留了消息网关，仍需要最小渠道工具。

保留：

- `send_message`
  向已接入国内渠道发送消息

范围限制：

- 只支持 `weixin`
- 只支持 `dingtalk`
- 只支持 `feishu`
- 只支持 `wecom`

### 6. 技能工具

首期保留 Hermes 的技能渐进披露工具。

保留：

- `skills_list`
- `skill_view`
- `skill_manage`

说明：

- `skills_list` 负责列出技能元数据
- `skill_view` 负责查看 `SKILL.md` 或关联文件
- `skill_manage` 负责创建、修改和维护技能目录内容

## 二、不内建，后续走技能扩展

这些能力当前不进内建工具面，但允许未来通过技能体系接入。

### 1. Web 能力

不内建：

- `web_search`
- `web_extract`
- `browser_*`

后续方案：

- 有明确业务需求时，通过技能体系接入
- 不作为首期核心工具

### 2. 其他潜在扩展

如果后续确实出现图片、语音、网页交互需求，也优先按技能扩展处理，而不是先扩大内建工具面。

## 三、明确不做的工具

### 1. 不做的内部工具

- `clarify`
- `execute_code`
- `mixture_of_agents`

### 2. 不做的浏览器与多模态工具

- 全部 `browser_*`
- `vision_analyze`
- `image_generate`
- `text_to_speech`
- `speech_to_text`

### 3. 不做的扩展协议与服务工具

- MCP 相关工具
- ACP 相关工具
- API Server 相关工具

### 4. 不做的研究型工具

- RL
- batch runner
- trajectory compression
- benchmark / eval 类工具

## 四、最终工具面

如果把首期工具面收敛成最小可用集合，建议就是：

### A. 文件工具

- `read_file`
- `write_file`
- `patch`
- `search_files`

### B. 运行时工具

- `todo`
- `memory`
- `delegate_task`

### C. 定时任务工具

- `cronjob`

### D. 终端与进程工具

- `terminal`
- `process`

### E. 渠道工具

- `send_message`

### F. 技能工具

- `skills_list`
- `skill_view`
- `skill_manage`

## 五、命名约束

为了保证后续实现、文档、提示词和测试都只有一套口径，统一采用以下规则：

- 保留 Hermes 原名：`read_file`、`write_file`、`patch`、`search_files`、`terminal`、`process`、`todo`、`memory`、`delegate_task`、`cronjob`、`send_message`、`skills_list`、`skill_view`、`skill_manage`
- 不使用 `edit_file`
- 不使用 `spawn_task`
- 不使用 `run_job_now`
- 不使用 `list_jobs`、`add_job`、`start_job`、`stop_job` 这类拆分式模型工具名

可接受的内部实现映射：

- Java 服务方法可以叫 `editFile()`，但模型工具名仍然是 `patch`
- Java 运行时服务可以叫 `spawnTask()`，但模型工具名仍然是 `delegate_task`
- Java 调度服务可以有 `runJobNow()`，但模型工具名仍然是 `cronjob(action="run")`

## 六、结论

`jimuqu-claw` 首期不是“大而全工具箱”，而是围绕以下五组能力构建：

1. 文件编辑
2. 任务规划与子任务
3. 定时任务
4. 受控命令执行
5. 国内渠道触达

除此之外的能力，优先通过技能扩展，而不是继续扩大内建工具面。
