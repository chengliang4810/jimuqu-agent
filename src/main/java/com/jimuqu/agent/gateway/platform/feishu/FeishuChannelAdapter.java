package com.jimuqu.agent.gateway.platform.feishu;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import org.noear.snack4.ONode;

/**
 * FeishuChannelAdapter 实现。
 */
public class FeishuChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String SEND_URL = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";

    private final AppConfig.ChannelConfig config;
    private volatile String tenantAccessToken;
    private volatile long tokenExpireAt;

    public FeishuChannelAdapter(AppConfig.ChannelConfig config) {
        super(PlatformType.FEISHU, config);
        this.config = config;
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            return false;
        }
        if (StrUtil.isBlank(config.getAppId()) || StrUtil.isBlank(config.getAppSecret())) {
            log.warn("[FEISHU] Missing appId/appSecret");
            return false;
        }
        try {
            refreshTenantTokenIfNecessary();
            return true;
        } catch (Exception e) {
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
            String content = ONode.serialize(new FeishuTextMessage(request.getText()));
            String body = new ONode()
                    .set("receive_id", request.getChatId())
                    .set("msg_type", "text")
                    .set("content", content)
                    .toJson();

            String response = HttpRequest.post(SEND_URL)
                    .contentType(ContentType.JSON.toString())
                    .header("Authorization", "Bearer " + tenantAccessToken)
                    .body(body)
                    .timeout(15000)
                    .execute()
                    .body();

            ONode node = ONode.ofJson(response);
            int code = node.get("code").getInt(0);
            if (code != 0) {
                throw new IllegalStateException("Feishu send failed: " + node.get("msg").getString());
            }
            log.info("[FEISHU:{}] {}", request.getChatId(), request.getText());
        } catch (Exception e) {
            throw new IllegalStateException("Feishu send failed", e);
        }
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

    public static class FeishuTextMessage {
        private String text;

        public FeishuTextMessage(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
