# Solon Claw

English | [简体中文](README.md)

Solon Claw is a single-instance Agent service built with Java, Solon, and Solon AI. The project aims to align with the core behavior and capabilities of an external reference Agent in the Java / Solon ecosystem, with a focus on the Agent loop, tool calling, sessions, memory, skills, scheduled tasks, Chinese messaging channels, and a dashboard-first setup and diagnostics experience.

The public product name is **Solon Claw**. Commands, Maven artifacts, Docker images, and configuration namespaces consistently use `solonclaw`. The Java package `com.jimuqu.solon.claw` is only a source-code namespace, not a separate product or configuration name.

> The project is under active development. APIs and configuration keys may change as the implementation evolves. Feedback and contributions are welcome.

## Features

- **Agent core loop**: multi-turn sessions, streaming/non-streaming model calls, tool calls, context compression, retry, rollback, and session search.
- **Model protocols**: supports common interfaces such as `openai`, `openai-responses`, `ollama`, `gemini`, and `anthropic`.
- **Tool system**: built-in tools for file operations, search, patching, Shell/Python/JavaScript execution, Memory, scheduled jobs, web search/fetch, and message delivery.
- **Chinese messaging channels**: focuses on Feishu, DingTalk, WeCom, Weixin, QQBot, and Yuanbao; websocket / stream first, with Weixin iLink long-poll retained.
- **Dashboard-first operations**: status, sessions, workspace configuration, channel doctor, logs, skills, and scheduled jobs.
- **Persistence**: SQLite-backed storage for sessions, policies, scheduled jobs, and channel states.
- **Skills and memory**: local skills, Skills Hub imports, long-term memory, user context, and context file collaboration.
- **Deployment**: supports `java -jar` and Docker / Docker Compose single-instance deployments.

## Tech Stack

- Java source compatibility: 1.8
- Build: Maven, Node.js/npm for the Dashboard frontend
- Web framework: Solon
- AI orchestration: Solon AI, Solon AI Agent, Solon AI Skills
- JSON: Snack4
- Utilities: Hutool
- Database: SQLite
- Frontend: Vue / Vite
- Container: Docker, Docker Compose

## Quick Start

### Requirements

- JDK 8+ (JDK 17 recommended)
- Maven 3.9+
- Node.js 24+ and npm
- Network access to your target LLM provider

### Clone and Build

```bash
git clone https://github.com/chengliang4810/solonclaw.git
cd solonclaw
mvn -DskipTests package
```

Maven runs `npm install` and `npm run build` in the `web` directory during `generate-resources` by default. To build only the backend:

```bash
mvn -DskipTests -Dskip.web.build=true package
```

### Run

```bash
java -jar target/solonclaw-0.0.1.jar
```

The default endpoint is:

```text
http://127.0.0.1:8080
```

On first Dashboard open, enter a new access token to initialize the local instance; the page writes it to `workspace/config.yml` through the localhost-only bootstrap endpoint. To pin the token before startup, pass:

```bash
java -Dsolonclaw.dashboard.accessToken=your-token -jar target/solonclaw-0.0.1.jar
```

On startup, the service creates a local `workspace/` directory for configuration, SQLite data, cache, logs, skills, and context files. Workspace children are derived by the program: `context/`, `skills/`, `cache/`, `logs/`, and `data/state.db`.

### Docker Compose

```bash
docker compose up -d
```

The default Compose file mounts local `./workspace` to `/app/workspace` inside the container so workspace data and the online-updatable `solonclaw.jar` persist. On first start, `/app/docker-entrypoint.sh` copies the bundled JAR and then runs the workspace JAR. The image includes `openssh-client`, so `ssh`, `scp`, and `sftp` are available inside the container.

After the container starts, run `docker exec -it solonclaw solonclaw` to open the full TUI attached to the existing service in that container.

If you are migrating from an older non-root image, fixed UID/GID ownership for the host workspace directory is no longer part of the default deployment requirement. Custom deployment scripts can remove the previous user-mapping logic.

## Configuration

Default configuration lives in:

```text
src/main/resources/app.yml
```

Model providers are managed by the workspace configuration file and the Dashboard. The workspace file is created at:

```text
workspace/config.yml
```

`workspace/config.yml` does not configure its own directory. The workspace directory is decided by startup-level `solonclaw.workspace` and defaults to `workspace/` under the current working directory.

See `config.example.yml` at the repository root for the full example. Startup also syncs this file to `workspace/config.example.yml` as a read-only reference; the effective configuration remains `workspace/config.yml`.

Recommended model configuration structure:

```yaml
providers:
  default:
    name: DefaultProvider
    baseUrl: https://api.openai.com
    apiKey: ""
    defaultModel: gpt-5.4
    dialect: openai
model:
  providerKey: default
  default: "gpt-5.4"
fallbackProviders: []
security:
  allowPrivateUrls: false
  websiteBlocklist:
    enabled: false
    domains: []
    sharedFiles: []
  fileGuardrailMode: strict
  urlGuardrailMode: strict
  guardrailMode: approval
  guardrailCronMode: strict
  guardrailCronScope: job
  hardlineAllowlist: []
approvals:
  subagentAutoApprove: false
  timeoutSeconds: 180
  modelProvider: ""
  model: ""
solonclaw:
  workspace: ./workspace
  dashboard:
    accessToken: ""
```

Common workspace settings:

| Key | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP server port |
| `solonclaw.workspace` | `./workspace` | Agent workspace directory; relative paths resolve from the running Jar directory, and reads, writes, and ordinary commands inside it are free |
| `providers.<key>.baseUrl` | - | Model service base URL |
| `providers.<key>.apiKey` | - | Model service API key |
| `providers.<key>.defaultModel` | - | Default model for the provider |
| `providers.<key>.dialect` | `openai` | Protocol dialect |
| `model.providerKey` | `default` | Active default provider |
| `model.default` | empty | Global model override; when empty, provider `defaultModel` is used |
| `solonclaw.llm.stream` | `true` | Enables streaming output |
| `solonclaw.llm.reasoningEffort` | `medium` | Default reasoning effort |
| `solonclaw.scheduler.enabled` | `true` | Enables scheduled jobs |
| `solonclaw.browser.rewriteLoopbackUrls` | `false` | Rewrites loopback URLs for browser tools running inside containers |
| `security.tirithEnabled` | `true` | Enables Tirith command content scanning |
| `security.tirithFailOpen` | `true` | Allows execution when Tirith is unavailable or times out; set `false` to fail closed |
| `security.allowPrivateUrls` | `false` | Allows URL tools to reach private addresses; cloud metadata remains blocked |
| `security.websiteBlocklist.enabled` | `false` | Enables URL-tool domain blocking |
| `security.websiteBlocklist.domains` | empty | Blocked domains, including exact names and `*.example.com` wildcards |
| `security.websiteBlocklist.sharedFiles` | empty | Shared blocklist files; relative paths resolve from the workspace |
| `security.fileGuardrailMode` | `strict` | File path preflight mode: `strict`, `bypass` |
| `security.urlGuardrailMode` | `strict` | URL preflight mode: `strict`, `bypass` |
| `security.guardrailMode` | `approval` | Agent tool safety mode: `bypass`, `approval`, `smart` |
| `security.guardrailCronMode` | `strict` | Scheduled-job safety mode: `strict`, `approval`, `bypass`, `approve` |
| `security.guardrailCronScope` | `job` | Scheduled-job approval memory scope: `job`, `session`, `global` |
| `security.hardlineAllowlist` | empty | Explicitly allowlisted hardline categories; none are allowed by default |
| `approvals.subagentAutoApprove` | `false` | Automatically approves one approvable dangerous command for sub-agents |
| `approvals.timeoutSeconds` | `180` | Shared approval timeout in seconds, including messaging channels |
| `approvals.modelProvider` | empty | Provider dedicated to smart approvals; set together with `approvals.model` |
| `approvals.model` | empty | Model dedicated to smart approvals; both empty values reuse the current Profile model |
| `solonclaw.terminal.credentialFiles` | empty | Workspace-relative credential files available to isolated execution |
| `solonclaw.terminal.envPassthrough` | empty | Third-party environment variables allowed for local subprocesses |
| `solonclaw.terminal.sudoPassword` | empty | Optional sudo password for `sudo -S` rewriting; can also be supplied with `SOLONCLAW_SUDO_PASSWORD` |
| `solonclaw.trace.retentionDays` | `14` | Run trace retention in days |
| `solonclaw.trace.maxAttempts` | `2` | Maximum outer attempts per run |
| `solonclaw.task.busyPolicy` | `interrupt` | Policy for new messages while a session is already running |
| `solonclaw.task.subagentMaxConcurrency` | `3` | Maximum sub-agent concurrency |
| `solonclaw.task.subagentMaxDepth` | `1` | Maximum sub-agent spawn depth |
| `solonclaw.task.toolOutputInlineLimit` | `50000` | Stores oversized single tool outputs in cache and returns only a preview |
| `solonclaw.task.mediaCacheTtlHours` | `168` | Channel media cache TTL in hours |
| `solonclaw.skills.externalDirs` | empty | Additional read-only skill directories |
| `solonclaw.skills.templateVars` | `true` | Enables SKILL.md template variable replacement |
| `solonclaw.gateway.filterSilenceNarration` | `true` | Drops short silence narration before channel delivery |
| `solonclaw.web.searchBackend` | `ddgs` | Web search backend: built-in `ddgs` or `brave-free` |
| `solonclaw.pricing.prices` | empty | Model pricing configuration; empty means token-only usage without cost calculation |

Prefer the Dashboard for provider and default-model management, or edit `workspace/config.yml` directly. Keep secrets out of Git.

## Messaging Channels

Supported and prioritized channels:

| Channel | Prefix | Inbound mode | Status |
| --- | --- | --- | --- |
| [Feishu](docs/feishu.md) | `solonclaw.channels.feishu.*` | websocket / platform capabilities | Implemented, integration ongoing |
| [DingTalk](docs/dingtalk.md) | `solonclaw.channels.dingtalk.*` | stream mode | Supported |
| [WeCom](docs/wecom.md) | `solonclaw.channels.wecom.*` | websocket / platform capabilities | Implemented, integration ongoing |
| [Weixin](docs/weixin.md) | `solonclaw.channels.weixin.*` | iLink long-poll | Implemented, integration ongoing |
| [QQBot](docs/qqbot.md) | `solonclaw.channels.qqbot.*` | websocket / REST | Implemented, integration ongoing |
| [Yuanbao](docs/yuanbao.md) | `solonclaw.channels.yuanbao.*` | websocket / REST | Implemented, integration ongoing |

The Dashboard includes channel status and doctor endpoints. Prefer the Dashboard for setup, diagnostics, and troubleshooting.

## Slash Commands

Common in-conversation commands:

- `/new`: start a new session
- `/retry`: retry the previous turn
- `/undo`: undo the previous turn
- `/branch`: branch from the current session
- `/resume`: resume a session
- `/status`: show workspace status
- `/usage`: show token usage
- `/model`: inspect or switch models
- `/tools`: inspect tool state
- `/skills`: manage skills
- `/cron`: manage scheduled jobs
- `/pairing`: channel user pairing and approvals
- `/approve` / `/deny`: dangerous command approval

## API Overview

Main HTTP endpoints:

- `GET /api/status`: workspace status
- `POST /api/gateway/message`: signed gateway message injection
- `GET /api/diagnostics/doctor`: channel diagnostics
- `GET /api/sessions`: session list
- `POST /api/chat/runs`: Dashboard chat run
- `GET /api/config`: read configuration
- `GET /api/workspace-config`: workspace-backed settings

Dashboard APIs require a session token by default. Gateway injection uses HMAC signature headers.

## Project Layout

The list below covers every top-level package that currently contains Java sources. Nested packages under `core/`, `gateway/`, `profile/`, `skillhub/`, `storage/`, `support/`, `tool/`, and `web/` are intentionally omitted for readability.

```text
src/main/java/com/jimuqu/solon/claw/
├── agent/          # Agent profiles
├── bootstrap/      # Solon startup and bean wiring
├── command/        # Host command registration and execution
├── config/         # Config-file loading, workspace overrides, path normalization
├── context/        # AGENTS / MEMORY / USER / Skills context
├── core/           # Domain models, repository interfaces, service interfaces
├── engine/         # Agent loop, compression, delegation
├── gateway/        # Messaging channels, auth, delivery, workspace refresh
├── goal/           # Persistent goals and execution budgets
├── llm/            # Model protocol adapters and Solon AI integration
├── media/          # Media types, caching, and processing boundaries
├── pricing/        # Model pricing and cost calculation
├── proactive/      # Conversation- and memory-driven proactive reminders
├── profile/        # Profile lifecycle, isolation, and task delegation
├── provider/       # Search, browser, and media provider boundaries
├── scheduler/      # Cron and heartbeat scheduling
├── skillhub/       # Skills Hub, imports, guardrails, sources
├── storage/        # SQLite repository implementations
├── support/        # Workspace support utilities
├── tool/           # Built-in tool registry and implementations
├── tui/            # TUI runtime protocol and controller boundary
├── usage/          # Token usage backfill and accounting
└── web/            # Dashboard backend services and controllers
```

## Release Guardrails

Before normal commits, scan only the current work tree and new commits on the current branch relative to the default branch:

```bash
python3 scripts/check-project-naming.py --check-git-commit-subjects --check-git-object-text --check-current-branch-range
```

Scanning all Git refs is only for manual history audits, not for deciding whether the current source tree or release range is clean.

## Testing

Run the full Maven test lifecycle, including the frontend-bound build steps:

```bash
mvn test
```

Compile only the backend:

```bash
mvn -DskipTests -Dskip.web.build=true compile
```

Run selected tests:

```bash
mvn "-Dtest=DashboardControllerHttpTest" test
```

> In Windows PowerShell, quote `-Dtest=...` when using comma-separated test names.

## Contributing

Issues and pull requests are welcome. Before contributing, please check existing issues, run relevant tests, and describe the motivation, main implementation details, and verification scope in your pull request.

## License

This project is licensed under the [MIT License](LICENSE).
