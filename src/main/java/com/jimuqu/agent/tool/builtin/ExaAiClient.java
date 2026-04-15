package com.jimuqu.agent.tool.builtin;

import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.time.Duration;

/**
 * Exa MCP 客户端持有器。
 */
final class ExaAiClient {
    /**
     * Exa MCP 服务地址。
     */
    private static final String BASE_URL = "https://mcp.exa.ai/mcp";

    /**
     * 默认请求超时，单位毫秒。
     */
    private static final int TIMEOUT_MS = 30000;

    /**
     * 延迟初始化后的客户端提供器。
     */
    private static McpClientProvider provider;

    private ExaAiClient() {
    }

    /**
     * 获取单例 MCP 客户端。
     */
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
