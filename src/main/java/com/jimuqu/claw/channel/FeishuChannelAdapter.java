package com.jimuqu.claw.channel;

import com.jimuqu.claw.config.ClawProperties;

public class FeishuChannelAdapter extends AbstractWebhookChannelAdapter {
    public FeishuChannelAdapter(ClawProperties.ChannelProperties properties) {
        super("feishu", properties);
    }

    @Override
    protected String[] messageIdPaths() {
        return append(super.messageIdPaths(),
                "event.message.message_id",
                "header.event_id");
    }

    @Override
    protected String[] chatIdPaths() {
        return append(super.chatIdPaths(),
                "event.message.chat_id");
    }

    @Override
    protected String[] threadIdPaths() {
        return append(super.threadIdPaths(),
                "event.message.thread_id");
    }

    @Override
    protected String[] userIdPaths() {
        return append(super.userIdPaths(),
                "open_id",
                "sender_id.open_id",
                "event.sender.sender_id.open_id",
                "event.sender.sender_id.user_id");
    }
}
