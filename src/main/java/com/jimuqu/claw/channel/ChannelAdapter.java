package com.jimuqu.claw.channel;

import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import com.jimuqu.claw.channel.model.ChannelOutboundMessage;

public interface ChannelAdapter {
    String platform();

    boolean enabled();

    ChannelInboundMessage parseInbound(String body);

    Object sendMessage(ReplyRoute route, ChannelOutboundMessage outboundMessage);
}
