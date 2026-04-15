package com.jimuqu.agent.tool.builtin;

import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.time.Duration;

final class ExaAiClient {
    private static final String BASE_URL = "https://mcp.exa.ai/mcp";
    private static final int TIMEOUT_MS = 30000;

    private static McpClientProvider provider;

    private ExaAiClient() {
    }

    static synchronized McpClientProvider getMcpClient() {
        if (provider == null) {
            provider = McpClientProvider.builder()
                    .url(BASE_URL)
                    .channel(McpChannel.STREAMABLE)
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .build();
        }
        return provider;
    }
}
