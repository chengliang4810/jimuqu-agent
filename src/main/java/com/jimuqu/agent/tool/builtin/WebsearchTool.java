package com.jimuqu.agent.tool.builtin;

import com.jimuqu.agent.support.constants.ToolNameConstants;
import org.noear.solon.Utils;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.ToolResult;
import org.noear.solon.ai.mcp.client.McpClientProvider;
import org.noear.solon.ai.rag.Document;
import org.noear.solon.annotation.Param;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Exa MCP 的网页搜索工具。
 */
public class WebsearchTool {
    /**
     * 默认搜索结果数。
     */
    private static final int DEFAULT_NUM_RESULTS = 8;

    /**
     * 默认上下文字符数。
     */
    private static final int DEFAULT_CONTEXT_CHARS = 10000;

    /**
     * 默认实时抓取策略。
     */
    private static final String DEFAULT_LIVECRAWL = "fallback";

    /**
     * 默认搜索类型。
     */
    private static final String DEFAULT_TYPE = "auto";

    /**
     * 单例实例。
     */
    private static final WebsearchTool INSTANCE = new WebsearchTool();

    /**
     * Exa MCP 客户端。
     */
    private final McpClientProvider mcpClient;

    /**
     * 获取单例实例。
     */
    public static WebsearchTool getInstance() {
        return INSTANCE;
    }

    /**
     * 构造搜索工具。
     */
    public WebsearchTool() {
        this.mcpClient = ExaAiClient.getMcpClient();
    }

    /**
     * 执行网页搜索。
     */
    @ToolMapping(name = ToolNameConstants.WEBSEARCH, description = "执行实时网页搜索")
    public Document websearch(
            @Param(name = "query", description = "搜索关键词") String query,
            @Param(name = "numResults", required = false, defaultValue = "8", description = "返回结果数量") Integer numResults,
            @Param(name = "livecrawl", required = false, defaultValue = "fallback", description = "实时抓取策略") String livecrawl,
            @Param(name = "type", required = false, defaultValue = "auto", description = "搜索类型") String type,
            @Param(name = "contextMaxCharacters", required = false, defaultValue = "10000", description = "最大上下文字符数") Integer contextMaxCharacters
    ) throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("query", query);
        args.put("numResults", numResults == null ? DEFAULT_NUM_RESULTS : numResults);
        args.put("livecrawl", livecrawl == null ? DEFAULT_LIVECRAWL : livecrawl);
        args.put("type", type == null ? DEFAULT_TYPE : type);
        args.put("contextMaxCharacters", contextMaxCharacters == null ? DEFAULT_CONTEXT_CHARS : contextMaxCharacters);

        ToolResult result = mcpClient.callTool("web_search_exa", args);
        if (result.isError()) {
            String errorMessage = Utils.isNotEmpty(result.getContent()) ? result.getContent() : "Search service error";
            throw new RuntimeException(errorMessage);
        }

        if (Utils.isNotEmpty(result.getContent())) {
            return new Document()
                    .title("Web search: " + query)
                    .content(result.getContent())
                    .metadata("query", query)
                    .metadata("type", type)
                    .metadata("source", "exa.ai");
        }

        return new Document()
                .title("Web search: " + query)
                .content("No search results found. Please try a different query.");
    }
}
