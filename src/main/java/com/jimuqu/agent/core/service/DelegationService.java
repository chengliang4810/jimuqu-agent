package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.DelegationResult;
import com.jimuqu.agent.core.model.DelegationTask;

import java.util.List;

/**
 * 子代理委托服务接口。
 */
public interface DelegationService {
    /**
     * 单任务委托。
     */
    DelegationResult delegateSingle(String sourceKey, String prompt, String context) throws Exception;

    /**
     * 批量并行委托。
     */
    List<DelegationResult> delegateBatch(String sourceKey, List<DelegationTask> tasks) throws Exception;
}
