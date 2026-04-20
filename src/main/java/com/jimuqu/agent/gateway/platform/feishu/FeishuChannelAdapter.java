package com.jimuqu.agent.gateway.platform.feishu;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.lark.oapi.Client;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.CallBackOperator;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerData;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.application.v6.model.GetApplicationReq;
import com.lark.oapi.service.application.v6.model.GetApplicationResp;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.MentionEvent;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.P2MessageReactionCreatedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReactionCreatedV1Data;
import com.lark.oapi.service.im.v1.model.P2MessageReactionDeletedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReactionDeletedV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.MessageAttachment;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import com.jimuqu.agent.support.constants.GatewayBehaviorConstants;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String BOT_INFO_URL = "https://open.feishu.cn/open-apis/bot/v3/info";

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
        setConnectionMode("websocket");
        setFeatures("text", "attachments", "post-media", "group-mention", "card-action", "reactions");
        setSetupState(config != null && config.isEnabled() ? "configured" : "disabled");
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            setSetupState("disabled");
            setDetail("disabled");
            return false;
        }
        java.util.ArrayList<String> missing = new java.util.ArrayList<String>();
        if (StrUtil.isBlank(config.getAppId())) {
            missing.add("JIMUQU_FEISHU_APP_ID");
        }
        if (StrUtil.isBlank(config.getAppSecret())) {
            missing.add("JIMUQU_FEISHU_APP_SECRET");
        }
        if (!missing.isEmpty()) {
            setConnected(false);
            setSetupState("missing_config");
            setMissingEnv(missing);
            setLastError("feishu_missing_credentials", "missing appId/appSecret");
            setDetail("missing appId/appSecret");
            log.warn("[FEISHU] Missing appId/appSecret");
            return false;
        }
        try {
            refreshTenantTokenIfNecessary();
            hydrateBotIdentity();
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
                    .onP2MessageReactionCreatedV1(new ImService.P2MessageReactionCreatedV1Handler() {
                        @Override
                        public void handle(P2MessageReactionCreatedV1 event) throws Exception {
                            handleReactionCreatedEvent(event == null ? null : event.getEvent());
                        }
                    })
                    .onP2MessageReactionDeletedV1(new ImService.P2MessageReactionDeletedV1Handler() {
                        @Override
                        public void handle(P2MessageReactionDeletedV1 event) throws Exception {
                            handleReactionDeletedEvent(event == null ? null : event.getEvent());
                        }
                    })
                    .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                        @Override
                        public P2CardActionTriggerResponse handle(P2CardActionTrigger event) throws Exception {
                            handleCardActionEvent(event == null ? null : event.getEvent());
                            return new P2CardActionTriggerResponse();
                        }
                    })
                    .build();
            wsClient = new com.lark.oapi.ws.Client.Builder(config.getAppId(), config.getAppSecret())
                    .eventHandler(dispatcher)
                    .build();
            wsClient.start();
            setConnected(true);
            setSetupState("connected");
            setMissingEnv(new String[0]);
            clearLastError();
            setDetail("websocket connected");
            return true;
        } catch (Exception e) {
            setConnected(false);
            setSetupState("error");
            setLastError("feishu_connect_failed", e.getMessage());
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

    private void handleReactionCreatedEvent(P2MessageReactionCreatedV1Data event) {
        if (event == null) {
            return;
        }
        GatewayMessage message = toReactionGatewayMessage(
                event.getMessageId(),
                event.getUserId(),
                event.getReactionType() == null ? "" : event.getReactionType().getEmojiType(),
                true
        );
        if (message != null && inboundMessageHandler() != null) {
            try {
                inboundMessageHandler().handle(message);
            } catch (Exception e) {
                throw new IllegalStateException("Feishu reaction handle failed", e);
            }
        }
    }

    private void handleReactionDeletedEvent(P2MessageReactionDeletedV1Data event) {
        if (event == null) {
            return;
        }
        GatewayMessage message = toReactionGatewayMessage(
                event.getMessageId(),
                event.getUserId(),
                event.getReactionType() == null ? "" : event.getReactionType().getEmojiType(),
                false
        );
        if (message != null && inboundMessageHandler() != null) {
            try {
                inboundMessageHandler().handle(message);
            } catch (Exception e) {
                throw new IllegalStateException("Feishu reaction handle failed", e);
            }
        }
    }

    private void handleCardActionEvent(P2CardActionTriggerData event) {
        if (event == null || inboundMessageHandler() == null) {
            return;
        }
        GatewayMessage message = toCardActionGatewayMessage(event);
        if (message == null) {
            return;
        }
        try {
            inboundMessageHandler().handle(message);
        } catch (Exception e) {
            throw new IllegalStateException("Feishu card action handle failed", e);
        }
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
        if (!allowInbound(chatType, chatId, userId, messageNode.getMentions(), rawContent)) {
            return null;
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
        if ("post".equalsIgnoreCase(messageType)) {
            return parsePostContent(content).textContent;
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
        } else if ("post".equalsIgnoreCase(messageType)) {
            PostParseResult post = parsePostContent(content);
            for (String imageKey : post.imageKeys) {
                MessageAttachment attachment = downloadMessageResource("image", messageId, imageKey, "image.jpg");
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }
            for (PostMediaRef ref : post.mediaRefs) {
                MessageAttachment attachment = downloadMessageResource(ref.resourceType, messageId, ref.fileKey, ref.fileName);
                if (attachment != null) {
                    attachment.setKind(AttachmentCacheService.normalizeKind(ref.resourceType, ref.fileName, attachment.getMimeType()));
                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    private boolean allowInbound(String chatType, String chatId, String userId, MentionEvent[] mentions, String rawContent) {
        if ("group".equalsIgnoreCase(chatType)) {
            String policy = StrUtil.blankToDefault(config.getGroupPolicy(), GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST).toLowerCase();
            if (GatewayBehaviorConstants.GROUP_POLICY_DISABLED.equals(policy)) {
                return false;
            }
            if (GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST.equals(policy)
                    && !contains(config.getGroupAllowedUsers(), chatId)
                    && !contains(config.getAllowedUsers(), userId)) {
                return false;
            }
            discoverBotName(mentions);
            return isBotMentioned(mentions, rawContent);
        }
        String dmPolicy = StrUtil.blankToDefault(config.getDmPolicy(), GatewayBehaviorConstants.DM_POLICY_OPEN).toLowerCase();
        if (GatewayBehaviorConstants.DM_POLICY_DISABLED.equals(dmPolicy)) {
            return false;
        }
        if (GatewayBehaviorConstants.DM_POLICY_ALLOWLIST.equals(dmPolicy)) {
            return contains(config.getAllowedUsers(), userId);
        }
        return true;
    }

    private boolean contains(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            String normalized = StrUtil.nullToEmpty(value).trim();
            if ("*".equals(normalized) || target.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private void discoverBotName(MentionEvent[] mentions) {
        if (StrUtil.isNotBlank(config.getBotName()) || mentions == null) {
            return;
        }
        for (MentionEvent mention : mentions) {
            if (mention != null && StrUtil.isNotBlank(mention.getName())) {
                config.setBotName(mention.getName().trim());
                return;
            }
        }
    }

    private boolean isBotMentioned(MentionEvent[] mentions, String rawContent) {
        if (mentions == null || mentions.length == 0) {
            return false;
        }
        for (MentionEvent mention : mentions) {
            if (mention == null) {
                continue;
            }
            UserId id = mention.getId();
            if (id != null) {
                if (StrUtil.isNotBlank(config.getBotOpenId()) && config.getBotOpenId().equalsIgnoreCase(id.getOpenId())) {
                    return true;
                }
                if (StrUtil.isNotBlank(config.getBotUserId()) && config.getBotUserId().equalsIgnoreCase(id.getUserId())) {
                    return true;
                }
            }
            if (StrUtil.isNotBlank(config.getBotName()) && config.getBotName().equalsIgnoreCase(mention.getName())) {
                return true;
            }
        }
        return mentions.length > 0 && StrUtil.nullToEmpty(rawContent).contains("@_user_");
    }

    private PostParseResult parsePostContent(ONode content) {
        PostParseResult result = new PostParseResult();
        ONode locale = content.get("zh_cn");
        if (locale == null || locale.isNull()) {
            locale = content.get("en_us");
        }
        if (locale == null || locale.isNull()) {
            locale = content;
        }
        collectPostNode(locale, result);
        result.textContent = result.text.toString().trim();
        return result;
    }

    private void collectPostNode(ONode node, PostParseResult result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectPostNode(node.get(i), result);
            }
            return;
        }
        if (!node.isObject()) {
            String text = node.getString();
            if (StrUtil.isNotBlank(text)) {
                appendText(result.text, text.trim());
            }
            return;
        }

        String tag = node.get("tag").getString();
        if ("text".equalsIgnoreCase(tag)) {
            appendText(result.text, node.get("text").getString());
        } else if ("a".equalsIgnoreCase(tag)) {
            appendText(result.text, node.get("text").getString());
        } else if ("at".equalsIgnoreCase(tag)) {
            appendText(result.text, StrUtil.blankToDefault(node.get("user_name").getString(), node.get("name").getString()));
        } else if ("img".equalsIgnoreCase(tag)) {
            String imageKey = node.get("image_key").getString();
            if (StrUtil.isNotBlank(imageKey)) {
                result.imageKeys.add(imageKey);
            }
        } else if ("media".equalsIgnoreCase(tag)) {
            String fileKey = node.get("file_key").getString();
            if (StrUtil.isNotBlank(fileKey)) {
                result.mediaRefs.add(new PostMediaRef(fileKey, StrUtil.blankToDefault(node.get("file_name").getString(), "media.mp4"), "media"));
            }
        } else if ("file".equalsIgnoreCase(tag)) {
            String fileKey = node.get("file_key").getString();
            if (StrUtil.isNotBlank(fileKey)) {
                result.mediaRefs.add(new PostMediaRef(fileKey, StrUtil.blankToDefault(node.get("file_name").getString(), "attachment.bin"), "file"));
            }
        }

        Map<?, ?> values = ONode.deserialize(node.toJson(), LinkedHashMap.class);
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if ("tag".equals(key) || "text".equals(key) || "file_key".equals(key)
                    || "file_name".equals(key) || "image_key".equals(key)
                    || "user_name".equals(key) || "name".equals(key)) {
                continue;
            }
            collectPostNode(node.get(key), result);
        }
    }

    private void appendText(StringBuilder buffer, String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append('\n');
        }
        buffer.append(text.trim());
    }

    private GatewayMessage toCardActionGatewayMessage(P2CardActionTriggerData event) {
        if (event == null || event.getContext() == null) {
            return null;
        }
        CallBackOperator operator = event.getOperator();
        String userId = operator == null ? null : StrUtil.blankToDefault(operator.getOpenId(), operator.getUserId());
        String chatId = event.getContext().getOpenChatId();
        String messageId = event.getContext().getOpenMessageId();
        String payload = ONode.serialize(event.getAction() == null ? new LinkedHashMap<String, Object>() : event.getAction().getValue());
        GatewayMessage message = new GatewayMessage(PlatformType.FEISHU, chatId, userId, "Card action: " + payload);
        message.setChatType(GatewayBehaviorConstants.CHAT_TYPE_DM);
        message.setChatName(chatId);
        message.setUserName(userId);
        message.setThreadId(messageId);
        return message;
    }

    private GatewayMessage toReactionGatewayMessage(String messageId, UserId userId, String emoji, boolean created) {
        if (StrUtil.isBlank(messageId)) {
            return null;
        }
        ONode meta = fetchMessageMeta(messageId);
        ONode item = meta.get("data").get("items").get(0);
        if (item == null || item.isNull()) {
            return null;
        }
        String chatId = item.get("chat_id").getString();
        String chatType = "group".equalsIgnoreCase(item.get("chat_type").getString()) ? "group" : "dm";
        String actor = userId == null ? null : StrUtil.blankToDefault(userId.getOpenId(), userId.getUserId());
        GatewayMessage message = new GatewayMessage(PlatformType.FEISHU, chatId, actor,
                (created ? "Reaction added: " : "Reaction removed: ") + emoji + " on " + messageId);
        message.setChatType(chatType);
        message.setChatName(chatId);
        message.setUserName(actor);
        message.setThreadId(messageId);
        return message;
    }

    private ONode fetchMessageMeta(String messageId) {
        refreshTenantTokenIfNecessary();
        String response = HttpRequest.get("https://open.feishu.cn/open-apis/im/v1/messages/" + messageId)
                .header("Authorization", "Bearer " + tenantAccessToken)
                .timeout(15000)
                .execute()
                .body();
        return ensureOk(response, "Feishu message lookup failed");
    }

    protected void hydrateBotIdentity() {
        if (StrUtil.isNotBlank(config.getBotOpenId())
                && StrUtil.isNotBlank(config.getBotUserId())
                && StrUtil.isNotBlank(config.getBotName())) {
            return;
        }
        try {
            Map<String, String> applicationInfo = fetchApplicationInfo();
            if (applicationInfo != null && StrUtil.isBlank(config.getBotName())) {
                String appName = applicationInfo.get("app_name");
                if (StrUtil.isNotBlank(appName)) {
                    config.setBotName(appName.trim());
                }
            }
        } catch (Exception e) {
            log.debug("[FEISHU] application info discovery failed: {}", e.getMessage(), e);
        }
        try {
            Map<String, String> botInfo = fetchBotInfo();
            if (botInfo != null) {
                if (StrUtil.isBlank(config.getBotName()) && StrUtil.isNotBlank(botInfo.get("bot_name"))) {
                    config.setBotName(botInfo.get("bot_name").trim());
                }
                if (StrUtil.isBlank(config.getBotOpenId()) && StrUtil.isNotBlank(botInfo.get("bot_open_id"))) {
                    config.setBotOpenId(botInfo.get("bot_open_id").trim());
                }
                if (StrUtil.isBlank(config.getBotUserId()) && StrUtil.isNotBlank(botInfo.get("bot_user_id"))) {
                    config.setBotUserId(botInfo.get("bot_user_id").trim());
                }
            }
        } catch (Exception e) {
            log.debug("[FEISHU] bot info discovery failed: {}", e.getMessage(), e);
        }
    }

    protected Map<String, String> fetchApplicationInfo() throws Exception {
        Client client = Client.newBuilder(config.getAppId(), config.getAppSecret()).build();
        GetApplicationReq request = GetApplicationReq.newBuilder()
                .appId(config.getAppId())
                .lang("en_us")
                .build();
        GetApplicationResp response = client.application().application().get(request);
        if (response == null || !response.success() || response.getData() == null || response.getData().getApp() == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("app_name", response.getData().getApp().getAppName());
        return result;
    }

    protected Map<String, String> fetchBotInfo() {
        refreshTenantTokenIfNecessary();
        String response = HttpRequest.get(BOT_INFO_URL)
                .header("Authorization", "Bearer " + tenantAccessToken)
                .timeout(15000)
                .execute()
                .body();
        ONode node = ONode.ofJson(response);
        if (node.get("code").getInt(-1) != 0) {
            return null;
        }
        ONode bot = node.get("bot");
        if (bot == null || bot.isNull()) {
            bot = node.get("data").get("bot");
        }
        if (bot == null || bot.isNull()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("bot_name", firstNonBlank(bot.get("bot_name").getString(), bot.get("botName").getString(), bot.get("app_name").getString()));
        result.put("bot_open_id", firstNonBlank(bot.get("open_id").getString(), bot.get("openId").getString()));
        result.put("bot_user_id", firstNonBlank(bot.get("user_id").getString(), bot.get("userId").getString()));
        return result;
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
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

    private static class PostMediaRef {
        private final String fileKey;
        private final String fileName;
        private final String resourceType;

        private PostMediaRef(String fileKey, String fileName, String resourceType) {
            this.fileKey = fileKey;
            this.fileName = fileName;
            this.resourceType = resourceType;
        }
    }

    private static class PostParseResult {
        private final StringBuilder text = new StringBuilder();
        private String textContent;
        private final List<String> imageKeys = new ArrayList<String>();
        private final List<PostMediaRef> mediaRefs = new ArrayList<PostMediaRef>();
    }
}
