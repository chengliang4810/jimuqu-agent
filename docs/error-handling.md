# 错误处理

本文说明 Solon Claw 的异常分层、客户端响应、日志脱敏和模型调用重试规则。目标是让调用方获得稳定错误语义，同时把凭据、内部路径、异常类名和堆栈限制在可信服务端边界内。

## 分层原则

错误按发生位置处理，不把底层异常直接穿透所有层：

| 层级            | 表达方式                                    | 处理责任                                             |
| --------------- | ------------------------------------------- | ---------------------------------------------------- |
| 输入与状态校验  | `IllegalArgumentException` 或稳定业务错误码 | Controller 映射为 400/404/409，不重试                |
| 领域控制流      | `AgentRunCancelledException` 等明确类型     | 标记取消或暂停，不当作系统故障重试                   |
| 跨 Profile 调用 | `DashboardProfileGatewayException`          | 保留安全 HTTP 状态和稳定错误码，隐藏目标网关内部异常 |
| Profile 解析    | `DashboardProfileNotFoundException`         | 映射为 Profile 不存在，不泄露本地路径                |
| 模型与网络适配  | 原始异常交给 `LlmErrorClassifier`           | 决定同提供方重试、备用提供方、上下文压缩或停止       |
| HTTP 最外层     | `DashboardResponse` 或协议专用错误          | 生成安全客户端响应，内部细节只写服务端日志           |

`GoalJudgeUnparseableException` 是目标裁决的特殊控制信号：模型确实返回内容但 JSON 不可解析时累计连续失败；网络或超时继续按 fail-open 处理，解析失败达到上限后自动暂停目标，避免无意义消耗。

不要用异常消息判断业务类型。需要跨层传递的状态、错误码或重试属性应放在明确类型或结构化字段中。

## Dashboard HTTP 响应

多数 Dashboard 成功响应：

```json
{
  "success": true,
  "data": {}
}
```

受控错误响应：

```json
{
  "success": false,
  "code": "PROFILE_NOT_FOUND",
  "error": "可安全展示的消息"
}
```

处理规则：

- 4xx 可以返回经过校验、脱敏且对用户有行动意义的消息。
- 5xx 必须调用接收 `Throwable` 的 `DashboardResponse.error(...)` 路径；客户端统一看到 `请求处理失败 / Request failed`。
- 5xx 日志只记录 HTTP 状态、稳定错误码和异常类型，不把异常消息或堆栈放进响应。
- 客户端同时依据 HTTP 状态和 `code` 处理；不要解析 `error` 文案。
- SSE、文件下载、TUI JSON-RPC 和 `GatewayReply` 使用各自协议，但仍遵守“不把原始内部异常返回客户端”的规则。

常用 HTTP 语义：

| 状态 | 含义                                    |
| ---- | --------------------------------------- |
| 400  | 请求体、参数或配置值非法                |
| 401  | Dashboard 身份、网关签名或短会话无效    |
| 403  | Origin、权限或安全策略拒绝              |
| 404  | Profile、会话、Run 或资源不存在         |
| 409  | 当前状态冲突、重复 nonce 或并发操作冲突 |
| 413  | 请求体或上传内容超过限制                |
| 429  | 请求频率超过限制                        |
| 503  | 依赖、审计或受控运行能力暂不可用        |

## 错误文本脱敏

`ErrorTextSupport` 提供三种边界：

- `safeError(Throwable)`：取异常消息或简单类名，再经 `SecretRedactor` 脱敏并截断到 1,000 字符。
- `summaryWithType(Throwable)`：用于内部 debug 诊断，输出简单类型和最多 500 字符的脱敏摘要。
- `typeOnly(Throwable)`：高敏感路径只记录异常类型，不记录消息。

`SecretRedactor` 会处理 Bearer、常见 API Key/Token/Secret/Password 字段、环境变量赋值、JSON/YAML 密钥字段、私钥、JWT、数据库连接凭据、URL user-info、敏感查询参数和已知令牌前缀，并移除危险显示控制字符。

使用限制：

- `safeError` 只是脱敏，不会把任意异常消息变成适合公开的产品文案。未知 5xx 仍必须返回固定消息。
- 不记录请求体、Authorization、Cookie、密钥揭示值或完整上游响应。
- URL 日志使用 `SecretRedactor.maskUrl`，不能只删除单个查询参数。
- 高敏感操作用 request ID 或审计 event ID 对账，不在日志中重复业务秘密。
- 日志参数应优先使用稳定阶段、业务 ID、错误码和异常类型。

## LLM 错误分类

`LlmErrorClassifier` 从异常链消息和常见 HTTP 状态中生成统一决策：

| 原因                       | 同提供方重试 | 备用提供方/模型 | 先压缩上下文 | 说明                               |
| -------------------------- | ------------ | --------------- | ------------ | ---------------------------------- |
| `AUTH`                     | 否           | 是              | 否           | 401/403、无效或撤销的密钥          |
| `BILLING`                  | 否           | 是              | 否           | 402、余额或套餐不足                |
| `RATE_LIMIT`               | 是           | 是，立即切换    | 否           | 429、频率/并发/周期窗口限制        |
| `OVERLOADED`               | 最多额外一次 | 是              | 否           | 503/529 或过载文本                 |
| `SERVER_ERROR`             | 是           | 是              | 否           | 500/502/504                        |
| `TIMEOUT`                  | 最多额外一次 | 是              | 否           | 超时、断连和传输错误               |
| `CERTIFICATE_VERIFICATION` | 否           | 否              | 否           | 证书链、过期或主机名校验失败       |
| `CONTEXT_OVERFLOW`         | 是           | 否              | 是           | 上下文窗口超限                     |
| `PAYLOAD_TOO_LARGE`        | 是           | 否              | 是           | 413 或请求体/附件过大              |
| `MODEL_NOT_FOUND`          | 否           | 是              | 否           | 404、模型名称不存在或不可用        |
| `PROVIDER_POLICY_BLOCKED`  | 否           | 否              | 否           | 提供方账户数据策略阻断             |
| `CONTENT_POLICY_BLOCKED`   | 否           | 是              | 否           | 内容安全策略拒绝当前提示词         |
| `UNKNOWN`                  | 否           | 是              | 否           | 无法可靠分类，避免盲目重复同一请求 |

实际尝试次数仍受当前会话的 retry 配置、备用提供方列表和外层 Run attempt 上限约束。限流与计费错误会跳过当前提供方剩余重试；TIMEOUT 和 OVERLOADED 为避免长时间卡住，当前提供方最多额外尝试一次。

上下文溢出与请求体过大只有在编排器能够压缩或减少载荷时才应再次调用；不能原样重放同一请求。

## 中断与取消

取消不是普通失败：

- `AgentRunCancelledException` 表示用户或控制面明确停止当前运行。
- 捕获 `InterruptedException` 时先恢复线程中断标志，再停止当前工作；不能吞掉后继续重试。
- 不用 `catch (Throwable)` 把 `Error` 或中断转换成 `null`。只有进程/协议最外层可以做兜底捕获，并必须保留取消和中断语义。

## 排查流程

1. 先记录 HTTP 状态、稳定错误码、request ID、Profile、session ID 或 run ID。
2. 在 Dashboard 日志或服务日志中按这些标识定位同一请求。
3. 对模型失败查看分类原因、状态码、`retryable`、`shouldFallback` 和 `shouldCompress` 元数据。
4. 确认是否已切换备用提供方、是否执行上下文压缩、是否达到 Run attempt 上限。
5. 只有在脱敏后仍不足以定位时，才在受控本机环境提高日志级别；不要在生产响应中临时输出原始异常。
