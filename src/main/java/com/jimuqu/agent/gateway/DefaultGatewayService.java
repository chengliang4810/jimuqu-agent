package com.jimuqu.agent.gateway;

import com.jimuqu.agent.core.CommandService;
import com.jimuqu.agent.core.ConversationOrchestrator;
import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.DeliveryService;
import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;

public class DefaultGatewayService {
    private final CommandService commandService;
    private final ConversationOrchestrator conversationOrchestrator;
    private final DeliveryService deliveryService;

    public DefaultGatewayService(CommandService commandService,
                                 ConversationOrchestrator conversationOrchestrator,
                                 DeliveryService deliveryService) {
        this.commandService = commandService;
        this.conversationOrchestrator = conversationOrchestrator;
        this.deliveryService = deliveryService;
    }

    public GatewayReply handle(GatewayMessage message) throws Exception {
        GatewayReply reply;
        String text = message.getText() == null ? "" : message.getText().trim();
        if (text.startsWith("/")) {
            reply = commandService.handle(message, text);
            reply.setCommandHandled(true);
        } else {
            reply = conversationOrchestrator.handleIncoming(message);
        }

        if (reply != null && reply.getContent() != null && reply.getContent().trim().length() > 0) {
            deliveryService.deliver(new DeliveryRequest(message.getPlatform(), message.getChatId(), message.getUserId(), message.getThreadId(), reply.getContent()));
        }

        return reply;
    }
}
