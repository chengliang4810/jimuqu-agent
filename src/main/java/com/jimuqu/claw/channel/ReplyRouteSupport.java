package com.jimuqu.claw.channel;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.runtime.model.ReplyRoute;

import java.util.Map;

public final class ReplyRouteSupport {
    private ReplyRouteSupport() {
    }

    public static ReplyRoute parse(String routeText) {
        if (StrUtil.isBlank(routeText)) {
            return null;
        }

        String[] parts = routeText.trim().split(":", 3);
        if (parts.length == 0 || StrUtil.isBlank(parts[0])) {
            return null;
        }

        return ReplyRoute.builder()
                .platform(parts[0].trim().toLowerCase())
                .chatId(parts.length > 1 ? StrUtil.emptyToNull(parts[1].trim()) : null)
                .threadId(parts.length > 2 ? StrUtil.emptyToNull(parts[2].trim()) : null)
                .build();
    }

    public static ReplyRoute fromMap(Map<String, Object> routeMap, String defaultPlatform) {
        if (routeMap == null || routeMap.isEmpty()) {
            return null;
        }

        String platform = ChannelPayloadSupport.string(routeMap, "platform");
        if (StrUtil.isBlank(platform)) {
            platform = defaultPlatform;
        }
        if (StrUtil.isBlank(platform)) {
            return null;
        }

        String chatId = ChannelPayloadSupport.string(routeMap, "chat_id", "chatId");
        String threadId = ChannelPayloadSupport.string(routeMap, "thread_id", "threadId");
        if (StrUtil.isBlank(chatId) && StrUtil.isBlank(threadId)) {
            return ReplyRoute.builder()
                    .platform(platform.toLowerCase())
                    .build();
        }

        return ReplyRoute.builder()
                .platform(platform.toLowerCase())
                .chatId(chatId)
                .threadId(threadId)
                .build();
    }

    public static String format(ReplyRoute route) {
        if (route == null || StrUtil.isBlank(route.getPlatform())) {
            return null;
        }
        if (StrUtil.isBlank(route.getChatId())) {
            return route.getPlatform();
        }
        if (StrUtil.isBlank(route.getThreadId())) {
            return route.getPlatform() + ":" + route.getChatId();
        }
        return route.getPlatform() + ":" + route.getChatId() + ":" + route.getThreadId();
    }
}
