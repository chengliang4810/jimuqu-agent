package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.gateway.DefaultGatewayService;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

@Controller
public class GatewayController {
    private final DefaultGatewayService gatewayService;

    public GatewayController(DefaultGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Mapping("/api/gateway/message")
    public GatewayReply message(Context context) throws Exception {
        GatewayMessage message = ONode.deserialize(context.body(), GatewayMessage.class);
        return gatewayService.handle(message);
    }
}
