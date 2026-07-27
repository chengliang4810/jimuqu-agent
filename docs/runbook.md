# solonclaw 运维 Runbook

本文用于处理 solonclaw 生产事故。部署、升级、回滚和备份恢复步骤见 [部署手册](deploy.md)。

## 1. 处置原则

1. 先止损：暂停有副作用的定时任务、渠道或升级动作，不要在故障现场反复重启。
2. 再取证：记录时间、版本、部署方式、健康响应、服务状态和最近日志。
3. 后恢复：优先使用可逆操作；替换 JAR、配置或数据库前先保留带时间戳的现场副本。
4. 最后验证：HTTP 存活不等于业务恢复，必须验证模型、SQLite、渠道和审批链路。
5. 严禁泄密：工单和聊天中不得粘贴 API Key、Bearer Token、Cookie、渠道凭据、完整配置或未脱敏的会话内容。

除非明确说明，以下命令假设：

```bash
INSTALL_DIR="${SOLONCLAW_HOME:-$HOME/.solonclaw}"
WORKSPACE_DIR="${SOLONCLAW_WORKSPACE:-$INSTALL_DIR/workspace}"
```

## 2. 五分钟基线检查

### 2.1 确认 HTTP 进程

```bash
date -Iseconds
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/health/detailed
```

`/health` 返回 `{"ok":true,"service":"solonclaw"}` 只表示进程存活。`/health/detailed` 还包含 PID、运行时长、网关状态和活动任务摘要，但也不能替代真实模型与渠道探测。

### 2.2 确认服务管理器状态

Linux：

```bash
sudo systemctl status solonclaw --no-pager
sudo journalctl -u solonclaw --since "-15 min" --no-pager
```

macOS：

```bash
launchctl list com.solonclaw.agent
tail -n 200 "$INSTALL_DIR/logs/launchd-stderr.log"
```

Windows PowerShell：

```powershell
$InstallDir = if ($env:SOLONCLAW_HOME) {
  $env:SOLONCLAW_HOME
} else {
  Join-Path $env:USERPROFILE ".solonclaw"
}
$Nssm = Join-Path $InstallDir "nssm.exe"
& $Nssm status solonclaw
```

Docker：

```bash
docker compose ps
docker inspect solonclaw \
  --format 'status={{.State.Status}} restart={{.RestartCount}} oom={{.State.OOMKilled}} image={{.Image}}'
docker compose logs --since=15m --tail=300 solonclaw
```

### 2.3 查看分层日志

```bash
tail -n 200 "$WORKSPACE_DIR/logs/errors.log"
tail -n 200 "$WORKSPACE_DIR/logs/agent.log"
tail -n 200 "$WORKSPACE_DIR/logs/gateway.log"
```

- `errors.log`：所有 ERROR 级别事件。
- `agent.log`：会话、工具、调度和后台任务。
- `gateway.log`：渠道连接、鉴权、投递和回调。

先记录原始时间范围，再按 `ERROR`、`WARN`、HTTP 状态码、渠道名、run ID 或 request ID 缩小范围。不要先清空日志。

### 2.4 保存最小证据

```bash
EVIDENCE_DIR="$INSTALL_DIR/incidents/$(date +%Y%m%d-%H%M%S)"
umask 077
mkdir -p "$EVIDENCE_DIR"
curl -fsS http://127.0.0.1:8080/health/detailed \
  > "$EVIDENCE_DIR/health-detailed.json" || true
tail -n 500 "$WORKSPACE_DIR/logs/errors.log" \
  > "$EVIDENCE_DIR/errors.tail.log" || true
tail -n 500 "$WORKSPACE_DIR/logs/gateway.log" \
  > "$EVIDENCE_DIR/gateway.tail.log" || true
```

证据目录可能含敏感业务信息，只能存放在受控主机并限制权限。

## 3. 服务无法启动或健康检查失败

按以下顺序定位：

1. 服务是否在重启循环。
2. `8080` 是否被其他进程占用。
3. Java 是否可执行，JAR 是否存在且可读。
4. `workspace/config.yml` 是否为有效 YAML。
5. 工作区、`data/` 和 `logs/` 是否可写。
6. 最近是否升级了 JAR、镜像、Java 或配置。

Linux 检查端口和文件：

```bash
sudo ss -ltnp | grep ':8080' || true
test -r "$INSTALL_DIR/solonclaw.jar"
test -r "$WORKSPACE_DIR/config.yml"
test -w "$WORKSPACE_DIR"
```

Docker 检查挂载和权限：

```bash
docker inspect solonclaw \
  --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
ls -ld "$WORKSPACE_DIR" "$WORKSPACE_DIR/data" "$WORKSPACE_DIR/logs"
```

如果配置变更后启动失败：

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
cp -p "$WORKSPACE_DIR/config.yml" \
  "$WORKSPACE_DIR/config.yml.failed-$STAMP"
```

用升级前备份或最后一个已知可用配置恢复，不要凭猜测删除整段配置。`solonclaw.workspace` 是启动级配置，不能通过工作区内的 `config.yml` 改变当前工作区。

如果升级后重启循环，按 [部署手册的升级与回滚](deploy.md#6-升级与回滚) 恢复旧 JAR或镜像。不要在没有数据库兼容性结论时单独回滚数据库。

## 4. 渠道掉线、收不到消息或发不出去

### 4.1 判断故障范围

- 所有渠道都异常：先检查主进程、网络、SQLite 和网关总状态。
- 单一渠道异常：重点检查该渠道的启用状态、凭据、平台会话和访问策略。
- 只收不发：检查平台发送权限、机器人状态、目标绑定和限流。
- 只发不收：检查 websocket、stream 或 long-poll 连接、回调鉴权和平台侧事件订阅。
- 私聊正常、群聊异常：检查群策略、允许用户、@ 提及要求和机器人群权限。

在 TUI 中执行：

```text
/status
/gateway status
/doctor
```

或在 Dashboard 的“渠道”和“诊断”页面查看 doctor。不要把 doctor 返回的敏感配置原样复制到外部工单。

### 4.2 检查网关日志

```bash
grep -Ein \
  'error|warn|disconnect|reconnect|unauthorized|forbidden|timeout|429|rate.limit' \
  "$WORKSPACE_DIR/logs/gateway.log" | tail -n 200
```

重点确认：

- 配置的渠道是否 `enabled: true`。
- 凭据是否过期、被撤销或属于错误环境。
- `allowedUsers`、DM/群聊策略、owner/pairing 绑定是否允许当前发送者。
- 主机时间是否准确；签名和临时票据依赖正确时钟。
- 主机能否解析并访问渠道官方端点。
- 平台控制台是否停用了机器人、事件订阅或发送权限。

### 4.3 恢复顺序

1. 在平台侧确认机器人和凭据有效。
2. 在 Dashboard 重新保存该渠道配置或重新完成配对。
3. 先只重启网关相关运行态；无法隔离时再安排主服务重启。
4. 用一个受控账号完成真实入站和出站。
5. 同时观察 `gateway.log`，确认没有快速重连或重复投递。

不要为了恢复单一渠道而删除 `state.db`；渠道绑定、会话和审批状态都存储在 SQLite 中。

## 5. 审批超时或审批一直待处理

`approvals.timeoutSeconds` 是统一审批有效期。令牌过期后，旧 `/approve` 不会执行原操作，这是安全边界，不是队列卡死。

先在原会话执行：

```text
/approve list
/approve status
```

处理步骤：

1. 确认审批回复发回了产生审批的同一会话和同一 Profile。
2. 确认使用当前列表中的审批 ID，而不是旧消息中的 ID。
3. 已过期时重新发起原始操作，生成新的审批请求。
4. 渠道按钮失败时，使用文本命令 `/approve <审批ID>` 或 `/deny <审批ID>`。
5. 需要清理记忆范围时，先查看状态，再使用 `/approve clear session`、`/approve clear always` 或 `/approve clear all`。

定时任务默认使用 `security.guardrailCronMode: strict` 时，不会等待人工审批，而是直接阻断可审批危险操作。只有显式配置为 `approval` 时才会暂停并请求原会话审批。

如果正常人工响应经常超过有效期，可以在 `workspace/config.yml` 显式设置：

```yaml
approvals:
  # 所有审批的有效期，单位秒；按真实值班响应时间设置。
  timeoutSeconds: 180
```

延长有效期会扩大危险操作令牌的可用窗口。修改后重新发起审批验证，不要复用旧审批 ID，也不要通过直接修改 SQLite 绕过审批。

## 6. SQLite 损坏、锁等待或数据不可读

默认数据库是 `workspace/data/state.db`，命名 Profile 位于 `workspace/profiles/<名称>/data/state.db`。`/health` 是存活检查，即使数据库业务查询失败也可能仍返回成功。

### 6.1 先停止写入并保留现场

Linux：

```bash
sudo systemctl stop solonclaw
```

macOS 使用 `launchctl unload`，Windows 使用安装目录内的 `nssm.exe stop solonclaw`，Docker 使用 `docker compose stop solonclaw`。

停止后复制主库及可能存在的 WAL/SHM：

```bash
DB="$WORKSPACE_DIR/data/state.db"
FORENSICS="$INSTALL_DIR/incidents/db-$(date +%Y%m%d-%H%M%S)"
umask 077
mkdir -p "$FORENSICS"
cp -p "$DB" "$FORENSICS/"
test ! -f "$DB-wal" || cp -p "$DB-wal" "$FORENSICS/"
test ! -f "$DB-shm" || cp -p "$DB-shm" "$FORENSICS/"
```

不要在唯一副本上运行 `VACUUM`、`.recover`、手工 `DELETE` 或表结构修改。

### 6.2 检查完整性

```bash
sqlite3 "$DB" "PRAGMA quick_check;"
sqlite3 "$DB" "PRAGMA integrity_check;"
```

返回 `ok` 才表示检查通过。如果只是 `database is locked`：

- 确认服务和残留 Java 进程已经停止。
- 确认没有另一个 solonclaw 实例指向同一工作区。
- 不要直接删除 `-wal` 或 `-shm`；它们可能包含尚未合并的数据。

### 6.3 优先从已验证备份恢复

按 [部署手册的恢复步骤](deploy.md#73-恢复) 恢复整个工作区或受影响 Profile。恢复前保留当前工作区，恢复后再次执行 `PRAGMA integrity_check;`。

### 6.4 无可用备份时尝试恢复副本

只对现场副本操作：

```bash
BROKEN="$FORENSICS/state.db"
RECOVERED="$FORENSICS/state.recovered.db"

sqlite3 "$BROKEN" ".recover" | sqlite3 "$RECOVERED"
sqlite3 "$RECOVERED" "PRAGMA integrity_check;"
```

即使返回 `ok`，也要核对关键表、会话数、定时任务和渠道绑定。`.recover` 可能丢失记录或约束；未经业务核对，不得直接替换生产数据库。

## 7. 模型限流、配额不足或上游不可用

先区分错误类型：

| 信号                                     | 含义                 | 处置                                         |
| ---------------------------------------- | -------------------- | -------------------------------------------- |
| `429`、`rate_limit`、`too many requests` | 临时限流             | 降低并发，等待窗口恢复，配置备用 Provider    |
| `402`、`quota`、`billing`                | 配额或计费问题       | 检查账户余额和套餐，不要盲目重试             |
| `401` / `403`                            | 密钥、权限或组织错误 | 核对凭据和 Provider 权限                     |
| `404`、`model not found`                 | 模型名或端点不匹配   | 核对模型、基础 URL 与协议方言                |
| `413`、context overflow                  | 请求或上下文过大     | 减少附件/上下文，检查上下文窗口配置          |
| `500` / `502` / `503` / `504` / `529`    | 上游故障或过载       | 使用备用 Provider，保留请求时间与 request ID |

solonclaw 会把 429 分类为可降级错误，并在配置了 `fallbackProviders` 时立即切换候选 Provider。没有备用 Provider 时，重复同一请求只会继续消耗限流窗口。

处理步骤：

1. 在 Dashboard 的模型健康页确认是单个模型、单个 Provider 还是全部上游故障。
2. 检查 `agent.log` 中的状态码、失败原因、Provider 和模型；不要输出 API Key。
3. 在 Provider 控制台核对配额、限流窗口和服务状态。
4. 降低并发任务和子 Agent 数量，暂停非关键 Cron。
5. 配置与主 Provider 不同故障域的 `fallbackProviders`。
6. 用低风险短请求验证，再恢复正常流量。

备用配置示例：

```yaml
fallbackProviders:
  - provider: backup
    model: backup-model
```

`backup` 必须在 `providers` 中完整配置。不要通过提高重试次数掩盖长期配额不足；限流和计费故障应由容量、配额或故障转移解决。

## 8. JVM OOM 或容器被系统杀死

### 8.1 确认是哪类内存故障

Linux：

```bash
sudo journalctl -u solonclaw --since "-30 min" --no-pager |
  grep -Ei 'OutOfMemoryError|Java heap space|GC overhead|Killed process|oom-kill' || true
sudo dmesg -T |
  grep -Ei 'out of memory|killed process|oom-kill' | tail -n 50 || true
```

Docker：

```bash
docker inspect solonclaw \
  --format 'status={{.State.Status}} oom={{.State.OOMKilled}} exit={{.State.ExitCode}} restart={{.RestartCount}}'
docker stats --no-stream solonclaw
docker compose logs --since=30m --tail=500 solonclaw |
  grep -Ei 'OutOfMemoryError|Java heap space|GC overhead|Killed process' || true
```

- JVM 日志出现 `OutOfMemoryError`：Java 堆、直接内存、线程或元空间耗尽。
- 容器 `OOMKilled=true` 或内核记录 killed process：宿主机或容器限制先杀死了 JVM。
- 只有频繁 Full GC：可能尚未 OOM，但已经出现内存压力。

### 8.2 先降低负载

- 暂停非关键 Cron 和批量任务。
- 降低 `solonclaw.task.subagentMaxConcurrency`。
- 减少超大附件、工具输出和长上下文请求。
- 检查是否有异常重试、失控子进程或大量并发浏览器任务。

配置示例：

```yaml
solonclaw:
  task:
    # 子 Agent 最大并发数；事故期间可临时降低。
    subagentMaxConcurrency: 1
```

### 8.3 设置受控堆上限

堆上限必须低于主机或容器内存限制，为 Metaspace、直接内存、线程栈和子进程留余量。以下 `1g` 只是示例，应根据监控数据调整。

systemd 使用 override：

```bash
sudo systemctl edit solonclaw
```

填入：

```ini
[Service]
Environment="JAVA_TOOL_OPTIONS=-Xms256m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/workspace/logs"
```

然后执行：

```bash
sudo systemctl daemon-reload
sudo systemctl restart solonclaw
```

Docker Compose 可增加：

```yaml
services:
  solonclaw:
    environment:
      SOLONCLAW_OFFICIAL_DOCKER_IMAGE: "1"
      JAVA_TOOL_OPTIONS: >-
        -Xms256m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError
        -XX:HeapDumpPath=/app/workspace/logs
    mem_limit: 1536m
```

执行 `docker compose config` 确认格式，再重建容器。macOS 可在 launchd 的 `EnvironmentVariables` 中增加 `JAVA_TOOL_OPTIONS`；Windows 可使用 NSSM 的 `AppEnvironmentExtra` 设置同名变量。

Heap dump 可能包含 API Key、会话内容和文件片段。应限制权限、加密传输并按敏感数据处理，分析完成后按保留策略清理。

### 8.4 恢复验收

重启后持续观察一个完整业务高峰，至少确认：

- 重启次数不再增加。
- 堆和容器内存趋于稳定。
- 模型调用、工具执行和渠道收发正常。
- 没有因降低并发造成长期积压。

只扩大 `-Xmx` 可能把 JVM OOM 变成宿主机 OOM。若内存持续线性增长，应保留 heap dump 和对应版本，进入代码级泄漏分析。

## 9. 升级或恢复后的统一验收

```bash
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/health/detailed
tail -n 100 "$WORKSPACE_DIR/logs/errors.log"
```

然后逐项验证：

1. Dashboard 登录和 doctor。
2. 默认 Profile 与至少一个命名 Profile 的会话读取。
3. 主模型短请求；配置了 fallback 时再验证备用路由。
4. 每个启用渠道的一条真实入站和出站。
5. 一次需要人工确认的低风险审批流程。
6. 下一次 Cron/Heartbeat 不出现集中失败。

事故关闭记录应包含：开始和恢复时间、影响范围、确认根因、实际恢复动作、数据丢失情况、当前版本/JAR 校验或镜像摘要，以及防复发任务。无法确认根因时必须写“最佳假设”，不能把时间相关性当成已证实原因。
