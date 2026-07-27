# 企业微信渠道

当前实现使用企业微信智能机器人 WebSocket 接收入站消息并回传文本或附件，不需要部署 HTTP 回调。适配器已实现协议链路，但不同企业账号的智能机器人开放范围可能不同，上线前必须用目标企业实测权限。

## 平台准备

1. 在企业微信管理端创建支持 WebSocket 接入的智能机器人。
2. 记录机器人 Bot ID 和 Secret，并确认机器人可被目标成员或群聊使用。
3. 确认运行主机可以访问企业微信 WebSocket 服务。

## 最小配置

```yaml
solonclaw:
  channels:
    wecom:
      enabled: true
      botId: your-bot-id
      secret: your-bot-secret
      dmPolicy: pairing
      groupPolicy: allowlist
      groupAllowedUsers:
        - allowed-user-id
      toolProgress: off
```

`botId` 和 `secret` 是必填项。只有平台提供专用地址时才需要覆盖 `websocketUrl`。如需限制某个群内的成员，可在 `groups.<group-id>.allowFrom` 中配置成员 ID。

## 验证与排查

- 在 Dashboard 渠道页确认连接模式为 `websocket`，状态为已连接且订阅成功。
- `missing_config` 表示缺少 Bot ID 或 Secret；`error` 时先检查机器人是否启用、凭据是否匹配。
- 私聊或群聊被忽略时检查 `dmPolicy`、`groupPolicy`、配对状态和允许名单。
- 连接或发送失败的具体原因以 Dashboard doctor 和日志中的 `[WECOM]` 记录为准。
