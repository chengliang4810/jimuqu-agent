package com.jimuqu.agent.tool.runtime;

import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.enums.PlatformType;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;

/**
 * MessagingTools 实现。
 */
public class MessagingTools {
    private final DeliveryService deliveryService;
    private final String sourceKey;

    public MessagingTools(DeliveryService deliveryService, String sourceKey) {
        this.deliveryService = deliveryService;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "send_message", description = "Send a text message to a target platform and chat. If platform or chatId is empty, send back to the current source.")
    public String sendMessage(@Param(name = "platform", description = "目标平台名", required = false) String platform,
                              @Param(name = "chatId", description = "目标聊天 ID", required = false) String chatId,
                              @Param(name = "text", description = "要发送的文本") String text) throws Exception {
        String[] parts = sourceKey.split(":", 3);
        PlatformType targetPlatform = PlatformType.fromName(platform == null || platform.trim().length() == 0 ? parts[0] : platform);
        String targetChatId = chatId == null || chatId.trim().length() == 0 ? parts[1] : chatId;
        String targetUserId = parts.length > 2 ? parts[2] : null;
        deliveryService.deliver(new DeliveryRequest(targetPlatform, targetChatId, targetUserId, null, null, text));
        return "Message delivered";
    }
}
