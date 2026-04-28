package com.jimuqu.agent.llm.dialect;

import cn.hutool.core.util.StrUtil;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatResponseDefault;
import org.noear.solon.ai.llm.dialect.openai.OpenaiChatDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs raw OpenAI chat-completions responses when parsing fails.
 */
public class LoggingOpenaiChatDialect extends OpenaiChatDialect {
    private static final Logger log = LoggerFactory.getLogger(LoggingOpenaiChatDialect.class);

    @Override
    public boolean parseResponseJson(ChatConfig config, ChatResponseDefault resp, String json) {
        try {
            return super.parseResponseJson(config, resp, json);
        } catch (RuntimeException e) {
            log.warn("Failed to parse openai raw response: provider={}, model={}, apiUrl={}, stream={}, bodyLength={}, bodyHexHead={}, body={}",
                    StrUtil.blankToDefault(config.getProvider(), ""),
                    StrUtil.blankToDefault(config.getModel(), ""),
                    StrUtil.blankToDefault(config.getApiUrl(), ""),
                    resp != null && resp.isStream(),
                    json == null ? 0 : json.length(),
                    RawResponseLogSupport.hexHead(json),
                    RawResponseLogSupport.preview(json),
                    e);
            throw e;
        }
    }
}
