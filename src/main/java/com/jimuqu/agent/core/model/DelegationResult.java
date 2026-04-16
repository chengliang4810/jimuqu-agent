package com.jimuqu.agent.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 子代理执行结果。
 */
@Getter
@Setter
@NoArgsConstructor
public class DelegationResult {
    /**
     * 任务名称。
     */
    private String name;

    /**
     * 子会话 ID。
     */
    private String sessionId;

    /**
     * 执行摘要。
     */
    private String content;

    /**
     * 是否失败。
     */
    private boolean error;
}
