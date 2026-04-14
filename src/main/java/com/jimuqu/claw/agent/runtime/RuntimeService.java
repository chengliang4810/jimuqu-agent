package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;

public interface RuntimeService {
    RunRecord handleInbound(ChannelInboundMessage inboundMessage);

    RunRecord handleRequest(RunRequest request);
}
