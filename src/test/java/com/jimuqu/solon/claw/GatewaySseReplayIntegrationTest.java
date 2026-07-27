package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.IoUtil;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

/** 验证网关到 Responses SSE、会话持久化和渠道投递的完整本地回放链路。 */
public class GatewaySseReplayIntegrationTest {
    /** Responses SSE 回放必须经过真实网关主链并形成可持久化、可投递的最终回复。 */
    @Test
    void shouldReplayResponsesSseThroughGatewayPersistenceAndDelivery() throws Exception {
        try (ResponsesReplayServer server = new ResponsesReplayServer()) {
            TestEnvironment env = TestEnvironment.withReplayedResponsesLlm(server.baseUrl());
            env.send("sse-replay-room", "sse-replay-user", "/pairing claim-admin");

            GatewayReply reply = env.send("sse-replay-room", "sse-replay-user", "请只回复：网关 SSE 回放完成");

            assertThat(reply).isNotNull();
            assertThat(reply.getContent()).contains("网关 SSE 回放完成");
            assertThat(server.requestCount.get()).isEqualTo(1);
            assertThat(server.path.get()).isEqualTo("/v1/responses");
            ONode request = ONode.ofJson(server.body.get());
            assertThat(request.get("stream").getBoolean()).isTrue();
            assertThat(request.get("model").getString()).isEqualTo("gpt-5.4");
            assertThat(server.body.get()).contains("请只回复：网关 SSE 回放完成");
            assertThat(env.memoryChannelAdapter.getLastRequest()).isNotNull();
            assertThat(env.memoryChannelAdapter.getLastRequest().getText()).contains("网关 SSE 回放完成");
            SessionRecord session =
                    env.sessionRepository.getBoundSession("MEMORY:sse-replay-room:sse-replay-user");
            assertThat(session).isNotNull();
            assertThat(session.getNdjson()).contains("网关 SSE 回放完成");
        }
    }

    /** 提供分段刷新的本地 Responses SSE 固定回放服务。 */
    private static final class ResponsesReplayServer implements AutoCloseable {
        /** 本地随机端口 HTTP 服务。 */
        private final HttpServer server;

        /** 捕获到的请求路径。 */
        private final AtomicReference<String> path = new AtomicReference<String>();

        /** 捕获到的 UTF-8 请求体。 */
        private final AtomicReference<String> body = new AtomicReference<String>();

        /** 收到的模型请求数量。 */
        private final AtomicInteger requestCount = new AtomicInteger();

        /** 启动仅绑定 loopback 的随机端口回放服务。 */
        private ResponsesReplayServer() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::replay);
            server.start();
        }

        /** 返回不带协议路径的本地基础地址。 */
        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** 捕获请求并按事件边界分段刷新 Responses SSE。 */
        private void replay(HttpExchange exchange) {
            try {
                requestCount.incrementAndGet();
                path.set(exchange.getRequestURI().getPath());
                body.set(IoUtil.readUtf8(exchange.getRequestBody()));
                exchange.getResponseHeaders()
                        .set("Content-Type", "text/event-stream; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream output = exchange.getResponseBody()) {
                    writeEvent(
                            output,
                            "{\"type\":\"response.output_text.delta\",\"delta\":\"网关 SSE \"}");
                    writeEvent(
                            output, "{\"type\":\"response.output_text.delta\",\"delta\":\"回放完成\"}");
                    writeEvent(
                            output,
                            "{\"type\":\"response.completed\",\"response\":{\"model\":\"gpt-5.4\","
                                    + "\"usage\":{\"input_tokens\":2,\"output_tokens\":3,"
                                    + "\"total_tokens\":5}}}");
                    writeEvent(output, "[DONE]");
                }
            } catch (Exception e) {
                body.compareAndSet(null, "");
            } finally {
                exchange.close();
            }
        }

        /** 写入并立即刷新单个 SSE data 事件。 */
        private void writeEvent(OutputStream output, String data) throws Exception {
            output.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        /** 停止本地回放服务。 */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
