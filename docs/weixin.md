# 微信渠道

当前实现通过微信 iLink long-poll 接收入站消息，支持文本、附件、引用媒体和输入状态。iLink 接口及账号可用性受平台控制，属于持续联调能力，不应在未经目标账号验证的情况下视为稳定生产通道。

## 推荐接入

优先在 Dashboard 渠道页启动微信扫码登录。扫码完成后，系统会把 `token` 和 `accountId` 写入当前 Profile 的运行配置；这两个值属于敏感凭据，不要提交到 Git 或粘贴到日志。

也可以手工写入已有凭据：

```yaml
solonclaw:
  channels:
    weixin:
      enabled: true
      token: your-ilink-token
      accountId: your-account-id
      dmPolicy: pairing
      groupPolicy: disabled
      toolProgress: off
```

`token` 和 `accountId` 是 long-poll 连接的必填项。默认禁用群聊；确认目标账号支持并完成准入策略配置后，再将 `groupPolicy` 改为 `open` 或 `allowlist`。

## 验证与排查

- Dashboard 扫码结果只有进入完成状态才表示凭据已落盘；待处理状态不是成功。
- 渠道页应显示连接模式 `long-poll`，并从 `connecting` 进入已连接状态。
- 登录失效或持续轮询失败时重新扫码，不要长期复用已撤销的 token。
- 消息被忽略时检查配对状态、私聊/群聊策略和允许名单；网络或协议错误以 Dashboard doctor 和日志中的 `[WEIXIN]` 记录为准。
