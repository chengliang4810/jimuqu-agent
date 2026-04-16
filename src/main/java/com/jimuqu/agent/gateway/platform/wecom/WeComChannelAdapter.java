package com.jimuqu.agent.gateway.platform.wecom;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.base.AbstractConfigurableChannelAdapter;
import okhttp3.*;
import org.noear.snack4.ONode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * WeComChannelAdapter 实现。
 */
public class WeComChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String DEFAULT_WS_URL = "wss://openws.work.weixin.qq.com";

    private final AppConfig.ChannelConfig config;
    private final OkHttpClient client;
    private final ConcurrentMap<String, CompletableFuture<ONode>> pendingResponses = new ConcurrentHashMap<String, CompletableFuture<ONode>>();
    private volatile WebSocket webSocket;

    public WeComChannelAdapter(AppConfig.ChannelConfig config) {
        super(PlatformType.WECOM, config);
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            setDetail("disabled");
            return false;
        }
        if (StrUtil.isBlank(config.getBotId()) || StrUtil.isBlank(config.getSecret())) {
            setConnected(false);
            setDetail("missing botId/secret");
            log.warn("[WECOM] Missing botId/secret");
            return false;
        }

        String wsUrl = StrUtil.blankToDefault(config.getWebsocketUrl(), DEFAULT_WS_URL);
        CountDownLatch latch = new CountDownLatch(1);
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new Listener(latch));
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("WeCom websocket open timeout");
            }
            ONode auth = request("aibot_subscribe", new ONode()
                    .set("bot_id", config.getBotId())
                    .set("secret", config.getSecret())
                    .asObject(), 15);
            int ret = auth.get("ret").getInt(0);
            if (ret != 0) {
                throw new IllegalStateException("WeCom subscribe failed: " + auth.toJson());
            }
            setConnected(true);
            setDetail("websocket subscribed");
            return true;
        } catch (Exception e) {
            if (webSocket != null) {
                webSocket.cancel();
            }
            setConnected(false);
            setDetail("connect failed: " + e.getMessage());
            throw new IllegalStateException("WeCom connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "normal");
            webSocket = null;
        }
        setConnected(false);
        setDetail("disconnected");
    }

    @Override
    public void send(DeliveryRequest request) {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("WeCom chatId is required");
        }
        ONode response = request("aibot_send_msg", new ONode()
                .set("chatid", request.getChatId())
                .set("msgtype", "markdown")
                .getOrNew("markdown")
                .set("content", request.getText())
                .parent()
                .asObject(), 15);
        int ret = response.get("ret").getInt(0);
        if (ret != 0) {
            throw new IllegalStateException("WeCom send failed: " + response.toJson());
        }
    }

    private ONode request(String cmd, ONode body, int timeoutSeconds) {
        if (webSocket == null) {
            throw new IllegalStateException("WeCom websocket is not connected");
        }

        String reqId = UUID.randomUUID().toString();
        CompletableFuture<ONode> future = new CompletableFuture<ONode>();
        pendingResponses.put(reqId, future);
        String payload = new ONode()
                .set("cmd", cmd)
                .getOrNew("headers")
                .set("req_id", reqId)
                .parent()
                .set("body", body)
                .toJson();

        if (!webSocket.send(payload)) {
            pendingResponses.remove(reqId);
            throw new IllegalStateException("WeCom websocket send failed");
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingResponses.remove(reqId);
            throw new IllegalStateException("WeCom request timeout", e);
        }
    }

    private class Listener extends WebSocketListener {
        private final CountDownLatch openLatch;

        private Listener(CountDownLatch openLatch) {
            this.openLatch = openLatch;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            openLatch.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            ONode node = ONode.ofJson(text);
            String reqId = node.get("headers").get("req_id").getString();
            if (StrUtil.isBlank(reqId)) {
                reqId = node.get("payload").get("headers").get("req_id").getString();
            }
            CompletableFuture<ONode> future = pendingResponses.remove(reqId);
            if (future != null) {
                future.complete(node);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            for (CompletableFuture<ONode> future : pendingResponses.values()) {
                future.completeExceptionally(t);
            }
            pendingResponses.clear();
            openLatch.countDown();
            log.warn("[WECOM] websocket failure: {}", t.getMessage());
        }
    }
}
