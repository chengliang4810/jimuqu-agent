package com.jimuqu.agent.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DelegationResult;
import com.jimuqu.agent.core.model.DelegationTask;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.support.IdSupport;
import com.jimuqu.agent.support.constants.ToolNameConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 默认子代理委托服务。
 */
public class DefaultDelegationService implements DelegationService {
    /**
     * 默认最大并行数。
     */
    private static final int MAX_CONCURRENT = 3;

    /**
     * 子代理固定禁用的工具。
     */
    private static final List<String> BLOCKED_TOOLS = Arrays.asList(
            ToolNameConstants.DELEGATE_TASK,
            ToolNameConstants.MEMORY,
            ToolNameConstants.SEND_MESSAGE,
            ToolNameConstants.APPROVAL,
            ToolNameConstants.CRONJOB,
            ToolNameConstants.EXECUTE_CODE
    );

    /**
     * 对话编排器。
     */
    private final ConversationOrchestratorHolder conversationHolder;

    /**
     * 工具注册表。
     */
    private final SqlitePreferenceStore preferenceStore;

    /**
     * 会话仓储。
     */
    private final SessionRepository sessionRepository;

    /**
     * 构造委托服务。
     */
    public DefaultDelegationService(ConversationOrchestratorHolder conversationHolder,
                                    SqlitePreferenceStore preferenceStore,
                                    SessionRepository sessionRepository) {
        this.conversationHolder = conversationHolder;
        this.preferenceStore = preferenceStore;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public DelegationResult delegateSingle(String sourceKey, String prompt, String context) throws Exception {
        String childSourceKey = sourceKey + ":delegate:" + IdSupport.newId();
        for (String blockedTool : BLOCKED_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, blockedTool, false);
        }

        if (conversationHolder.get() == null) {
            throw new IllegalStateException("Conversation orchestrator is not ready");
        }
        GatewayMessage message = new GatewayMessage(PlatformType.MEMORY, "", "", decoratePrompt(prompt, context));
        message.setSourceKeyOverride(childSourceKey);
        GatewayReply reply = conversationHolder.get().handleIncoming(message);

        DelegationResult result = new DelegationResult();
        result.setName("delegate");
        result.setSessionId(reply.getSessionId());
        result.setContent(reply.getContent());
        result.setError(reply.isError());
        return result;
    }

    @Override
    public List<DelegationResult> delegateBatch(final String sourceKey, List<DelegationTask> tasks) throws Exception {
        List<DelegationResult> results = new ArrayList<DelegationResult>();
        if (tasks == null || tasks.isEmpty()) {
            return results;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(MAX_CONCURRENT, tasks.size()));
        try {
            List<Future<DelegationResult>> futures = new ArrayList<Future<DelegationResult>>();
            for (final DelegationTask task : tasks) {
                futures.add(executorService.submit(new Callable<DelegationResult>() {
                    @Override
                    public DelegationResult call() throws Exception {
                        DelegationResult result = delegateSingle(sourceKey, task.getPrompt(), task.getContext());
                        result.setName(StrUtil.blankToDefault(task.getName(), "delegate"));
                        return result;
                    }
                }));
            }

            for (Future<DelegationResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 拼接委托上下文。
     */
    private String decoratePrompt(String prompt, String context) {
        if (StrUtil.isBlank(context)) {
            return prompt;
        }
        return "任务目标:\n" + prompt + "\n\n补充上下文:\n" + context;
    }
}
