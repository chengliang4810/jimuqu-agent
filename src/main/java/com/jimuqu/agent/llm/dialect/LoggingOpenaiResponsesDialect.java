package com.jimuqu.agent.llm.dialect;

import cn.hutool.core.util.StrUtil;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatResponseDefault;
import org.noear.solon.ai.llm.dialect.openai.OpenaiResponsesDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在 openai-responses 返回无法解析时先记录原始响应内容，便于排查上游异常返回。
 */
public class LoggingOpenaiResponsesDialect extends OpenaiResponsesDialect {
    private static final Logger log = LoggerFactory.getLogger(LoggingOpenaiResponsesDialect.class);
    private static final int MAX_LOG_BODY_LENGTH = 16000;

    @Override
    public boolean parseResponseJson(ChatConfig config, ChatResponseDefault resp, String json) {
        try {
            return super.parseResponseJson(config, resp, json);
        } catch (RuntimeException e) {
            log.warn("Failed to parse openai-responses raw response: provider={}, model={}, apiUrl={}, stream={}, bodyLength={}, body={}",
                    StrUtil.blankToDefault(config.getProvider(), ""),
                    StrUtil.blankToDefault(config.getModel(), ""),
                    StrUtil.blankToDefault(config.getApiUrl(), ""),
                    resp != null && resp.isStream(),
                    json == null ? 0 : json.length(),
                    formatBodyForLog(json),
                    e);
            throw e;
        }
    }

    private String formatBodyForLog(String json) {
        if (json == null) {
            return "<null>";
        }
        if (json.length() <= MAX_LOG_BODY_LENGTH) {
            return json;
        }
        return json.substring(0, MAX_LOG_BODY_LENGTH)
                + "\n...[truncated, totalLength=" + json.length() + "]";
    }
}
