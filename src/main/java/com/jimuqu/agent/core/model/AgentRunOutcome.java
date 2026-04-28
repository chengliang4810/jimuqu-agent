package com.jimuqu.agent.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Supervisor 执行结果。
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRunOutcome {
    private String finalReply;
    private LlmResult result;
    private AgentRunRecord runRecord;
}
