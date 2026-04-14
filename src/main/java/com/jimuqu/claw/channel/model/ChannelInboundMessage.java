package com.jimuqu.claw.channel.model;

import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelInboundMessage {
    private String messageId;
    private String text;
    private SessionContext sessionContext;
    private ReplyRoute replyRoute;
    private Instant receivedAt;
}
