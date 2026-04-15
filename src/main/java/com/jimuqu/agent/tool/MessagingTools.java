package com.jimuqu.agent.tool;

import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.DeliveryService;
import com.jimuqu.agent.core.PlatformType;
import org.noear.solon.ai.annotation.ToolMapping;

public class MessagingTools {
    private final DeliveryService deliveryService;
    private final String sourceKey;

    public MessagingTools(DeliveryService deliveryService, String sourceKey) {
        this.deliveryService = deliveryService;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "send_message", description = "Send a text message to a target platform and chat. If platform or chatId is empty, send back to the current source.")
    public String sendMessage(String platform, String chatId, String text) throws Exception {
        String[] parts = sourceKey.split(":", 3);
        PlatformType targetPlatform = PlatformType.fromName(platform == null || platform.trim().length() == 0 ? parts[0] : platform);
        String targetChatId = chatId == null || chatId.trim().length() == 0 ? parts[1] : chatId;
        String targetUserId = parts.length > 2 ? parts[2] : null;
        deliveryService.deliver(new DeliveryRequest(targetPlatform, targetChatId, targetUserId, null, text));
        return "Message delivered";
    }
}
