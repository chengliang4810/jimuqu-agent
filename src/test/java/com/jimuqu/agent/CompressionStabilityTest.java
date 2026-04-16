package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.engine.DefaultContextCompressionService;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.constants.CompressionConstants;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验上下文压缩的反抖动、失败冷却与摘要合并行为。
 */
public class CompressionStabilityTest {
    @Test
    void shouldSkipRecompressionWhenRecentCompressionDidNotGainEnoughNewContext() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-1");
        session.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser(repeat("A", 3200)),
                ChatMessage.ofAssistant(repeat("B", 3200))
        )));
        session.setLastCompressionAt(System.currentTimeMillis());
        session.setLastCompressionInputTokens(1500);

        SessionRecord compressed = service.compressIfNeeded(session, "system", "next");

        assertThat(compressed.getCompressedSummary()).isNull();
        assertThat(compressed.getLastCompressionAt()).isEqualTo(session.getLastCompressionAt());
    }

    @Test
    void shouldSkipCompressionDuringFailureCooldown() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-2");
        session.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser(repeat("A", 3200)),
                ChatMessage.ofAssistant(repeat("B", 3200))
        )));
        session.setLastCompressionFailedAt(System.currentTimeMillis());

        SessionRecord compressed = service.compressIfNeeded(session, "system", "next");

        assertThat(compressed.getCompressedSummary()).isNull();
        assertThat(compressed.getLastCompressionInputTokens()).isZero();
    }

    @Test
    void shouldMergePreviousSummaryWhenCompressingAgain() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-3");
        session.setCompressedSummary(CompressionConstants.SUMMARY_PREFIX + "\n旧摘要内容");
        session.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofSystem("system"),
                ChatMessage.ofUser("目标：完成同步"),
                ChatMessage.ofAssistant(CompressionConstants.SUMMARY_PREFIX + "\n旧摘要内容"),
                ChatMessage.ofAssistant("已经完成第一步并修改多个文件。"),
                ChatMessage.ofTool("tool output " + repeat("C", 500), "tool", "1"),
                ChatMessage.ofUser("继续推进"),
                ChatMessage.ofAssistant("继续处理中"),
                ChatMessage.ofUser("收尾")
        )));

        SessionRecord compressed = service.compressNow(session, "system prompt");

        assertThat(compressed.getCompressedSummary()).contains("Previous Summary");
        assertThat(compressed.getCompressedSummary()).contains("旧摘要内容");
    }

    private AppConfig config() {
        AppConfig config = new AppConfig();
        config.getCompression().setEnabled(true);
        config.getCompression().setThresholdPercent(0.5D);
        config.getCompression().setProtectHeadMessages(1);
        config.getCompression().setTailRatio(0.2D);
        config.getLlm().setContextWindowTokens(2000);
        return config;
    }

    private String repeat(String value, int count) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(value);
        }
        return buffer.toString();
    }
}
