package com.jimuqu.agent.core;

import org.noear.solon.ai.chat.message.AssistantMessage;

public class LlmResult {
    private AssistantMessage assistantMessage;
    private String ndjson;
    private boolean streamed;
    private String rawResponse;

    public AssistantMessage getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(AssistantMessage assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public String getNdjson() {
        return ndjson;
    }

    public void setNdjson(String ndjson) {
        this.ndjson = ndjson;
    }

    public boolean isStreamed() {
        return streamed;
    }

    public void setStreamed(boolean streamed) {
        this.streamed = streamed;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }
}
