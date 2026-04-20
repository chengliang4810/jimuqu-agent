package com.jimuqu.agent.gateway.platform.weixin;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.MessageAttachment;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.noear.snack4.ONode;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * WeiXinChannelAdapter 实现。
 */
public class WeiXinChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String DEFAULT_CDN_BASE_URL = "https://novac2c.cdn.weixin.qq.com/c2c";
    private static final String SEND_ENDPOINT = "ilink/bot/sendmessage";
    private static final String GET_UPLOAD_URL_ENDPOINT = "ilink/bot/getuploadurl";
    private static final String CONTEXT_TOKEN_KEY = "context_token";

    private static final int MSG_TYPE_BOT = 2;
    private static final int MSG_STATE_FINISH = 2;
    private static final int ITEM_TEXT = 1;
    private static final int ITEM_IMAGE = 2;
    private static final int ITEM_FILE = 4;
    private static final int ITEM_VIDEO = 5;
    private static final int MEDIA_IMAGE = 1;
    private static final int MEDIA_VIDEO = 2;
    private static final int MEDIA_FILE = 3;

    private final AppConfig.ChannelConfig config;
    private final ChannelStateRepository channelStateRepository;
    private final AttachmentCacheService attachmentCacheService;

    public WeiXinChannelAdapter(AppConfig.ChannelConfig config,
                                ChannelStateRepository channelStateRepository,
                                AttachmentCacheService attachmentCacheService) {
        super(PlatformType.WEIXIN, config);
        this.config = config;
        this.channelStateRepository = channelStateRepository;
        this.attachmentCacheService = attachmentCacheService;
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            setDetail("disabled");
            return false;
        }
        if (StrUtil.isBlank(config.getToken()) || StrUtil.isBlank(config.getAccountId())) {
            setConnected(false);
            setDetail("missing token/accountId");
            log.warn("[WEIXIN] Missing token/accountId");
            return false;
        }
        setConnected(true);
        setDetail("token/accountId configured");
        return true;
    }

    @Override
    public void send(DeliveryRequest request) {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("Weixin chatId is required");
        }

        if (StrUtil.isNotBlank(request.getText())) {
            sendText(request.getChatId(), request.getText());
        }
        List<MessageAttachment> attachments = request.getAttachments();
        if (attachments != null) {
            for (MessageAttachment attachment : attachments) {
                sendAttachment(request.getChatId(), attachment);
            }
        }
    }

    private void sendText(String chatId, String text) {
        ONode message = baseMessage(chatId);
        String contextToken = loadContextToken(chatId);
        if (StrUtil.isNotBlank(contextToken)) {
            message.set("context_token", contextToken);
        }
        message.getOrNew("item_list").asArray().add(new ONode()
                .set("type", ITEM_TEXT)
                .getOrNew("text_item")
                .set("text", text)
                .parent()
                .parent());
        ONode response = apiPost(SEND_ENDPOINT, new ONode().set("msg", message).asObject());
        if (response.get("errcode").getInt(0) == -14 && StrUtil.isNotBlank(contextToken)) {
            message.remove("context_token");
            response = apiPost(SEND_ENDPOINT, new ONode().set("msg", message).asObject());
        }
        ensureSuccess(response, "Weixin text send failed");
    }

    private void sendAttachment(String chatId, MessageAttachment attachment) {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException("Weixin attachment file not found: " + attachment.getLocalPath());
        }

        String kind = AttachmentCacheService.normalizeKind(attachment.getKind(), attachment.getOriginalName(), attachment.getMimeType());
        int mediaType = "image".equals(kind) ? MEDIA_IMAGE : ("video".equals(kind) ? MEDIA_VIDEO : MEDIA_FILE);
        byte[] plaintext = FileUtil.readBytes(file);
        byte[] aesKey = RandomUtil.randomBytes(16);
        byte[] ciphertext = encryptAesEcb(plaintext, aesKey);
        String fileKey = IdUtil.fastSimpleUUID();

        ONode uploadInfo = apiPost(GET_UPLOAD_URL_ENDPOINT, new ONode()
                .set("filekey", fileKey)
                .set("media_type", mediaType)
                .set("to_user_id", chatId)
                .set("rawsize", plaintext.length)
                .set("rawfilemd5", DigestUtil.md5Hex(plaintext))
                .set("filesize", ciphertext.length)
                .set("no_need_thumb", true)
                .set("aeskey", HexUtil.encodeHexStr(aesKey))
                .asObject());
        ensureSuccess(uploadInfo, "Weixin upload init failed");

        String uploadUrl = resolveUploadUrl(uploadInfo, fileKey);
        String encryptedParam = uploadCiphertext(uploadUrl, ciphertext);

        ONode message = baseMessage(chatId);
        String contextToken = loadContextToken(chatId);
        if (StrUtil.isNotBlank(contextToken)) {
            message.set("context_token", contextToken);
        }
        message.getOrNew("item_list").asArray().add(
                buildMediaItem(mediaType, attachment, plaintext.length, ciphertext.length, encryptedParam, aesKey)
        );
        ONode sendResponse = apiPost(SEND_ENDPOINT, new ONode().set("msg", message).asObject());
        ensureSuccess(sendResponse, "Weixin media send failed");
    }

    private ONode baseMessage(String chatId) {
        return new ONode()
                .set("from_user_id", "")
                .set("to_user_id", chatId)
                .set("client_id", "jimuqu-weixin-" + UUID.randomUUID().toString().replace("-", ""))
                .set("message_type", MSG_TYPE_BOT)
                .set("message_state", MSG_STATE_FINISH)
                .getOrNew("item_list")
                .asArray()
                .parent()
                .asObject();
    }

    private ONode buildMediaItem(int mediaType,
                                 MessageAttachment attachment,
                                 int plaintextSize,
                                 int ciphertextSize,
                                 String encryptedParam,
                                 byte[] aesKey) {
        String encodedAesKey = Base64.getEncoder().encodeToString(HexUtil.encodeHexStr(aesKey).getBytes(StandardCharsets.UTF_8));
        if (mediaType == MEDIA_IMAGE) {
            return new ONode()
                    .set("type", ITEM_IMAGE)
                    .getOrNew("image_item")
                    .getOrNew("media")
                    .set("encrypt_query_param", encryptedParam)
                    .set("aes_key", encodedAesKey)
                    .set("encrypt_type", 1)
                    .parent()
                    .set("mid_size", ciphertextSize)
                    .parent()
                    .parent();
        }
        if (mediaType == MEDIA_VIDEO) {
            return new ONode()
                    .set("type", ITEM_VIDEO)
                    .getOrNew("video_item")
                    .getOrNew("media")
                    .set("encrypt_query_param", encryptedParam)
                    .set("aes_key", encodedAesKey)
                    .set("encrypt_type", 1)
                    .parent()
                    .set("video_size", ciphertextSize)
                    .set("play_length", 0)
                    .set("video_md5", DigestUtil.md5Hex(new File(attachment.getLocalPath())))
                    .parent()
                    .parent();
        }
        return new ONode()
                .set("type", ITEM_FILE)
                .getOrNew("file_item")
                .getOrNew("media")
                .set("encrypt_query_param", encryptedParam)
                .set("aes_key", encodedAesKey)
                .set("encrypt_type", 1)
                .parent()
                .set("file_name", fileNameOf(attachment))
                .set("len", String.valueOf(plaintextSize))
                .parent()
                .parent();
    }

    private ONode apiPost(String endpoint, ONode payload) {
        String baseUrl = StrUtil.blankToDefault(config.getBaseUrl(), DEFAULT_BASE_URL).replaceAll("/+$", "");
        String body = payload.toJson();
        String response = HttpRequest.post(baseUrl + "/" + endpoint)
                .header("AuthorizationType", "ilink_bot_token")
                .header("Authorization", "Bearer " + config.getToken())
                .header("iLink-App-Id", "bot")
                .header("iLink-App-ClientVersion", String.valueOf((2 << 16) | (2 << 8)))
                .contentType(ContentType.JSON.toString())
                .body(body)
                .timeout(20000)
                .execute()
                .body();
        return ONode.ofJson(response);
    }

    private String resolveUploadUrl(ONode uploadInfo, String fileKey) {
        String uploadFullUrl = uploadInfo.get("upload_full_url").getString();
        if (StrUtil.isNotBlank(uploadFullUrl)) {
            return uploadFullUrl;
        }
        String uploadParam = uploadInfo.get("upload_param").getString();
        if (StrUtil.isBlank(uploadParam)) {
            throw new IllegalStateException("Weixin upload init missing upload url: " + uploadInfo.toJson());
        }
        String cdnBaseUrl = StrUtil.blankToDefault(config.getCdnBaseUrl(), DEFAULT_CDN_BASE_URL).replaceAll("/+$", "");
        return cdnBaseUrl + "/upload?encrypted_query_param=" + cn.hutool.core.net.URLEncodeUtil.encodeAll(uploadParam) + "&filekey=" + cn.hutool.core.net.URLEncodeUtil.encodeAll(fileKey);
    }

    private String uploadCiphertext(String uploadUrl, byte[] ciphertext) {
        HttpResponse response = HttpRequest.post(uploadUrl)
                .body(ciphertext)
                .header("Content-Type", "application/octet-stream")
                .timeout(120000)
                .execute();
        try {
            if (response.getStatus() != 200) {
                throw new IllegalStateException("Weixin CDN upload failed: " + response.getStatus());
            }
            String encryptedParam = response.header("x-encrypted-param");
            if (StrUtil.isBlank(encryptedParam)) {
                throw new IllegalStateException("Weixin CDN upload missing x-encrypted-param header");
            }
            return encryptedParam;
        } finally {
            response.close();
        }
    }

    private byte[] encryptAesEcb(byte[] plaintext, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new IllegalStateException("Weixin encrypt failed", e);
        }
    }

    private void ensureSuccess(ONode node, String defaultMessage) {
        int errCode = node.get("errcode").getInt(0);
        int ret = node.get("ret").getInt(0);
        if (errCode != 0 || ret != 0) {
            throw new IllegalStateException(defaultMessage + ": " + node.toJson());
        }
    }

    private String loadContextToken(String chatId) {
        try {
            return channelStateRepository.get(PlatformType.WEIXIN, config.getAccountId() + ":" + chatId, CONTEXT_TOKEN_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    private String fileNameOf(MessageAttachment attachment) {
        return StrUtil.blankToDefault(attachment.getOriginalName(), new File(attachment.getLocalPath()).getName());
    }
}
