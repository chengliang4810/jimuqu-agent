package com.jimuqu.claw.channel;

import com.jimuqu.claw.config.ClawProperties;

public class WecomChannelAdapter extends AbstractWebhookChannelAdapter {
    public WecomChannelAdapter(ClawProperties.ChannelProperties properties) {
        super("wecom", properties);
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
                "external_userid",
                "conversation_id",
                "conversationId");
    }

    @Override
    protected String[] userIdPaths() {
        return append(super.userIdPaths(),
                "userid",
                "sender.userid",
                "sender.user_id");
    }
}
