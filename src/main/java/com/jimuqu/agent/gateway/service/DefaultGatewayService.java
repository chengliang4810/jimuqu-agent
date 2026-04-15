package com.jimuqu.agent.gateway.service;

import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.support.constants.GatewayCommandConstants;

/**
 * 网关主入口服务，负责把消息分流到授权、命令和对话主链。
 */
public class DefaultGatewayService {
    /**
     * 命令服务。
     */
    private final CommandService commandService;

    /**
     * 对话编排器。
     */
    private final ConversationOrchestrator conversationOrchestrator;

    /**
     * 渠道投递服务。
     */
    private final DeliveryService deliveryService;

    /**
     * 授权服务。
     */
    private final GatewayAuthorizationService gatewayAuthorizationService;

    /**
     * 构造网关服务。
     */
    public DefaultGatewayService(CommandService commandService,
                                 ConversationOrchestrator conversationOrchestrator,
                                 DeliveryService deliveryService,
                                 GatewayAuthorizationService gatewayAuthorizationService) {
        this.commandService = commandService;
        this.conversationOrchestrator = conversationOrchestrator;
        this.deliveryService = deliveryService;
        this.gatewayAuthorizationService = gatewayAuthorizationService;
    }

    /**
     * 处理单条统一网关消息。
     *
     * @param message 渠道统一消息
     * @return 网关处理结果
     */
    public GatewayReply handle(GatewayMessage message) throws Exception {
        GatewayReply preAuth = gatewayAuthorizationService.preAuthorize(message);
        if (preAuth != null) {
            if (preAuth.getContent() != null && preAuth.getContent().trim().length() > 0) {
                deliveryService.deliver(new DeliveryRequest(
                        message.getPlatform(),
                        message.getChatId(),
                        message.getUserId(),
                        message.getThreadId(),
                        preAuth.getContent()
                ));
            }
            return preAuth;
        }

        GatewayReply reply;
        String text = message.getText() == null ? "" : message.getText().trim();
        if (!gatewayAuthorizationService.isAuthorized(message)) {
            return null;
        }
        if (text.startsWith(GatewayCommandConstants.COMMAND_PREFIX)) {
            reply = commandService.handle(message, text);
            reply.setCommandHandled(true);
        } else {
            reply = conversationOrchestrator.handleIncoming(message);
        }

        if (reply != null && reply.getContent() != null && reply.getContent().trim().length() > 0) {
            deliveryService.deliver(new DeliveryRequest(
                    message.getPlatform(),
                    message.getChatId(),
                    message.getUserId(),
                    message.getThreadId(),
                    reply.getContent()
            ));
        }

        return reply;
    }
}
