# solonclaw 部署手册

本文说明 solonclaw 的原生安装、Docker Compose 部署、服务管理、升级回滚，以及 SQLite 备份恢复。事故处置见 [运维 Runbook](runbook.md)。

## 1. 部署边界

solonclaw 是单实例服务。默认监听 `8080`，公开存活检查为：

```bash
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/health/detailed
```

原生安装的默认目录如下：

| 内容     | Linux / macOS              | Windows                    |
| -------- | -------------------------- | -------------------------- |
| 安装目录 | `~/.solonclaw`             | `%USERPROFILE%\.solonclaw` |
| 运行 JAR | `<安装目录>/solonclaw.jar` | `<安装目录>\solonclaw.jar` |
| 工作区   | `<安装目录>/workspace`     | `<安装目录>\workspace`     |
| 生效配置 | `<工作区>/config.yml`      | `<工作区>\config.yml`      |
| SQLite   | `<工作区>/data/state.db`   | `<工作区>\data\state.db`   |
| 日志     | `<工作区>/logs/`           | `<工作区>\logs\`           |

命名 Profile 的数据位于 `<工作区>/profiles/<Profile 名>/`，每个 Profile 有独立的 `config.yml` 和 `data/state.db`。备份时应覆盖整个工作区，不能只复制默认 Profile 的数据库。

Docker 镜像固定使用 `/app/workspace` 作为持久化目录，并以 UID/GID `10001` 的非 root 用户运行。镜像入口实际启动 `/app/workspace/solonclaw.jar`；首次启动且该文件不存在时，才从镜像复制内置 JAR。

## 2. 上线前检查

- 原生运行要求 Java 8+，生产环境推荐 Java 17。
- 原生 TUI 要求 Node.js 24+；后端服务本身不依赖 Node.js。
- Docker 部署要求 Docker 和 Compose v2。
- `workspace/config.yml` 必须设置高强度 `solonclaw.dashboard.accessToken`，不要使用弱口令。
- 工作区和备份中包含 API Key、渠道凭据与会话数据，只允许运行账户和备份账户访问。
- 对外开放 `8080` 前应使用防火墙限制来源，或在受控反向代理后提供 TLS；不要把无访问控制的端口直接暴露到公网。
- 生产升级应使用明确的 Release 标签或镜像摘要，避免把 `latest` 当作可审计版本。

完整配置参考 [config.example.yml](../config.example.yml)，安装行为以 [install.sh](../scripts/install.sh)、[install.ps1](../scripts/install.ps1)、[Dockerfile](../Dockerfile) 和 [docker-compose.yml](../docker-compose.yml) 为准。

## 3. 一键安装

一键安装会交互式选择原生服务或 Docker Compose，并创建最小 `workspace/config.yml`。生产环境建议先下载并审阅脚本，再执行。

### 3.1 Linux / macOS

```bash
curl -fsSLo /tmp/solonclaw-install.sh \
  https://raw.githubusercontent.com/chengliang4810/solon-claw/main/scripts/install.sh
less /tmp/solonclaw-install.sh
bash /tmp/solonclaw-install.sh
```

脚本会校验 Release 中 `solonclaw.jar` 的 SHA-256。Linux 使用 systemd，macOS 使用当前用户的 launchd Agent。安装目录可在交互提示中修改。

### 3.2 Windows

在 PowerShell 中先下载并检查脚本：

```powershell
$Installer = Join-Path $env:TEMP "solonclaw-install.ps1"
Invoke-WebRequest `
  -Uri "https://raw.githubusercontent.com/chengliang4810/solon-claw/main/scripts/install.ps1" `
  -OutFile $Installer
Get-Content $Installer
powershell -ExecutionPolicy Bypass -File $Installer
```

原生模式会把 `nssm.exe` 放在安装目录并注册自动启动的 `solonclaw` 服务。脚本会校验 JAR，但 NSSM 2.24 来自 `nssm.cc`；受控环境应按组织的软件供应链规则预置或校验 NSSM。

## 4. 原生服务管理

以下命令假设使用默认安装目录。自定义安装目录时替换变量即可。

### 4.1 Linux systemd

安装脚本创建 `/etc/systemd/system/solonclaw.service`。服务失败后等待 5 秒重启，停止超时为 30 秒。

```bash
INSTALL_DIR="${SOLONCLAW_HOME:-$HOME/.solonclaw}"
WORKSPACE_DIR="$INSTALL_DIR/workspace"

sudo systemctl status solonclaw --no-pager
sudo systemctl restart solonclaw
sudo journalctl -u solonclaw -n 200 --no-pager
tail -n 200 "$WORKSPACE_DIR/logs/errors.log"
```

修改 unit 后必须重新加载：

```bash
sudo systemctl daemon-reload
sudo systemctl restart solonclaw
```

### 4.2 macOS launchd

安装脚本创建 `~/Library/LaunchAgents/com.solonclaw.agent.plist`。标准输出和错误输出分别写入安装目录下的 `logs/launchd-stdout.log` 与 `logs/launchd-stderr.log`。

```bash
INSTALL_DIR="${SOLONCLAW_HOME:-$HOME/.solonclaw}"
PLIST="$HOME/Library/LaunchAgents/com.solonclaw.agent.plist"

launchctl list com.solonclaw.agent
launchctl stop com.solonclaw.agent
launchctl start com.solonclaw.agent
tail -n 200 "$INSTALL_DIR/logs/launchd-stderr.log"
tail -n 200 "$INSTALL_DIR/workspace/logs/errors.log"
```

需要维护窗口且不希望 KeepAlive 拉起进程时，应卸载再恢复：

```bash
launchctl unload "$PLIST"
# 在这里执行备份、恢复或 JAR 替换。
launchctl load "$PLIST"
```

### 4.3 Windows NSSM

以 PowerShell 运行：

```powershell
$InstallDir = if ($env:SOLONCLAW_HOME) {
  $env:SOLONCLAW_HOME
} else {
  Join-Path $env:USERPROFILE ".solonclaw"
}
$Nssm = Join-Path $InstallDir "nssm.exe"

& $Nssm status solonclaw
& $Nssm restart solonclaw
Get-Content (Join-Path $InstallDir "workspace\logs\errors.log") -Tail 200
```

不要依赖全局 `nssm` 命令；安装脚本保存的是 `<安装目录>\nssm.exe`。

## 5. Docker Compose

仓库 Compose 可直接启动：

```bash
docker compose pull
docker compose up -d
docker compose ps
docker compose logs --tail=200 solonclaw
curl -fsS http://127.0.0.1:8080/health
```

默认把宿主机 `./workspace` 挂载到 `/app/workspace`。Linux 上如果容器提示无权写入，先确认挂载目标确实是专用 solonclaw 工作区，再修正给镜像运行用户：

```bash
WORKSPACE_DIR="$(pwd)/workspace"
test -d "$WORKSPACE_DIR"
sudo chown -R 10001:10001 "$WORKSPACE_DIR"
sudo chmod -R u+rwX,go-rwx "$WORKSPACE_DIR"
docker compose up -d
```

进入容器 TUI：

```bash
docker exec -it solonclaw solonclaw
```

停止容器不会删除工作区：

```bash
docker compose stop solonclaw
docker compose start solonclaw
```

`docker compose down -v` 可能删除 Compose 管理的卷。当前仓库使用 bind mount，但生产操作仍不应使用 `-v`，除非已经核对 Compose 配置和备份。

## 6. 升级与回滚

### 6.1 升级前门禁

每次升级先完成以下事项：

1. 记录当前版本、JAR 校验值、镜像标签或摘要。
2. 创建整个工作区的一致性备份，见第 7 节。
3. 阅读目标 Release 说明，确认 Java 版本、配置和 SQLite 迁移兼容性。
4. 预留能启动旧 JAR 或旧镜像的回滚路径。
5. 在维护窗口内升级，并用 `/health`、`/health/detailed`、Dashboard 登录、模型调用和至少一个真实渠道完成验收。

### 6.2 Linux、macOS 与 Docker 的在线 JAR 升级

在 TUI 或受支持的消息渠道中执行：

```text
/version check
/version update
```

在线升级只接受同时提供 `solonclaw.jar` 和 `SHA256SUMS` 的 Release。它会：

1. 下载并校验最新 JAR。
2. 把当前 JAR 复制为同目录的 `solonclaw.jar.previous`。
3. 原子替换当前 JAR。
4. 以退出码 `75` 退出，交给 systemd、launchd 或 Docker 重启策略拉起。

Windows 禁止在线 JAR 自更新，必须按第 6.4 节停服务后手动替换。

升级后检查：

```bash
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/health/detailed
```

如果新版本不能启动，停止运行管理器，保存失败 JAR，再恢复 `.previous`：

```bash
INSTALL_DIR="${SOLONCLAW_HOME:-$HOME/.solonclaw}"
STAMP="$(date +%Y%m%d-%H%M%S)"

sudo systemctl stop solonclaw
cp -p "$INSTALL_DIR/solonclaw.jar" "$INSTALL_DIR/solonclaw.jar.failed-$STAMP"
cp -p "$INSTALL_DIR/solonclaw.jar.previous" "$INSTALL_DIR/solonclaw.jar"
sudo systemctl start solonclaw
curl -fsS http://127.0.0.1:8080/health
```

macOS 把 systemd 的停止和启动命令替换为 `launchctl unload "$PLIST"` 与 `launchctl load "$PLIST"`。Docker 则使用 `docker compose stop solonclaw` 与 `docker compose up -d solonclaw`，JAR 路径是宿主机工作区内的 `solonclaw.jar`。

如果目标版本迁移了 SQLite 结构，JAR 回滚不等于数据回滚。应依据 Release 说明决定是否同时恢复升级前工作区；恢复旧数据库会丢失升级后产生的数据。

### 6.3 Docker 镜像升级与回滚

只执行 `docker compose pull` 不足以升级应用：已有 `/app/workspace/solonclaw.jar` 时，入口脚本不会用新镜像内的 JAR 覆盖它。

受控镜像升级步骤：

```bash
COMPOSE_DIR="/path/to/solonclaw"
WORKSPACE_DIR="$COMPOSE_DIR/workspace"
STAMP="$(date +%Y%m%d-%H%M%S)"

cd "$COMPOSE_DIR"
docker inspect solonclaw --format '{{.Config.Image}} {{.Image}}'
docker compose stop solonclaw
cp -p "$WORKSPACE_DIR/solonclaw.jar" \
  "$WORKSPACE_DIR/solonclaw.jar.image-prev-$STAMP"
mv "$WORKSPACE_DIR/solonclaw.jar" \
  "$WORKSPACE_DIR/solonclaw.jar.before-image-$STAMP"
```

然后把 Compose 的 `image:` 改为明确的 Release 标签，例如 `ghcr.io/chengliang4810/solonclaw:vYYYY.MM.DD-abcdefg`，再执行：

```bash
docker compose config
docker compose pull solonclaw
docker compose up -d --force-recreate solonclaw
docker compose ps
curl -fsS http://127.0.0.1:8080/health
```

因为工作区 JAR 已移走，新容器会从目标镜像复制 JAR。确认成功前保留旧 JAR和升级前工作区备份。

镜像回滚时先停止容器，把失败 JAR移到带时间戳的文件名，将 Compose `image:` 恢复为旧标签或摘要，并把 `solonclaw.jar.before-image-<时间戳>` 复制回 `solonclaw.jar`，最后强制重建容器。若目标版本包含数据库迁移，还要按兼容性判断是否恢复升级前工作区。

### 6.4 Windows JAR 升级与回滚

从目标 GitHub Release 下载 `solonclaw.jar` 和 `SHA256SUMS`，然后在 PowerShell 校验并替换：

```powershell
$InstallDir = if ($env:SOLONCLAW_HOME) {
  $env:SOLONCLAW_HOME
} else {
  Join-Path $env:USERPROFILE ".solonclaw"
}
$ReleaseTag = "vYYYY.MM.DD-abcdefg"
$ReleaseBase = "https://github.com/chengliang4810/solon-claw/releases/download/$ReleaseTag"
$NewJar = Join-Path $InstallDir "solonclaw.jar.download"
$Checksums = Join-Path $InstallDir "SHA256SUMS.download"
$Nssm = Join-Path $InstallDir "nssm.exe"

Invoke-WebRequest -Uri "$ReleaseBase/solonclaw.jar" -OutFile $NewJar
Invoke-WebRequest -Uri "$ReleaseBase/SHA256SUMS" -OutFile $Checksums
$Entry = Get-Content $Checksums |
  Where-Object { $_ -match '^(?<hash>[0-9A-Fa-f]{64})\s+\*?solonclaw\.jar\s*$' }
if (@($Entry).Count -ne 1) { throw "SHA256SUMS 中缺少或重复 solonclaw.jar" }
$Expected = (($Entry -split '\s+')[0]).ToLowerInvariant()
$Actual = (Get-FileHash $NewJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Expected -ne $Actual) { throw "solonclaw.jar SHA-256 校验失败" }

& $Nssm stop solonclaw
Copy-Item (Join-Path $InstallDir "solonclaw.jar") `
  (Join-Path $InstallDir "solonclaw.jar.previous") -Force
Move-Item $NewJar (Join-Path $InstallDir "solonclaw.jar") -Force
& $Nssm start solonclaw
Invoke-RestMethod -Uri "http://127.0.0.1:8080/health" -TimeoutSec 5
```

回滚时停止服务，保存失败 JAR，把 `solonclaw.jar.previous` 复制回 `solonclaw.jar`，再启动并检查健康状态。

## 7. SQLite 与工作区备份

### 7.1 推荐的停机一致性备份

SQLite 使用 WAL。不要在服务运行时只复制 `state.db`；这可能漏掉 `state.db-wal` 中尚未检查点的数据。最稳妥的生产备份是停止服务并归档整个工作区。

Linux systemd 示例：

```bash
set -euo pipefail
INSTALL_DIR="${SOLONCLAW_HOME:-$HOME/.solonclaw}"
BACKUP_ROOT="$INSTALL_DIR/backups"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$BACKUP_ROOT/$STAMP"

umask 077
mkdir -p "$BACKUP_DIR"
sudo systemctl stop solonclaw
tar -C "$INSTALL_DIR" -czf "$BACKUP_DIR/workspace.tar.gz" workspace
(cd "$BACKUP_DIR" && sha256sum workspace.tar.gz > SHA256SUMS)
sudo systemctl start solonclaw
curl -fsS http://127.0.0.1:8080/health
```

macOS 在归档前后使用 `launchctl unload/load`。Docker 使用 `docker compose stop/start solonclaw`，并从宿主机挂载目录归档。

Windows 示例：

```powershell
$InstallDir = if ($env:SOLONCLAW_HOME) {
  $env:SOLONCLAW_HOME
} else {
  Join-Path $env:USERPROFILE ".solonclaw"
}
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $InstallDir "backups\$Stamp"
$Nssm = Join-Path $InstallDir "nssm.exe"

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
& $Nssm stop solonclaw
Compress-Archive -Path (Join-Path $InstallDir "workspace") `
  -DestinationPath (Join-Path $BackupDir "workspace.zip")
& $Nssm start solonclaw
Invoke-RestMethod -Uri "http://127.0.0.1:8080/health" -TimeoutSec 5
```

### 7.2 使用 sqlite3 在线备份

无法停机时，可使用 SQLite CLI 的在线备份 API。它只备份一个数据库，不包含配置、技能、上下文、附件或其他 Profile。

```bash
WORKSPACE_DIR="${SOLONCLAW_WORKSPACE:-$HOME/.solonclaw/workspace}"
DB="$WORKSPACE_DIR/data/state.db"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$WORKSPACE_DIR/../backups/state-$STAMP.db"

umask 077
mkdir -p "$(dirname "$DEST")"
sqlite3 "$DB" ".timeout 5000" ".backup '$DEST'"
sqlite3 "$DEST" "PRAGMA integrity_check;"
```

完整列出所有 Profile 数据库：

```bash
find "$WORKSPACE_DIR" -type f -path '*/data/state.db' -print
```

每个备份文件都必须返回 `ok` 后才能标记为成功。不要把在线备份写回正在运行的数据库路径。

### 7.3 恢复

恢复会覆盖当前运行状态，必须先停服务并保留现场副本。Linux 示例：

```bash
set -euo pipefail
INSTALL_DIR="${SOLONCLAW_HOME:-$HOME/.solonclaw}"
BACKUP_ARCHIVE="/path/to/workspace.tar.gz"
STAMP="$(date +%Y%m%d-%H%M%S)"

test -f "$BACKUP_ARCHIVE"
(cd "$(dirname "$BACKUP_ARCHIVE")" && sha256sum -c SHA256SUMS)
sudo systemctl stop solonclaw
mv "$INSTALL_DIR/workspace" "$INSTALL_DIR/workspace.before-restore-$STAMP"
tar -C "$INSTALL_DIR" -xzf "$BACKUP_ARCHIVE"
sqlite3 "$INSTALL_DIR/workspace/data/state.db" "PRAGMA integrity_check;"
sudo systemctl start solonclaw
curl -fsS http://127.0.0.1:8080/health
```

Docker 恢复后确认宿主机工作区归 UID/GID `10001` 所有。Windows 使用 NSSM 停止服务，把当前 `workspace` 移到带时间戳的目录，再用 `Expand-Archive` 解压备份。

恢复后至少验证：

- `/health` 与 `/health/detailed`。
- Dashboard 能登录且配置未丢失。
- 默认 Profile 和命名 Profile 的会话可读。
- 模型健康检查成功。
- 已启用渠道重新连接并能完成一条真实收发。

## 8. 上线验收

```bash
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/health/detailed
```

健康接口只证明 HTTP 进程可响应，不代表模型、SQLite 和渠道都正常。还应在 Dashboard 执行 doctor，查看 `workspace/logs/errors.log` 与 `gateway.log`，并完成真实模型调用和渠道收发。

验收失败时不要连续覆盖 JAR、工作区或备份。保留当前日志、版本、JAR 校验值、镜像摘要和失败时间，按 [运维 Runbook](runbook.md) 处理。
