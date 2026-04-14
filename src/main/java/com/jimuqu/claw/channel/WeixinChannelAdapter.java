package com.jimuqu.claw.channel;

import com.jimuqu.claw.config.ClawProperties;

public class WeixinChannelAdapter extends AbstractWebhookChannelAdapter {
    public WeixinChannelAdapter(ClawProperties.ChannelProperties properties) {
        super("weixin", properties);
    }

    @Override
    protected String[] messageIdPaths() {
        return append(super.messageIdPaths(),
                "msgid",
                "msg_id");
    }

    @Override
    protected String[] chatIdPaths() {
        return append(super.chatIdPaths(),
                "from_user",
                "fromUser");
    }

    @Override
    protected String[] userIdPaths() {
        return append(super.userIdPaths(),
                "from_user",
                "fromUser");
    }
}
