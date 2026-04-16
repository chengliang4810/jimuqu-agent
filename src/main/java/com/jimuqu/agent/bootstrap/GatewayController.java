package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.gateway.service.DefaultGatewayService;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

/**
 * HTTP 网关入口，主要用于内存网关和调试场景下的消息注入。
 */
@Controller
@RequiredArgsConstructor
public class GatewayController {
    /**
     * 网关服务。
     */
    private final DefaultGatewayService gatewayService;

    /**
     * 接收统一网关消息并转发到主处理链。
     *
     * @param context HTTP 上下文
     * @return 处理结果
     */
    @Mapping("/api/gateway/message")
    public GatewayReply message(Context context) throws Exception {
        GatewayMessage message = ONode.deserialize(context.body(), GatewayMessage.class);
        return gatewayService.handle(message);
    }
}
