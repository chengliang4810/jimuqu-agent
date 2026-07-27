package com.jimuqu.solon.claw.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.provider.TranscriptionProvider.TranscriptionResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** 验证内置 OpenAI 兼容 STT Provider 的上游失败、非法响应和超时契约。 */
class OpenAiSttProviderTest {
    /** 非成功响应必须脱敏上游正文，非法 JSON 必须返回稳定领域错误。 */
    @Test
    void mapsHttpFailureAndMalformedJsonToSafeResults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/http-error",
                exchange -> {
                    byte[] response =
                            "{\"error\":\"api_key=sk-response-secret-12345\"}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(429, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.createContext(
                "/invalid-json",
                exchange -> {
                    byte[] response = "{bad".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        try {
            AppConfig config = sttConfig(endpoint(server, "/http-error"));
            OpenAiSttProvider provider = new OpenAiSttProvider(config);

            TranscriptionResult httpFailure =
                    provider.transcribe(
                            new byte[] {1, 2, 3},
                            "audio/wav",
                            Collections.<String, Object>emptyMap());

            assertThat(httpFailure.isSuccess()).isFalse();
            assertThat(httpFailure.getError())
                    .contains("HTTP 429")
                    .contains("api_key=***")
                    .doesNotContain("sk-response-secret-12345")
                    .doesNotContain("stt-test-key-12345");

            config.getSpeech().getStt().setEndpoint(endpoint(server, "/invalid-json"));
            TranscriptionResult malformed =
                    provider.transcribe(
                            new byte[] {4, 5, 6},
                            "audio/wav",
                            Collections.<String, Object>emptyMap());

            assertThat(malformed.isSuccess()).isFalse();
            assertThat(malformed.getError()).isEqualTo("STT provider returned invalid JSON");
        } finally {
            server.stop(0);
        }
    }

    /** HTTP 请求超时时必须返回固定低敏错误，不得透出端点或 API Key。 */
    @Test
    void mapsRequestTimeoutToSafeResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        Thread.sleep(3000L);
                        byte[] response = "{\"text\":\"late\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, response.length);
                        exchange.getResponseBody().write(response);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try {
            AppConfig config = sttConfig(endpoint(server, "/slow"));
            config.getSpeech().getStt().setTimeoutSeconds(1);
            OpenAiSttProvider provider = new OpenAiSttProvider(config);

            TranscriptionResult result =
                    provider.transcribe(
                            new byte[] {7, 8, 9},
                            "audio/wav",
                            Collections.<String, Object>emptyMap());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError())
                    .isEqualTo("STT request failed")
                    .doesNotContain("127.0.0.1")
                    .doesNotContain("stt-test-key-12345");
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    /**
     * 创建启用的 STT 测试配置。
     *
     * @param endpoint 本地协议端点。
     * @return 返回应用配置。
     */
    private AppConfig sttConfig(String endpoint) {
        AppConfig config = new AppConfig();
        config.getSpeech().getStt().setEnabled(true);
        config.getSpeech().getStt().setEndpoint(endpoint);
        config.getSpeech().getStt().setApiKey("stt-test-key-12345");
        config.getSpeech().getStt().setModel("stt-model");
        config.getSpeech().getStt().setTimeoutSeconds(2);
        return config;
    }

    /**
     * 构造进程内 HTTP 协议端点。
     *
     * @param server 本地 HTTP 服务器。
     * @param path 请求路径。
     * @return 返回完整端点。
     */
    private String endpoint(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
