package com.jimuqu.agent.tool.builtin;

import org.noear.solon.Utils;
import org.noear.solon.ai.chat.tool.AbsTool;
import org.noear.solon.ai.chat.tool.ToolResult;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CodeSearchTool extends AbsTool {
    private static final int DEFAULT_TOKENS = 5000;
    private static final CodeSearchTool INSTANCE = new CodeSearchTool();

    public static CodeSearchTool getInstance() {
        return INSTANCE;
    }

    private final McpClientProvider mcpClient;

    public CodeSearchTool() {
        this.mcpClient = ExaAiClient.getMcpClient();

        addParam("query", String.class, true, "搜索查询词");
        addParam("tokensNum", Integer.class, false, "返回的 Token 数量", "5000");
    }

    @Override
    public String name() {
        return "codesearch";
    }

    @Override
    public String description() {
        return "使用 Exa Code API 搜索并获取编程任务相关上下文";
    }

    @Override
    public Object handle(Map<String, Object> args0) throws Throwable {
        String query = (String) args0.get("query");
        Object tokensNumObj = args0.get("tokensNum");
        Integer tokensNum = null;
        if (tokensNumObj instanceof Number) {
            tokensNum = Integer.valueOf(((Number) tokensNumObj).intValue());
        }
        int finalTokens = tokensNum == null ? DEFAULT_TOKENS : tokensNum.intValue();

        Map<String, Object> toolArgs = new HashMap<String, Object>();
        toolArgs.put("query", query);
        toolArgs.put("tokensNum", finalTokens);

        ToolResult result = mcpClient.callTool("get_code_context_exa", toolArgs);
        if (result.isError()) {
            String errorText = Utils.isNotEmpty(result.getContent()) ? result.getContent() : "Unknown error";
            throw new RuntimeException("代码搜索出错: " + errorText);
        }

        String title = "Code search: " + query;
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        if (Utils.isNotEmpty(result.getContent())) {
            response.put("output", result.getContent());
            response.put("title", title);
            response.put("metadata", new HashMap<String, Object>());
        } else {
            response.put("output", "未找到相关的代码片段或文档。请尝试更换查询词。");
            response.put("title", title);
            response.put("metadata", new HashMap<String, Object>());
        }

        return response;
    }
}
