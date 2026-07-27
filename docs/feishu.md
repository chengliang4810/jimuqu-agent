# 飞书渠道

当前实现通过飞书长连接接收入站事件，不需要配置公网回调地址。适配器已覆盖文本、附件、群聊提及、卡片动作、表情回应和可选文档评论事件；平台权限和真实租户环境仍需在上线前联调验证。

## 平台准备

1. 在飞书开放平台创建企业自建应用并启用机器人能力。
2. 开启长连接事件接收，订阅机器人需要处理的消息事件；卡片、表情和文档评论能力按需授权。
3. 发布应用并把机器人加入目标单聊或群聊。
4. 记录 App ID 和 App Secret。也可以通过 Dashboard 的飞书扫码配置流程写入凭据。

## 最小配置

```yaml
solonclaw:
  channels:
    feishu:
      enabled: true
      appId: your-app-id
      appSecret: your-app-secret
      dmPolicy: pairing
      groupPolicy: allowlist
      groupAllowedUsers:
        - allowed-open-id
      toolProgress: off
```

`appId` 和 `appSecret` 是连接所需的必填项。`groupAllowedUsers` 使用飞书用户 Open ID；需要限制具体群时再配置 `allowedChats`。文档评论能力默认关闭，启用前还需配置对应权限和配对文件。

## 验证与排查

- 在 Dashboard 渠道页确认连接模式为 `websocket`，且状态不再是 `missing_config` 或 `error`。
- 私聊无响应时检查 `dmPolicy`、配对状态和用户允许名单。
- 群聊无响应时检查机器人是否入群、事件权限、群策略和用户/群白名单。
- 连接失败时核对凭据、应用是否已发布以及运行主机能否访问飞书开放平台；具体错误以 Dashboard doctor 和日志中的 `[FEISHU]` 记录为准。
