package com.jimuqu.agent.gateway.platform.feishu;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.MessageAttachment;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;

import java.io.File;
import java.util.List;

/**
 * FeishuChannelAdapter 实现。
 */
public class FeishuChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String SEND_URL = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
    private static final String IMAGE_UPLOAD_URL = "https://open.feishu.cn/open-apis/im/v1/images";
    private static final String FILE_UPLOAD_URL = "https://open.feishu.cn/open-apis/im/v1/files";

    private final AppConfig.ChannelConfig config;
    private final AttachmentCacheService attachmentCacheService;
    private volatile String tenantAccessToken;
    private volatile long tokenExpireAt;

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
            setConnected(true);
            setDetail("tenant token ready");
            return true;
        } catch (Exception e) {
            setConnected(false);
            setDetail("connect failed: " + e.getMessage());
            log.warn("[FEISHU] connect failed: {}", e.getMessage());
            return false;
        }
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
