package com.jimuqu.agent.tool.builtin;

import org.noear.solon.Utils;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.ToolResult;
import org.noear.solon.ai.mcp.client.McpClientProvider;
import org.noear.solon.ai.rag.Document;
import org.noear.solon.annotation.Param;

import java.util.HashMap;
import java.util.Map;

public class WebsearchTool {
    private static final int DEFAULT_NUM_RESULTS = 8;
    private static final int DEFAULT_CONTEXT_CHARS = 10000;
    private static final String DEFAULT_LIVECRAWL = "fallback";
    private static final String DEFAULT_TYPE = "auto";

    private static final WebsearchTool INSTANCE = new WebsearchTool();

    public static WebsearchTool getInstance() {
        return INSTANCE;
    }

    private final McpClientProvider mcpClient;

    public WebsearchTool() {
        this.mcpClient = ExaAiClient.getMcpClient();
    }

    @ToolMapping(name = "websearch", description = "执行实时 web 搜索")
    public Document websearch(
            @Param(name = "query", description = "查询关键字") String query,
            @Param(name = "numResults", required = false, defaultValue = "8", description = "返回结果数量") Integer numResults,
            @Param(name = "livecrawl", required = false, defaultValue = "fallback", description = "实时爬行模式") String livecrawl,
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
