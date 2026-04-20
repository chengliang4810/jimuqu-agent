package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.MessageAttachment;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.support.AttachmentCacheService;
import lombok.RequiredArgsConstructor;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.snack4.ONode;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MessagingTools 实现。
 */
@RequiredArgsConstructor
public class MessagingTools {
    private final DeliveryService deliveryService;
    private final String sourceKey;
    private final AttachmentCacheService attachmentCacheService;

    @ToolMapping(name = "send_message", description = "Send a text message with optional local media attachments to a target platform and chat. If platform or chatId is empty, send back to the current source.")
    public String sendMessage(@Param(name = "platform", description = "目标平台名", required = false) String platform,
                              @Param(name = "chatId", description = "目标聊天 ID", required = false) String chatId,
                              @Param(name = "text", description = "要发送的文本") String text,
                              @Param(name = "mediaPaths", description = "可选本地附件路径数组", required = false) List<String> mediaPaths,
                              @Param(name = "channelExtrasJson", description = "可选渠道扩展 JSON；例如钉钉 AI card 所需参数", required = false) String channelExtrasJson) throws Exception {
        String[] parts = sourceKey.split(":", 3);
        PlatformType targetPlatform = PlatformType.fromName(platform == null || platform.trim().length() == 0 ? parts[0] : platform);
        String targetChatId = chatId == null || chatId.trim().length() == 0 ? parts[1] : chatId;
        String targetUserId = parts.length > 2 ? parts[2] : null;
        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(targetPlatform);
        request.setChatId(targetChatId);
        request.setUserId(targetUserId);
        request.setText(text);
        request.setAttachments(resolveAttachments(targetPlatform, mediaPaths));
        request.setChannelExtras(parseChannelExtras(channelExtrasJson));
        deliveryService.deliver(request);
        return "Message delivered";
    }

    private List<MessageAttachment> resolveAttachments(PlatformType platform, List<String> mediaPaths) {
        List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
        if (mediaPaths == null) {
            return attachments;
        }

        for (String rawPath : mediaPaths) {
            if (StrUtil.isBlank(rawPath)) {
                continue;
            }
            File file = new File(rawPath.trim());
            if (!file.isAbsolute()) {
                file = new File(System.getProperty("user.dir"), rawPath.trim());
            }
            attachments.add(attachmentCacheService.fromLocalFile(platform, file.getAbsoluteFile(), null, false, null));
        }
        return attachments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseChannelExtras(String channelExtrasJson) {
        if (StrUtil.isBlank(channelExtrasJson)) {
            return new LinkedHashMap<String, Object>();
        }
        Object parsed = ONode.deserialize(channelExtrasJson.trim(), Object.class);
        if (parsed instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) parsed);
        }
        throw new IllegalArgumentException("channelExtrasJson must be a JSON object");
    }
}
