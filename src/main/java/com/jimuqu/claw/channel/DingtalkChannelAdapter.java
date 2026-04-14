package com.jimuqu.claw.channel;

import com.jimuqu.claw.config.ClawProperties;

public class DingtalkChannelAdapter extends AbstractWebhookChannelAdapter {
    public DingtalkChannelAdapter(ClawProperties.ChannelProperties properties) {
        super("dingtalk", properties);
    }

    @Override
    protected String[] chatIdPaths() {
        return append(super.chatIdPaths(),
                "conversationId",
                "conversation_id",
                "event.conversationId",
                "event.conversation_id");
    }

    @Override
    protected String[] userIdPaths() {
        return append(super.userIdPaths(),
                "senderStaffId",
                "sender_staff_id",
                "sender.staffId",
                "sender.staff_id");
    }

    @Override
    protected String[] textPaths() {
        return append(super.textPaths(),
                "text.content",
                "msg_param.text",
                "msgParam.text");
    }
}
