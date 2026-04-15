package com.jimuqu.agent.gateway.platform.weixin;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.gateway.AbstractConfigurableChannelAdapter;
import org.noear.snack4.ONode;

import java.util.UUID;

public class WeiXinChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String SEND_ENDPOINT = BASE_URL + "/ilink/bot/sendmessage";

    private final AppConfig.ChannelConfig config;

    public WeiXinChannelAdapter(AppConfig.ChannelConfig config) {
        super(PlatformType.WEIXIN, config);
        this.config = config;
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            return false;
        }
        if (StrUtil.isBlank(config.getToken())) {
            log.warn("[WEIXIN] Missing token");
            return false;
        }
        return true;
    }

    @Override
    public void send(DeliveryRequest request) {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("Weixin chatId is required");
        }

        ONode body = new ONode()
                .getOrNew("msg")
                .set("from_user_id", "")
                .set("to_user_id", request.getChatId())
                .set("client_id", "jimuqu-weixin-" + UUID.randomUUID().toString().replace("-", ""))
                .set("message_type", 2)
                .set("message_state", 2)
                .getOrNew("item_list")
                .asArray()
                .add(new ONode()
                        .set("type", 1)
                        .getOrNew("text_item")
                        .set("content", request.getText())
                        .parent()
                        .parent())
                .parent()
                .parent();

        String response = HttpRequest.post(SEND_ENDPOINT)
                .header("AuthorizationType", "ilink_bot_token")
                .header("Authorization", "Bearer " + config.getToken())
                .header("iLink-App-Id", "bot")
                .header("iLink-App-ClientVersion", String.valueOf((2 << 16) | (2 << 8)))
                .contentType(ContentType.JSON.toString())
                .body(body.toJson())
                .timeout(20000)
                .execute()
                .body();

        ONode node = ONode.ofJson(response);
        int errCode = node.get("errcode").getInt(0);
        int ret = node.get("ret").getInt(0);
        if (errCode != 0 || ret != 0) {
            throw new IllegalStateException("Weixin send failed: " + response);
        }
    }
}
