# 腾讯元宝渠道

当前实现通过腾讯元宝 WebSocket 接收入站事件，并使用 REST API 发送文本和附件。适配器代码已具备连接与消息边界，但平台凭据发放、协议开放范围和目标账号权限仍需实测，因此当前状态是“已实现，持续联调”。

## 平台准备

1. 从腾讯元宝机器人平台取得 App ID、App Secret 和可选 Bot ID。
2. 确认机器人已发布到目标账号，并允许目标用户或群聊访问。
3. 确认运行主机可以访问元宝 WebSocket 和 REST API 域名。

## 最小配置

```yaml
solonclaw:
  channels:
    yuanbao:
      enabled: true
      appId: your-app-id
      appSecret: your-app-secret
      botId: your-bot-id
      dmPolicy: pairing
      groupPolicy: allowlist
      groupAllowedUsers:
        - allowed-user-id
      toolProgress: off
```

`appId` 和 `appSecret` 是连接必填项；平台要求指定机器人时同时填写 `botId`。通常沿用内置 `apiDomain` 和 `websocketUrl`，只有平台提供不同环境地址时才覆盖。

## 验证与排查

- 在 Dashboard 渠道页确认连接模式为 `websocket`，状态从 `connecting` 进入已连接。
- `missing_config` 表示缺少 App ID 或 App Secret；签名或权限错误需要同时核对时间、凭据和机器人发布状态。
- 消息被忽略时检查私聊/群聊策略、配对状态和允许名单。
- 连接、签名或消息协议错误以 Dashboard doctor 和日志中的 `[YUANBAO]` 记录为准。
