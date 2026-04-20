package com.jimuqu.agent.gateway.platform.dingtalk;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.aliyun.dingtalkoauth2_1_0.Client;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOHeaders;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTORequest;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOResponse;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendResponse;
import com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadHeaders;
import com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadRequest;
import com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadResponse;
import com.aliyun.tea.TeaException;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.MessageAttachment;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.noear.snack4.ONode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DingTalkChannelAdapter 实现。
 */
public class DingTalkChannelAdapter extends AbstractConfigurableChannelAdapter {
    private final AppConfig.ChannelConfig config;
    private final ChannelStateRepository channelStateRepository;
    private final AttachmentCacheService attachmentCacheService;
    private final Client oauthClient;
    private final com.aliyun.dingtalkrobot_1_0.Client robotClient;
    private volatile String accessToken;
    private volatile long accessTokenExpireAt;
    private volatile OpenDingTalkClient streamClient;
    private ExecutorService callbackExecutor;
    private final Map<String, Boolean> conversationGroupFlags = new ConcurrentHashMap<String, Boolean>();

    public DingTalkChannelAdapter(AppConfig.ChannelConfig config,
                                  ChannelStateRepository channelStateRepository,
                                  AttachmentCacheService attachmentCacheService) {
        super(PlatformType.DINGTALK, config);
        this.config = config;
        this.channelStateRepository = channelStateRepository;
        this.attachmentCacheService = attachmentCacheService;
        try {
            com.aliyun.teaopenapi.models.Config teaConfig = new com.aliyun.teaopenapi.models.Config();
            teaConfig.protocol = "https";
            teaConfig.regionId = "central";
            this.oauthClient = new Client(teaConfig);
            this.robotClient = new com.aliyun.dingtalkrobot_1_0.Client(teaConfig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize DingTalk SDK clients", e);
        }
    }

    @Override
    public boolean connect() {
        log.info("[DINGTALK] connect called: enabled={}, hasClientId={}, hasClientSecret={}, hasRobotCode={}",
                isEnabled(),
                !isBlank(config.getClientId()),
                !isBlank(config.getClientSecret()),
                !isBlank(config.getRobotCode()));
        if (!isEnabled()) {
            return false;
        }
        if (isBlank(config.getClientId()) || isBlank(config.getClientSecret())) {
            setDetail("missing clientId/clientSecret");
            log.warn("[DINGTALK] connect aborted: {}", detail());
            return false;
        }
        if (isBlank(config.getRobotCode())) {
            setDetail("missing robotCode");
            log.warn("[DINGTALK] connect aborted: {}", detail());
            return false;
        }

        try {
            refreshAccessTokenIfNecessary();
            callbackExecutor = Executors.newSingleThreadExecutor();
            streamClient = OpenDingTalkStreamClientBuilder.custom()
                    .credential(new AuthClientCredential(config.getClientId(), config.getClientSecret()))
                    .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, new OpenDingTalkCallbackListener<ChatbotMessage, Map<String, Object>>() {
                        public Map<String, Object> execute(ChatbotMessage message) {
                            handleInbound(message);
                            return new HashMap<String, Object>();
                        }
                    })
                    .build();
            streamClient.start();
            setConnected(true);
            setDetail("stream mode connected");
            log.info("[DINGTALK] stream mode connected");
            return true;
        } catch (Exception e) {
            setConnected(false);
            setDetail("stream mode connect failed: " + e.getMessage());
            log.warn("[DINGTALK] Stream mode connect failed", e);
            return false;
        }
    }

    @Override
    public void disconnect() {
        try {
            if (streamClient != null) {
                streamClient.stop();
            }
        } catch (Exception e) {
            log.warn("[DINGTALK] Stream mode disconnect failed", e);
        } finally {
            if (callbackExecutor != null) {
                callbackExecutor.shutdownNow();
            }
            setConnected(false);
            setDetail("stream mode disconnected");
        }
    }

    @Override
    public void send(DeliveryRequest request) throws Exception {
        if (isBlank(request.getChatId())) {
            throw new IllegalArgumentException("DingTalk openConversationId is required");
        }
        refreshAccessTokenIfNecessary();
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            StringBuilder buffer = new StringBuilder();
            if (notBlank(request.getText())) {
                buffer.append(request.getText()).append("\n\n");
            }
            buffer.append("当前版本钉钉附件发送暂按文本降级展示：");
            for (MessageAttachment attachment : request.getAttachments()) {
                buffer.append("\n- ")
                        .append(attachment.getOriginalName())
                        .append(" [")
                        .append(attachment.getKind())
                        .append("]");
            }
            request.setText(buffer.toString());
        }

        boolean isGroup = isGroupConversation(request);
        if (isGroup) {
            try {
                OrgGroupSendHeaders headers = new OrgGroupSendHeaders();
                headers.setXAcsDingtalkAccessToken(accessToken);

                OrgGroupSendRequest sendRequest = new OrgGroupSendRequest();
                sendRequest.setRobotCode(config.getRobotCode());
                sendRequest.setOpenConversationId(request.getChatId());
                sendRequest.setMsgKey("sampleMarkdown");
                sendRequest.setMsgParam(buildMarkdownParam(request.getText()));
                if (!isBlank(config.getCoolAppCode())) {
                    sendRequest.setCoolAppCode(config.getCoolAppCode());
                }

                OrgGroupSendResponse response = robotClient.orgGroupSendWithOptions(sendRequest, headers, new com.aliyun.teautil.models.RuntimeOptions());
                if (response == null || response.getBody() == null) {
                    throw new IllegalStateException("DingTalk group send returned empty response");
                }
                log.info("[DINGTALK:{}] sent processKey={}", request.getChatId(), response.getBody().getProcessQueryKey());
            } catch (TeaException e) {
                log.warn("[DINGTALK] group send failed: code={}, message={}, data={}", e.getCode(), e.getMessage(), e.getData(), e);
                throw e;
            }
        } else {
            if (isBlank(request.getUserId())) {
                throw new IllegalStateException("DingTalk private chat send requires userId from inbound context.");
            }
            try {
                BatchSendOTOHeaders headers = new BatchSendOTOHeaders();
                headers.setXAcsDingtalkAccessToken(accessToken);

                BatchSendOTORequest sendRequest = new BatchSendOTORequest();
                sendRequest.setRobotCode(config.getRobotCode());
                sendRequest.setUserIds(java.util.Collections.singletonList(request.getUserId()));
                sendRequest.setMsgKey("sampleMarkdown");
                sendRequest.setMsgParam(buildMarkdownParam(request.getText()));

                BatchSendOTOResponse response = robotClient.batchSendOTOWithOptions(sendRequest, headers, new com.aliyun.teautil.models.RuntimeOptions());
                if (response == null || response.getBody() == null) {
                    throw new IllegalStateException("DingTalk private send returned empty response");
                }
                log.info("[DINGTALK:{}] sent private batch response={}", request.getUserId(), response.getBody().toMap());
            } catch (TeaException e) {
                log.warn("[DINGTALK] private send failed: code={}, message={}, data={}", e.getCode(), e.getMessage(), e.getData(), e);
                throw e;
            }
        }
    }

    private void handleInbound(final ChatbotMessage message) {
        if (callbackExecutor == null || inboundMessageHandler() == null || message == null) {
            return;
        }
        callbackExecutor.submit(new Runnable() {
            public void run() {
                try {
                    String text = extractText(message);
                    if (isBlank(text)) {
                        return;
                    }
                    String conversationId = notBlank(message.getConversationId()) ? message.getConversationId() : message.getSenderId();
                    String userId = notBlank(message.getSenderStaffId()) ? message.getSenderStaffId() : message.getSenderId();
                    conversationGroupFlags.put(conversationId, "2".equals(String.valueOf(message.getConversationType())));
                    try {
                        channelStateRepository.put(PlatformType.DINGTALK, conversationId, "last_user_id", userId);
                    } catch (Exception ignored) {
                    }
                    List<MessageAttachment> attachments = extractAttachments(message);
                    log.info("[DINGTALK-INBOUND] conversationId={}, senderId={}, senderStaffId={}, type={}, text={}",
                            conversationId,
                            message.getSenderId(),
                            message.getSenderStaffId(),
                            message.getConversationType(),
                            text);
                    GatewayMessage gatewayMessage = new GatewayMessage(PlatformType.DINGTALK, conversationId, userId, text);
                    gatewayMessage.setChatType("2".equals(String.valueOf(message.getConversationType())) ? "group" : "dm");
                    gatewayMessage.setChatName(notBlank(message.getConversationTitle()) ? message.getConversationTitle() : conversationId);
                    gatewayMessage.setUserName(notBlank(message.getSenderNick()) ? message.getSenderNick() : userId);
                    gatewayMessage.setThreadId(message.getMsgId());
                    gatewayMessage.setAttachments(attachments);
                    inboundMessageHandler().handle(gatewayMessage);
                } catch (Exception e) {
                    log.warn("[DINGTALK] inbound dispatch failed: {}", e.getMessage(), e);
                }
            }
        });
    }

    private synchronized void refreshAccessTokenIfNecessary() throws Exception {
        long now = System.currentTimeMillis();
        if (!isBlank(accessToken) && accessTokenExpireAt > now + 60000L) {
            return;
        }

        GetAccessTokenRequest request = new GetAccessTokenRequest()
                .setAppKey(config.getClientId())
                .setAppSecret(config.getClientSecret());
        GetAccessTokenResponse response = oauthClient.getAccessToken(request);
        if (response == null || response.getBody() == null) {
            throw new IllegalStateException("DingTalk access token response is empty");
        }

        GetAccessTokenResponseBody body = response.getBody();
        if (isBlank(body.getAccessToken()) || body.getExpireIn() == null) {
            throw new IllegalStateException("DingTalk access token is missing");
        }

        accessToken = body.getAccessToken();
        accessTokenExpireAt = now + (body.getExpireIn() * 1000L);
    }

    private String buildMarkdownParam(String text) {
        return new ONode()
                .set("title", resolveMarkdownTitle(text))
                .set("text", text)
                .toJson();
    }

    private boolean isGroupConversation(DeliveryRequest request) {
        if ("group".equalsIgnoreCase(request.getChatType())) {
            return true;
        }
        if ("dm".equalsIgnoreCase(request.getChatType())) {
            return false;
        }
        Boolean value = conversationGroupFlags.get(request.getChatId());
        return value == null || value.booleanValue();
    }

    private String extractText(ChatbotMessage message) {
        String messageType = message.getMsgtype();
        if ("audio".equalsIgnoreCase(messageType) && message.getContent() != null && notBlank(message.getContent().getRecognition())) {
            return message.getContent().getRecognition().trim();
        }
        MessageContent text = message.getText();
        if (text != null && !isBlank(text.getContent())) {
            return text.getContent().trim();
        }
        MessageContent content = message.getContent();
        if (content != null && !isBlank(content.getContent())) {
            return content.getContent().trim();
        }
        return "";
    }

    private List<MessageAttachment> extractAttachments(ChatbotMessage message) {
        List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
        MessageContent content = message.getContent();
        String msgType = StrUtil.nullToEmpty(message.getMsgtype()).toLowerCase();
        if ("picture".equals(msgType) && content != null) {
            addAttachment(attachments, "image", content.getPictureDownloadCode(), "image.jpg", "image/jpeg", null);
        } else if ("file".equals(msgType) && content != null) {
            addAttachment(attachments, "file", content.getDownloadCode(), content.getFileName(), AttachmentCacheService.normalizeMimeType(null, content.getFileName()), null);
        } else if ("video".equals(msgType) && content != null) {
            addAttachment(attachments, "video", content.getDownloadCode(), "video.mp4", "video/mp4", null);
        } else if ("audio".equals(msgType) && content != null) {
            addAttachment(attachments, "voice", content.getDownloadCode(), "voice.silk", "audio/silk", content.getRecognition());
        }
        if (content != null && content.getRichText() != null) {
            for (MessageContent item : content.getRichText()) {
                String itemType = StrUtil.nullToEmpty(item.getType()).toLowerCase();
                if ("picture".equals(itemType) || "image".equals(itemType)) {
                    addAttachment(attachments, "image", notBlank(item.getPictureDownloadCode()) ? item.getPictureDownloadCode() : item.getDownloadCode(), "image.jpg", "image/jpeg", null);
                } else if ("file".equals(itemType)) {
                    addAttachment(attachments, "file", item.getDownloadCode(), item.getFileName(), AttachmentCacheService.normalizeMimeType(null, item.getFileName()), null);
                } else if ("video".equals(itemType)) {
                    addAttachment(attachments, "video", item.getDownloadCode(), "video.mp4", "video/mp4", null);
                } else if ("audio".equals(itemType) || "voice".equals(itemType)) {
                    addAttachment(attachments, "voice", item.getDownloadCode(), "voice.silk", "audio/silk", item.getRecognition());
                }
            }
        }
        return attachments;
    }

    private void addAttachment(List<MessageAttachment> attachments,
                               String kind,
                               String downloadCode,
                               String fileName,
                               String mimeType,
                               String transcribedText) {
        if (isBlank(downloadCode)) {
            return;
        }
        try {
            String downloadUrl = resolveDownloadUrl(downloadCode);
            byte[] data = HttpRequest.get(downloadUrl).timeout(30000).execute().bodyBytes();
            attachments.add(attachmentCacheService.cacheBytes(PlatformType.DINGTALK, kind, fileName, mimeType, false, transcribedText, data));
        } catch (Exception e) {
            log.warn("[DINGTALK] attachment download failed: kind={}, code={}, message={}", kind, downloadCode, e.getMessage());
        }
    }

    private String resolveDownloadUrl(String downloadCode) throws Exception {
        RobotMessageFileDownloadHeaders headers = new RobotMessageFileDownloadHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        RobotMessageFileDownloadRequest request = new RobotMessageFileDownloadRequest()
                .setDownloadCode(downloadCode)
                .setRobotCode(config.getRobotCode());
        RobotMessageFileDownloadResponse response = robotClient.robotMessageFileDownloadWithOptions(
                request,
                headers,
                new com.aliyun.teautil.models.RuntimeOptions()
        );
        if (response == null || response.getBody() == null || isBlank(response.getBody().getDownloadUrl())) {
            throw new IllegalStateException("DingTalk download url missing");
        }
        return response.getBody().getDownloadUrl();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    private String resolveMarkdownTitle(String content) {
        if (isBlank(content)) {
            return "jimuqu-agent";
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalized = line.replaceFirst("^[#>*`\\-\\s]+", "").trim();
            if (!isBlank(normalized)) {
                return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
            }
        }
        return "jimuqu-agent";
    }
}
