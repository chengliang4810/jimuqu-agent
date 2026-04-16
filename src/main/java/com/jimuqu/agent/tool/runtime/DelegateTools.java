package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.DelegationResult;
import com.jimuqu.agent.core.model.DelegationTask;
import com.jimuqu.agent.core.service.DelegationService;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * 子代理委托工具。
 */
public class DelegateTools {
    /**
     * 委托服务。
     */
    private final DelegationService delegationService;

    /**
     * 当前来源键。
     */
    private final String sourceKey;

    /**
     * 构造委托工具。
     */
    public DelegateTools(DelegationService delegationService, String sourceKey) {
        this.delegationService = delegationService;
        this.sourceKey = sourceKey;
    }

    /**
     * 支持单任务与批量委托。
     */
    @ToolMapping(name = "delegate_task", description = "Delegate a subtask. mode supports single or batch. batch mode accepts tasks as JSON array.")
    public String delegateTask(String mode, String prompt, String tasks, String context) throws Exception {
        if (delegationService == null) {
            return "Delegate tool is not ready";
        }

        if ("batch".equalsIgnoreCase(mode)) {
            List<DelegationTask> items = parseTasks(tasks);
            List<DelegationResult> results = delegationService.delegateBatch(sourceKey, items);
            return ONode.serialize(results);
        }

        DelegationResult result = delegationService.delegateSingle(sourceKey, prompt, context);
        return result.getContent();
    }

    /**
     * 解析批量任务 JSON。
     */
    private List<DelegationTask> parseTasks(String tasks) {
        List<DelegationTask> items = new ArrayList<DelegationTask>();
        if (StrUtil.isBlank(tasks)) {
            return items;
        }
        ONode node = ONode.ofJson(tasks);
        if (!node.isArray()) {
            return items;
        }
        for (int i = 0; i < node.size(); i++) {
            ONode item = node.get(i);
            DelegationTask task = new DelegationTask();
            task.setName(item.get("name").getString());
            task.setPrompt(item.get("prompt").getString());
            task.setContext(item.get("context").getString());
            items.add(task);
        }
        return items;
    }
}
