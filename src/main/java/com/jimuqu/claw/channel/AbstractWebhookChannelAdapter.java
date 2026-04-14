package com.jimuqu.claw.channel;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import com.jimuqu.claw.channel.model.ChannelOutboundMessage;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.support.JsonSupport;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractWebhookChannelAdapter implements ChannelAdapter {
    private final String platform;
    private final ClawProperties.ChannelProperties properties;

    protected AbstractWebhookChannelAdapter(String platform, ClawProperties.ChannelProperties properties) {
        this.platform = platform;
        this.properties = properties == null ? new ClawProperties.ChannelProperties() : properties;
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public boolean enabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }

    @Override
    public ChannelInboundMessage parseInbound(String body) {
        Map<String, Object> payload = ChannelPayloadSupport.parseJsonObject(body);

        String chatId = ChannelPayloadSupport.string(payload, chatIdPaths());
        String threadId = ChannelPayloadSupport.string(payload, threadIdPaths());
        String userId = ChannelPayloadSupport.string(payload, userIdPaths());
        String messageId = ChannelPayloadSupport.string(payload, messageIdPaths());
        String workspaceRoot = ChannelPayloadSupport.string(payload, workspaceRootPaths());
        String sessionId = ChannelPayloadSupport.string(payload, sessionIdPaths());
        String text = resolveText(payload);
        ReplyRoute replyRoute = resolveReplyRoute(payload, chatId, threadId);

        SessionContext sessionContext = SessionContext.builder()
                .sessionId(sessionId)
                .platform(platform())
                .chatId(chatId)
                .threadId(threadId)
                .userId(userId)
                .workspaceRoot(workspaceRoot)
                .messageId(messageId)
                .metadata(new LinkedHashMap<String, Object>())
                .build();

        return ChannelInboundMessage.builder()
                .messageId(messageId)
                .text(text)
                .sessionContext(sessionContext)
                .replyRoute(replyRoute)
                .receivedAt(Instant.now())
                .build();
    }

    @Override
    public Object sendMessage(ReplyRoute route, ChannelOutboundMessage outboundMessage) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("platform", platform());
        result.put("target", ReplyRouteSupport.format(route));

        if (outboundMessage == null || (StrUtil.isBlank(outboundMessage.getText()) && StrUtil.isBlank(outboundMessage.getMarkdown()))) {
            result.put("accepted", Boolean.FALSE);
            result.put("reason", "empty outbound message");
            return result;
        }

        if (StrUtil.isBlank(properties.getWebhookUrl())) {
            result.put("accepted", Boolean.FALSE);
            result.put("reason", "channel webhookUrl is not configured");
            return result;
        }

        String payloadJson = JsonSupport.toJson(toOutboundPayload(route, outboundMessage));
        HttpResponse response = null;
        try {
            response = HttpRequest.post(properties.getWebhookUrl())
                    .header("Content-Type", "application/json")
                    .body(payloadJson)
                    .execute();

            result.put("accepted", Boolean.TRUE);
            result.put("status", Integer.valueOf(response.getStatus()));
            result.put("response_body", StrUtil.maxLength(response.body(), 1000));
            return result;
        } catch (Exception e) {
            result.put("accepted", Boolean.FALSE);
            result.put("reason", e.getMessage());
            return result;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    protected Map<String, Object> toOutboundPayload(ReplyRoute route, ChannelOutboundMessage outboundMessage) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("platform", platform());
        payload.put("target", ReplyRouteSupport.format(route));
        payload.put("chat_id", route == null ? null : route.getChatId());
        payload.put("thread_id", route == null ? null : route.getThreadId());
        payload.put("text", outboundMessage.getText());
        payload.put("markdown", outboundMessage.getMarkdown());
        return payload;
    }

    protected String resolveText(Map<String, Object> payload) {
        Object value = ChannelPayloadSupport.value(payload, textPaths());
        return ChannelPayloadSupport.textValue(value);
    }

    protected ReplyRoute resolveReplyRoute(Map<String, Object> payload, String chatId, String threadId) {
        ReplyRoute route = ReplyRouteSupport.fromMap(
                ChannelPayloadSupport.map(payload, "reply_route", "replyRoute", "route"),
                platform());
        if (route != null) {
            return route;
        }

        route = ReplyRouteSupport.parse(ChannelPayloadSupport.string(payload, "reply_target", "replyTarget"));
        if (route != null) {
            if (StrUtil.isBlank(route.getPlatform())) {
                route.setPlatform(platform());
            }
            return route;
        }

        if (StrUtil.isBlank(chatId) && StrUtil.isBlank(threadId)) {
            return null;
        }

        return ReplyRoute.builder()
                .platform(platform())
                .chatId(chatId)
                .threadId(threadId)
                .build();
    }

    protected String[] messageIdPaths() {
        return new String[]{
                "message_id",
                "messageId",
                "message.id",
                "msg_id",
                "msgId",
                "event_id",
                "eventId",
                "header.event_id"
        };
    }

    protected String[] textPaths() {
        return new String[]{
                "text",
                "message",
                "message.text",
                "message.content",
                "content.text",
                "content",
                "event.text",
                "event.content",
                "event.message.text",
                "event.message.content"
        };
    }

    protected String[] chatIdPaths() {
        return new String[]{
                "chat_id",
                "chatId",
                "conversation_id",
                "conversationId",
                "open_chat_id",
                "openChatId",
                "session.chat_id",
                "session.chatId"
        };
    }

    protected String[] threadIdPaths() {
        return new String[]{
                "thread_id",
                "threadId",
                "message.thread_id",
                "message.threadId",
                "event.message.thread_id",
                "event.message.threadId",
                "session.thread_id",
                "session.threadId"
        };
    }

    protected String[] userIdPaths() {
        return new String[]{
                "user_id",
                "userId",
                "from_user",
                "fromUser",
                "sender.user_id",
                "sender.userId",
                "session.user_id",
                "session.userId"
        };
    }

    protected String[] workspaceRootPaths() {
        return new String[]{
                "workspace_root",
                "workspaceRoot",
                "session.workspace_root",
                "session.workspaceRoot"
        };
    }

    protected String[] sessionIdPaths() {
        return new String[]{
                "session_id",
                "sessionId",
                "session.id"
        };
    }

    protected String[] append(String[] base, String... extra) {
        String[] results = new String[base.length + extra.length];
        System.arraycopy(base, 0, results, 0, base.length);
        System.arraycopy(extra, 0, results, base.length, extra.length);
        return results;
    }
}
