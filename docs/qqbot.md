# QQBot 渠道

当前实现使用 QQ 机器人 REST API 获取令牌和发送消息，并通过 WebSocket 网关接收入站事件。适配器已覆盖文本、媒体、平台语音文本和部分内联键盘交互；开放平台事件与权限仍需按目标机器人联调。

## 平台准备

1. 在 QQ 开放平台创建机器人并取得 App ID 和 Client Secret。
2. 为机器人开通目标私聊、群聊或频道场景所需权限和事件。
3. 把机器人加入测试会话，并确认运行主机可访问 QQ API 与网关地址。

## 最小配置

```yaml
solonclaw:
  channels:
    qqbot:
      enabled: true
      appId: your-app-id
      clientSecret: your-client-secret
      dmPolicy: pairing
      groupPolicy: allowlist
      groupAllowedUsers:
        - allowed-user-id
      markdownSupport: true
      toolProgress: off
```

`appId` 和 `clientSecret` 是必填项。通常不需要设置 `websocketUrl`，适配器会从平台 `/gateway` 接口获取；只有平台明确提供固定地址时才覆盖它。

## 验证与排查

- Dashboard 渠道页应从 `connecting` 进入已连接；若显示 `REST ready; websocket gateway unavailable`，只能证明 REST 凭据有效，不能证明入站事件已可用。
- 401/403 类错误优先检查 App ID、Client Secret、机器人发布状态和场景权限。
- 收不到消息时检查事件权限、机器人是否加入会话以及私聊/群聊策略。
- 详细连接和协议错误以 Dashboard doctor 和日志中的 `[QQBOT]` 记录为准。
