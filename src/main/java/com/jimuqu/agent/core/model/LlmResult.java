package com.jimuqu.agent.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.noear.solon.ai.chat.message.AssistantMessage;

/**
 * 大模型调用结果封装。
 */
@Getter
@Setter
@NoArgsConstructor
public class LlmResult {
    /**
     * Solon AI 的助手消息对象。
     */
    private AssistantMessage assistantMessage;

    /**
     * 追加后的会话 NDJSON。
     */
    private String ndjson;

    /**
     * 是否通过流式模式生成。
     */
    private boolean streamed;

    /**
     * 原始协议响应。
     */
    private String rawResponse;
}
