package com.jimuqu.agent.llm.dialect;

import cn.hutool.core.util.StrUtil;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.ChatChoice;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatResponseDefault;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.llm.dialect.openai.OpenaiResponsesDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * 在 openai-responses 返回无法解析时先记录原始响应内容，便于排查上游异常返回。
 */
public class LoggingOpenaiResponsesDialect extends OpenaiResponsesDialect {
    private static final Logger log = LoggerFactory.getLogger(LoggingOpenaiResponsesDialect.class);

    @Override
    public boolean parseResponseJson(ChatConfig config, ChatResponseDefault resp, String json) {
        try {
            boolean reasoning = parseReasoningStreamDelta(resp, json);
            return super.parseResponseJson(config, resp, json) || reasoning;
        } catch (RuntimeException e) {
            log.warn("Failed to parse openai-responses raw response: provider={}, model={}, apiUrl={}, stream={}, bodyLength={}, bodyHexHead={}, body={}",
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

    private boolean parseReasoningStreamDelta(ChatResponseDefault resp, String json) {
        if (resp == null || !resp.isStream() || StrUtil.isBlank(json)) {
            return false;
        }
        boolean parsed = false;
        String[] lines = json.split("\n");
        for (String line : lines) {
            String candidate = StrUtil.trim(line);
            if (StrUtil.isBlank(candidate)) {
                continue;
            }
            if (candidate.startsWith("data:")) {
                candidate = StrUtil.trim(candidate.substring(5));
            } else if (candidate.startsWith("event:")) {
                continue;
            }
            if (StrUtil.isBlank(candidate) || "[DONE]".equals(candidate)) {
                continue;
            }
            try {
                ONode node = ONode.ofJson(candidate);
                String type = node.get("type").getString();
                if (StrUtil.isBlank(type) || !type.toLowerCase().contains("reasoning")) {
                    continue;
                }
                String delta = firstText(
                        node.get("delta").getString(),
                        node.get("text").getString(),
                        node.get("summary_text").getString()
                );
                ONode item = node.getOrNull("item");
                if (StrUtil.isBlank(delta) && item != null) {
                    delta = firstText(item.get("text").getString(), item.get("summary_text").getString());
                }
                if (StrUtil.isBlank(delta)) {
                    continue;
                }
                resp.reasoningBuilder.append(delta);
                resp.addChoice(new ChatChoice(0, new Date(), null, new AssistantMessage(delta, true)));
                parsed = true;
            } catch (Exception ignored) {
                // Let the upstream parser handle the event or report malformed JSON.
            }
        }
        return parsed;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
