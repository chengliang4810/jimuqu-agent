package com.jimuqu.agent.gateway.platform.feishu;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.MessageAttachment;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FeishuChannelAdapter 实现。
 */
public class FeishuChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String SEND_URL = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
    private static final String IMAGE_UPLOAD_URL = "https://open.feishu.cn/open-apis/im/v1/images";
    private static final String FILE_UPLOAD_URL = "https://open.feishu.cn/open-apis/im/v1/files";
    private static final String MESSAGE_RESOURCE_URL = "https://open.feishu.cn/open-apis/im/v1/messages/%s/resources/%s?type=%s";

    private final AppConfig.ChannelConfig config;
    private final AttachmentCacheService attachmentCacheService;
    private volatile String tenantAccessToken;
    private volatile long tokenExpireAt;
    private volatile com.lark.oapi.ws.Client wsClient;
    private ExecutorService inboundExecutor;

    public FeishuChannelAdapter(AppConfig.ChannelConfig config, AttachmentCacheService attachmentCacheService) {
        super(PlatformType.FEISHU, config);
        this.config = config;
        this.attachmentCacheService = attachmentCacheService;
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            setDetail("disabled");
            return false;
        }
        if (StrUtil.isBlank(config.getAppId()) || StrUtil.isBlank(config.getAppSecret())) {
            setConnected(false);
            setDetail("missing appId/appSecret");
            log.warn("[FEISHU] Missing appId/appSecret");
            return false;
        }
        try {
            refreshTenantTokenIfNecessary();
            inboundExecutor = Executors.newSingleThreadExecutor();
            EventDispatcher dispatcher = EventDispatcher.newBuilder("", "")
                    .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                        @Override
                        public void handle(P2MessageReceiveV1 event) {
                            if (event == null || event.getEvent() == null || inboundExecutor == null) {
                                return;
                            }
                            inboundExecutor.submit(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        handleWebsocketEvent(event.getEvent());
                                    } catch (Exception e) {
                                        log.warn("[FEISHU] websocket inbound dispatch failed: {}", e.getMessage(), e);
                                    }
                                }
                            });
                        }
                    })
                    .build();
            wsClient = new com.lark.oapi.ws.Client.Builder(config.getAppId(), config.getAppSecret())
                    .eventHandler(dispatcher)
                    .build();
            wsClient.start();
            setConnected(true);
            setDetail("websocket connected");
            return true;
        } catch (Exception e) {
            setConnected(false);
            setDetail("connect failed: " + e.getMessage());
            log.warn("[FEISHU] connect failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        shutdownWebsocketClient();
        if (inboundExecutor != null) {
            inboundExecutor.shutdownNow();
            inboundExecutor = null;
        }
        setConnected(false);
        setDetail("disconnected");
    }

    @Override
    public void send(DeliveryRequest request) {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("Feishu chatId is required");
        }
        try {
            refreshTenantTokenIfNecessary();
            if (StrUtil.isNotBlank(request.getText())) {
                sendText(request.getChatId(), request.getText());
            }
            List<MessageAttachment> attachments = request.getAttachments();
            if (attachments != null) {
                for (MessageAttachment attachment : attachments) {
                    sendAttachment(request.getChatId(), attachment);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Feishu send failed", e);
        }
    }

    public String handleWebhook(String rawBody) {
        ONode payload = ONode.ofJson(rawBody);
        if ("url_verification".equals(payload.get("type").getString())) {
            return new ONode().set("challenge", payload.get("challenge").getString()).toJson();
        }
        String eventType = payload.get("header").get("event_type").getString();
        if (!"im.message.receive_v1".equals(eventType)) {
            return new ONode().set("code", 0).set("msg", "ok").toJson();
        }
        try {
            GatewayMessage message = toGatewayMessageFromWebhook(payload.get("event"));
            if (message != null && inboundMessageHandler() != null) {
                inboundMessageHandler().handle(message);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Feishu webhook handle failed", e);
        }
        return new ONode().set("code", 0).set("msg", "ok").toJson();
    }

    public void handleWebsocketEvent(P2MessageReceiveV1Data event) {
        GatewayMessage message = toGatewayMessage(event == null ? null : event.getMessage(), event == null ? null : event.getSender());
        if (message != null && inboundMessageHandler() != null) {
            try {
                inboundMessageHandler().handle(message);
            } catch (Exception e) {
                throw new IllegalStateException("Feishu websocket handle failed", e);
            }
        }
    }

    private GatewayMessage toGatewayMessageFromWebhook(ONode event) {
        ONode messageNode = event.get("message");
        ONode senderNode = event.get("sender");
        if (messageNode == null || messageNode.isNull()) {
            return null;
        }
        String messageType = messageNode.get("message_type").getString();
        String rawContent = messageNode.get("content").getString();
        ONode content = StrUtil.isBlank(rawContent) ? new ONode() : ONode.ofJson(rawContent);
        String chatId = messageNode.get("chat_id").getString();
        String chatType = "group".equalsIgnoreCase(messageNode.get("chat_type").getString()) ? "group" : "dm";
        String userId = senderNode.get("sender_id").get("open_id").getString();
        if (StrUtil.isBlank(userId)) {
            userId = senderNode.get("sender_id").get("user_id").getString();
        }
        String text = extractInboundText(messageType, content);
        List<MessageAttachment> attachments = extractInboundAttachments(messageType, content, messageNode.get("message_id").getString());
        if (StrUtil.isBlank(text) && attachments.isEmpty()) {
            return null;
        }
        GatewayMessage message = new GatewayMessage(PlatformType.FEISHU, chatId, userId, text);
        message.setChatType(chatType);
        message.setChatName(chatId);
        message.setUserName(userId);
        message.setThreadId(messageNode.get("message_id").getString());
        message.setAttachments(attachments);
        return message;
    }

    private GatewayMessage toGatewayMessage(EventMessage messageNode,
                                            com.lark.oapi.service.im.v1.model.EventSender sender) {
        if (messageNode == null) {
            return null;
        }
        String messageType = messageNode.getMessageType();
        String rawContent = messageNode.getContent();
        ONode content = StrUtil.isBlank(rawContent) ? new ONode() : ONode.ofJson(rawContent);
        String chatId = messageNode.getChatId();
        String chatType = "group".equalsIgnoreCase(messageNode.getChatType()) ? "group" : "dm";
        UserId senderId = sender == null ? null : sender.getSenderId();
        String userId = senderId == null ? null : senderId.getOpenId();
        if (StrUtil.isBlank(userId) && senderId != null) {
            userId = senderId.getUserId();
        }
        String text = extractInboundText(messageType, content);
        List<MessageAttachment> attachments = extractInboundAttachments(messageType, content, messageNode.getMessageId());
        if (StrUtil.isBlank(text) && attachments.isEmpty()) {
            return null;
        }
        GatewayMessage message = new GatewayMessage(PlatformType.FEISHU, chatId, userId, text);
        message.setChatType(chatType);
        message.setChatName(chatId);
        message.setUserName(userId);
        message.setThreadId(messageNode.getMessageId());
        message.setAttachments(attachments);
        return message;
    }

    private String extractInboundText(String messageType, ONode content) {
        if ("text".equalsIgnoreCase(messageType)) {
            return content.get("text").getString();
        }
        return "";
    }

    private List<MessageAttachment> extractInboundAttachments(String messageType, ONode content, String messageId) {
        List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
        if ("image".equalsIgnoreCase(messageType)) {
            MessageAttachment attachment = downloadMessageResource("image", messageId, content.get("image_key").getString(), "image.jpg");
            if (attachment != null) {
                attachments.add(attachment);
            }
        } else if ("file".equalsIgnoreCase(messageType)) {
            MessageAttachment attachment = downloadMessageResource("file", messageId, content.get("file_key").getString(), content.get("file_name").getString());
            if (attachment != null) {
                attachments.add(attachment);
            }
        } else if ("audio".equalsIgnoreCase(messageType)) {
            MessageAttachment attachment = downloadMessageResource("audio", messageId, content.get("file_key").getString(), content.get("file_name").getString());
            if (attachment != null) {
                attachment.setKind("voice");
                attachments.add(attachment);
            }
        } else if ("media".equalsIgnoreCase(messageType)) {
            MessageAttachment attachment = downloadMessageResource("media", messageId, content.get("file_key").getString(), content.get("file_name").getString());
            if (attachment != null) {
                attachment.setKind("video");
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    private MessageAttachment downloadMessageResource(String resourceType, String messageId, String fileKey, String fallbackName) {
        if (StrUtil.isBlank(messageId) || StrUtil.isBlank(fileKey)) {
            return null;
        }
        refreshTenantTokenIfNecessary();
        String url = String.format(MESSAGE_RESOURCE_URL, messageId, fileKey, resourceType);
        HttpResponse response = HttpRequest.get(url)
                .header("Authorization", "Bearer " + tenantAccessToken)
                .timeout(30000)
                .execute();
        try {
            if (response.getStatus() >= 400) {
                throw new IllegalStateException("Feishu resource download failed: " + response.body());
            }
            String fileName = fallbackName;
            if (StrUtil.isBlank(fileName)) {
                fileName = fileKey;
            }
            String mimeType = AttachmentCacheService.normalizeMimeType(response.header("Content-Type"), fileName);
            return attachmentCacheService.cacheBytes(
                    PlatformType.FEISHU,
                    AttachmentCacheService.normalizeKind(resourceType, fileName, mimeType),
                    fileName,
                    mimeType,
                    false,
                    null,
                    response.bodyBytes()
            );
        } finally {
            response.close();
        }
    }

    private void sendText(String chatId, String text) {
        String content = ONode.serialize(new FeishuTextMessage(text));
        String body = new ONode()
                .set("receive_id", chatId)
                .set("msg_type", "text")
                .set("content", content)
                .toJson();
        ensureOk(postJson(SEND_URL, body), "Feishu text send failed");
        log.info("[FEISHU:{}] {}", chatId, text);
    }

    private void sendAttachment(String chatId, MessageAttachment attachment) {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException("Feishu attachment file not found: " + attachment.getLocalPath());
        }

        String kind = AttachmentCacheService.normalizeKind(attachment.getKind(), attachment.getOriginalName(), attachment.getMimeType());
        if ("image".equals(kind)) {
            String imageKey = uploadImage(file);
            String payload = new ONode()
                    .set("receive_id", chatId)
                    .set("msg_type", "image")
                    .set("content", new ONode().set("image_key", imageKey).toJson())
                    .toJson();
            ensureOk(postJson(SEND_URL, payload), "Feishu image send failed");
            return;
        }

        UploadRouting routing = resolveFileRouting(attachment);
        String fileKey = uploadFile(file, routing.uploadType);
        String payload = new ONode()
                .set("receive_id", chatId)
                .set("msg_type", routing.messageType)
                .set("content", new ONode().set("file_key", fileKey).toJson())
                .toJson();
        ensureOk(postJson(SEND_URL, payload), "Feishu file send failed");
    }

    private String uploadImage(File file) {
        String response = HttpRequest.post(IMAGE_UPLOAD_URL)
                .header("Authorization", "Bearer " + tenantAccessToken)
                .form("image_type", "message")
                .form("image", file)
                .timeout(30000)
                .execute()
                .body();
        ONode node = ensureOk(response, "Feishu image upload failed");
        String imageKey = node.get("data").get("image_key").getString();
        if (StrUtil.isBlank(imageKey)) {
            throw new IllegalStateException("Feishu image upload missing image_key");
        }
        return imageKey;
    }

    private String uploadFile(File file, String uploadType) {
        String response = HttpRequest.post(FILE_UPLOAD_URL)
                .header("Authorization", "Bearer " + tenantAccessToken)
                .form("file_type", uploadType)
                .form("file_name", file.getName())
                .form("file", file)
                .timeout(30000)
                .execute()
                .body();
        ONode node = ensureOk(response, "Feishu file upload failed");
        String fileKey = node.get("data").get("file_key").getString();
        if (StrUtil.isBlank(fileKey)) {
            throw new IllegalStateException("Feishu file upload missing file_key");
        }
        return fileKey;
    }

    private UploadRouting resolveFileRouting(MessageAttachment attachment) {
        String name = StrUtil.blankToDefault(attachment.getOriginalName(), "attachment.bin").toLowerCase();
        if (name.endsWith(".ogg") || name.endsWith(".opus")) {
            return new UploadRouting("opus", "audio");
        }
        if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".m4v")) {
            return new UploadRouting("mp4", "media");
        }
        if (name.endsWith(".pdf")) {
            return new UploadRouting("pdf", "file");
        }
        if (name.endsWith(".doc") || name.endsWith(".docx")) {
            return new UploadRouting("doc", "file");
        }
        if (name.endsWith(".xls") || name.endsWith(".xlsx")) {
            return new UploadRouting("xls", "file");
        }
        if (name.endsWith(".ppt") || name.endsWith(".pptx")) {
            return new UploadRouting("ppt", "file");
        }
        return new UploadRouting("stream", "file");
    }

    private String postJson(String url, String body) {
        return HttpRequest.post(url)
                .contentType(ContentType.JSON.toString())
                .header("Authorization", "Bearer " + tenantAccessToken)
                .body(body)
                .timeout(15000)
                .execute()
                .body();
    }

    private ONode ensureOk(String response, String defaultMessage) {
        ONode node = ONode.ofJson(response);
        int code = node.get("code").getInt(0);
        if (code != 0) {
            throw new IllegalStateException(defaultMessage + ": " + node.get("msg").getString());
        }
        return node;
    }

    private synchronized void refreshTenantTokenIfNecessary() {
        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(tenantAccessToken) && now < tokenExpireAt) {
            return;
        }
        String body = new ONode()
                .set("app_id", config.getAppId())
                .set("app_secret", config.getAppSecret())
                .toJson();
        String response = HttpRequest.post(TOKEN_URL)
                .contentType(ContentType.JSON.toString())
                .body(body)
                .timeout(15000)
                .execute()
                .body();
        ONode node = ONode.ofJson(response);
        int code = node.get("code").getInt(0);
        if (code != 0) {
            throw new IllegalStateException("Fetch tenant token failed: " + node.get("msg").getString());
        }
        tenantAccessToken = node.get("tenant_access_token").getString();
        long expire = node.get("expire").getLong(7200L);
        tokenExpireAt = now + Math.max(60000L, (expire - 60L) * 1000L);
    }

    private void shutdownWebsocketClient() {
        if (wsClient == null) {
            return;
        }
        try {
            java.lang.reflect.Field autoReconnectField = com.lark.oapi.ws.Client.class.getDeclaredField("autoReconnect");
            autoReconnectField.setAccessible(true);
            autoReconnectField.set(wsClient, Boolean.FALSE);
            java.lang.reflect.Method disconnectMethod = com.lark.oapi.ws.Client.class.getDeclaredMethod("disconnect");
            disconnectMethod.setAccessible(true);
            disconnectMethod.invoke(wsClient);
            java.lang.reflect.Field executorField = com.lark.oapi.ws.Client.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            Object executor = executorField.get(wsClient);
            if (executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdownNow();
            }
        } catch (Exception e) {
            log.debug("[FEISHU] websocket shutdown cleanup failed: {}", e.getMessage(), e);
        } finally {
            wsClient = null;
        }
    }

    @RequiredArgsConstructor
    public static class FeishuTextMessage {
        private final String text;

        public String getText() {
            return text;
        }
    }

    @RequiredArgsConstructor
    private static class UploadRouting {
        private final String uploadType;
        private final String messageType;
    }
}
