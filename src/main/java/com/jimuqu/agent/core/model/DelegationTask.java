package com.jimuqu.agent.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 子代理委托任务。
 */
@Getter
@Setter
@NoArgsConstructor
public class DelegationTask {
    /**
     * 任务名称。
     */
    private String name;

    /**
     * 委托目标。
     */
    private String prompt;

    /**
     * 可选短上下文。
     */
    private String context;
}
