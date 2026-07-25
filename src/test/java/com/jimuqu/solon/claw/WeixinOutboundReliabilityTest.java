package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.noear.snack4.ONode;

/** 验证微信出站请求串行、有界重试和失效上下文 token 清理。 */
public class WeixinOutboundReliabilityTest {
    /** 收到 ret=-2 后应按配置重试，文字与附件均不得丢失或乱序。 */
    @Test
    void shouldRetryRetMinusTwoAttachmentWithoutDroppingOrderedDelivery() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverExecutor = Executors.newCachedThreadPool();
        List<String> bodies = Collections.synchronizedList(new ArrayList<String>());
        AtomicInteger sendRequests = new AtomicInteger();
        server.setExecutor(serverExecutor);
        String uploadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/upload";
        registerUploadEndpoints(server, uploadUrl);
        server.createContext(
                "/ilink/bot/sendmessage",
                exchange -> {
                    bodies.add(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    String responseText =
                            sendRequests.incrementAndGet() == 2 ? "{\"ret\":-2}" : "{}";
                    byte[] response = responseText.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        AppConfig config = newConfig(server);
        config.getChannels().getWeixin().setSendChunkRetries(1);
        config.getChannels().getWeixin().setSendChunkRetryDelaySeconds(0D);
        WeiXinChannelAdapter adapter = newAdapter(config, new MemoryStateRepository());
        File attachmentFile = Files.createTempFile("solonclaw-weixin-rate-limit", ".txt").toFile();
        Files.writeString(attachmentFile.toPath(), "attachment", StandardCharsets.UTF_8);

        try {
            DeliveryRequest request = textRequest("wx-user", "text-before-attachment");
            request.getAttachments()
                    .add(attachmentRequest("wx-user", attachmentFile).getAttachments().get(0));
            adapter.send(request);

            assertThat(sendRequests.get()).isEqualTo(3);
            assertThat(bodies).hasSize(3);
            assertThat(bodies.get(0)).contains("\"type\":1");
            assertThat(bodies.get(1)).contains("\"type\":4");
            assertThat(bodies.get(2)).contains("\"type\":4");
        } finally {
            adapter.disconnect();
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    /** 持续收到平台失败响应时必须按配置停止重试，并允许后续消息继续发送。 */
    @Test
    void shouldBoundPersistentPlatformFailureAndReleaseOutboundGate() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean platformFailure = new AtomicBoolean(true);
        AtomicInteger sendRequests = new AtomicInteger();
        server.createContext(
                "/ilink/bot/sendmessage",
                exchange -> {
                    sendRequests.incrementAndGet();
                    String responseText = platformFailure.get() ? "{\"ret\":-2}" : "{}";
                    byte[] response = responseText.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        AppConfig config = newConfig(server);
        config.getChannels().getWeixin().setSendChunkRetries(1);
        config.getChannels().getWeixin().setSendChunkRetryDelaySeconds(0D);
        WeiXinChannelAdapter adapter = newAdapter(config, new MemoryStateRepository());

        try {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(1L),
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    adapter.send(
                                                            textRequest(
                                                                    "wx-user",
                                                                    "persistent failure")))
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("after 2 attempt(s)")
                                    .hasMessageContaining("\"ret\":-2"));
            assertThat(sendRequests.get()).isEqualTo(2);

            platformFailure.set(false);
            adapter.send(textRequest("wx-user", "after failure"));
            assertThat(sendRequests.get()).isEqualTo(3);
        } finally {
            adapter.disconnect();
            server.stop(0);
        }
    }

    /** tokenless 降级成功后，下一条消息不得再次携带已失效的持久 token。 */
    @ParameterizedTest
    @ValueSource(strings = {"errcode", "ret"})
    void shouldClearExpiredContextTokenBeforeFollowingSend(String errorField) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> bodies = Collections.synchronizedList(new ArrayList<String>());
        AtomicInteger requests = new AtomicInteger();
        server.createContext(
                "/ilink/bot/sendmessage",
                exchange -> {
                    bodies.add(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    String responseText =
                            requests.incrementAndGet() == 1 ? "{\"" + errorField + "\":-14}" : "{}";
                    byte[] response = responseText.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        AppConfig config = newConfig(server);
        config.getChannels().getWeixin().setSendChunkRetries(0);
        MemoryStateRepository repository = new MemoryStateRepository();
        repository.value.set("expired-token");
        WeiXinChannelAdapter adapter = newAdapter(config, repository);

        try {
            adapter.send(textRequest("wx-user", "first"));
            adapter.send(textRequest("wx-user", "second"));

            assertThat(bodies).hasSize(3);
            assertThat(ONode.ofJson(bodies.get(0)).get("msg").get("context_token").getString())
                    .isEqualTo("expired-token");
            assertThat(ONode.ofJson(bodies.get(1)).get("msg").get("context_token").getString())
                    .isBlank();
            assertThat(ONode.ofJson(bodies.get(2)).get("msg").get("context_token").getString())
                    .isBlank();
            assertThat(repository.deletes.get()).isEqualTo(1);
            assertThat(repository.value.get()).isNull();
        } finally {
            adapter.disconnect();
            server.stop(0);
        }
    }

    /** 附件降级发送也必须清理旧 token，且后续文字发送不得再次携带。 */
    @ParameterizedTest
    @ValueSource(strings = {"errcode", "ret"})
    void shouldClearExpiredContextTokenAfterAttachmentFallback(String errorField) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> bodies = Collections.synchronizedList(new ArrayList<String>());
        AtomicInteger sendRequests = new AtomicInteger();
        String uploadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/upload";
        registerUploadEndpoints(server, uploadUrl);
        server.createContext(
                "/ilink/bot/sendmessage",
                exchange -> {
                    bodies.add(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    String responseText =
                            sendRequests.incrementAndGet() == 1
                                    ? "{\"" + errorField + "\":-14}"
                                    : "{}";
                    byte[] response = responseText.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        AppConfig config = newConfig(server);
        MemoryStateRepository repository = new MemoryStateRepository();
        repository.value.set("expired-media-token");
        WeiXinChannelAdapter adapter = newAdapter(config, repository);
        File attachmentFile = Files.createTempFile("solonclaw-weixin-attachment", ".txt").toFile();
        Files.writeString(attachmentFile.toPath(), "attachment", StandardCharsets.UTF_8);

        try {
            adapter.send(attachmentRequest("wx-user", attachmentFile));
            adapter.send(textRequest("wx-user", "after attachment"));

            assertThat(bodies).hasSize(3);
            assertThat(ONode.ofJson(bodies.get(0)).get("msg").get("context_token").getString())
                    .isEqualTo("expired-media-token");
            assertThat(ONode.ofJson(bodies.get(1)).get("msg").get("context_token").getString())
                    .isBlank();
            assertThat(ONode.ofJson(bodies.get(2)).get("msg").get("context_token").getString())
                    .isBlank();
            assertThat(repository.deletes.get()).isEqualTo(1);
            assertThat(repository.value.get()).isNull();
        } finally {
            adapter.disconnect();
            server.stop(0);
        }
    }

    /** 创建指向本地假 iLink 服务的最小配置。 */
    private static AppConfig newConfig(HttpServer server) throws Exception {
        File home = Files.createTempDirectory("solonclaw-weixin-outbound-test").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(home.getAbsolutePath());
        config.getChannels().getWeixin().setAccountId("wx-bot");
        config.getChannels().getWeixin().setToken("test-token");
        config.getChannels()
                .getWeixin()
                .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return config;
    }

    /** 注册附件上传地址申请与上传两个固定测试端点。 */
    private static void registerUploadEndpoints(HttpServer server, String uploadUrl) {
        server.createContext(
                "/ilink/bot/getuploadurl",
                exchange -> {
                    byte[] response =
                            new ONode()
                                    .set("upload_full_url", uploadUrl)
                                    .toJson()
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.createContext(
                "/upload",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("x-encrypted-param", "uploaded-file");
                    exchange.sendResponseHeaders(200, 0L);
                    exchange.close();
                });
    }

    /** 创建带内存状态仓储的微信适配器。 */
    private static WeiXinChannelAdapter newAdapter(
            AppConfig config, ChannelStateRepository repository) {
        return new WeiXinChannelAdapter(
                config.getChannels().getWeixin(), repository, new AttachmentCacheService(config));
    }

    /** 创建单条文字投递请求。 */
    private static DeliveryRequest textRequest(String chatId, String text) {
        DeliveryRequest request = new DeliveryRequest();
        request.setChatId(chatId);
        request.setText(text);
        return request;
    }

    /** 创建单个文件附件投递请求。 */
    private static DeliveryRequest attachmentRequest(String chatId, File file) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setKind("file");
        attachment.setLocalPath(file.getAbsolutePath());
        attachment.setOriginalName(file.getName());
        attachment.setMimeType("text/plain");
        DeliveryRequest request = new DeliveryRequest();
        request.setChatId(chatId);
        request.getAttachments().add(attachment);
        return request;
    }

    /** 保存单个上下文 token，便于验证删除后连续发送行为。 */
    private static final class MemoryStateRepository implements ChannelStateRepository {
        private final AtomicReference<String> value = new AtomicReference<String>();
        private final AtomicInteger deletes = new AtomicInteger();

        /** 读取当前 token。 */
        @Override
        public String get(PlatformType platform, String scopeKey, String stateKey) {
            return value.get();
        }

        /** 保存当前 token。 */
        @Override
        public void put(
                PlatformType platform, String scopeKey, String stateKey, String stateValue) {
            value.set(stateValue);
        }

        /** 删除当前 token。 */
        @Override
        public void delete(PlatformType platform, String scopeKey, String stateKey) {
            deletes.incrementAndGet();
            value.set(null);
        }

        /** 当前测试不需要枚举状态项。 */
        @Override
        public List<StateItem> list(PlatformType platform, String scopeKey) {
            return Collections.emptyList();
        }
    }
}
