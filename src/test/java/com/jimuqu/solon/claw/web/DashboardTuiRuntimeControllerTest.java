package com.jimuqu.solon.claw.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.TuiRuntimeProtocolService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextEmpty;

/** 验证终端运行时 JSON-RPC 控制器的客户端错误边界。 */
public class DashboardTuiRuntimeControllerTest {
    /** 未受控协议异常只能返回固定公共消息，不得暴露内部路径、令牌或异常类型。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldHideInternalProtocolExceptionDetails() {
        String sensitive = "SQLException at /srv/tui/config.yml token=sk-tuiruntime12345";
        TuiRuntimeProtocolService protocolService =
                new TuiRuntimeProtocolService(new AppConfig()) {
                    /** 模拟协议服务抛出包含内部细节的异常。 */
                    @Override
                    public Map<String, Object> setupStatus() {
                        throw new IllegalStateException(sensitive);
                    }
                };
        DashboardTuiRuntimeController controller =
                new DashboardTuiRuntimeController(protocolService);
        JsonContext context =
                new JsonContext(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"rpc-1\",\"method\":\"setup.status\"}");

        Map<String, Object> response = controller.rpc(context);

        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat(context.status()).isEqualTo(500);
        assertThat(response.get("id")).isEqualTo("rpc-1");
        assertThat(error.get("code")).isEqualTo(Integer.valueOf(-32000));
        assertThat(error.get("message")).isEqualTo("TUI runtime RPC failed");
        assertThat(String.valueOf(response))
                .doesNotContain("/srv/tui/config.yml")
                .doesNotContain("sk-tuiruntime12345")
                .doesNotContain("SQLException")
                .doesNotContain("IllegalStateException");
    }

    /** 提供固定 JSON 请求体的轻量 Solon 上下文。 */
    private static class JsonContext extends ContextEmpty {
        /** UTF-8 编码的 JSON 请求体。 */
        private final byte[] body;

        /**
         * 创建固定请求体上下文。
         *
         * @param json JSON 请求文本。
         */
        JsonContext(String json) {
            this.body = json.getBytes(StandardCharsets.UTF_8);
        }

        /** 返回可重复创建的请求体输入流。 */
        @Override
        public InputStream bodyAsStream() {
            return new ByteArrayInputStream(body);
        }

        /** 返回请求体字节长度。 */
        @Override
        public long contentLength() {
            return body.length;
        }
    }
}
