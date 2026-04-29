package com.jimuqu.agent.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DelegationResult;
import com.jimuqu.agent.core.model.DelegationTask;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.support.IdSupport;
import com.jimuqu.agent.support.constants.ToolNameConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@RequiredArgsConstructor
public class DefaultDelegationService implements DelegationService {
    /**
     * 委托日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultDelegationService.class);

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
            ToolNameConstants.CRONJOB,
            ToolNameConstants.EXECUTE_PYTHON,
            ToolNameConstants.EXECUTE_JS
    );

    /**
     * 当前系统已知工具清单。
     */
    private static final List<String> ALL_TOOLS = Arrays.asList(
            ToolNameConstants.FILE_READ,
            ToolNameConstants.FILE_WRITE,
            ToolNameConstants.FILE_LIST,
            ToolNameConstants.FILE_DELETE,
            ToolNameConstants.EXECUTE_SHELL,
            ToolNameConstants.EXECUTE_PYTHON,
            ToolNameConstants.EXECUTE_JS,
            ToolNameConstants.GET_CURRENT_TIME,
            ToolNameConstants.DELEGATE_TASK,
            ToolNameConstants.MEMORY,
            ToolNameConstants.SESSION_SEARCH,
            ToolNameConstants.SKILLS_LIST,
            ToolNameConstants.SKILL_VIEW,
            ToolNameConstants.SKILL_MANAGE,
            ToolNameConstants.SEND_MESSAGE,
            ToolNameConstants.CRONJOB,
            ToolNameConstants.CODESEARCH,
            ToolNameConstants.WEBSEARCH,
            ToolNameConstants.WEBFETCH
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

    @Override
    public DelegationResult delegateSingle(String sourceKey, String prompt, String context) throws Exception {
        DelegationTask task = new DelegationTask();
        task.setName("delegate");
        task.setPrompt(prompt);
        task.setContext(context);
        return delegateSingle(sourceKey, task);
    }

    @Override
    public DelegationResult delegateSingle(String sourceKey, DelegationTask task) throws Exception {
        String prompt = task == null ? null : task.getPrompt();
        if (StrUtil.isBlank(prompt)) {
            return failureResult("delegate", "委托任务不能为空。");
        }

        try {
            SessionRecord parentSession = sessionRepository.getBoundSession(sourceKey);
            String childSourceKey = sourceKey + ":delegate:" + IdSupport.newId();
            cloneToolVisibility(sourceKey, childSourceKey);
            applyAllowedTools(childSourceKey, task == null ? null : task.getAllowedTools());
            applyBlockedTools(childSourceKey);
            prepareChildSession(childSourceKey, parentSession);

            if (conversationHolder.get() == null) {
                return failureResult("delegate", "Conversation orchestrator is not ready");
            }
            GatewayMessage message = new GatewayMessage(PlatformType.MEMORY, "", "", decoratePrompt(task));
            message.setSourceKeyOverride(childSourceKey);
            GatewayReply reply = conversationHolder.get().handleIncoming(message);

            DelegationResult result = new DelegationResult();
            result.setName(StrUtil.blankToDefault(task == null ? null : task.getName(), "delegate"));
            result.setSessionId(reply == null ? null : reply.getSessionId());
            result.setContent(reply == null ? "" : reply.getContent());
            result.setError(reply != null && reply.isError());
            return result;
        } catch (Exception e) {
            log.warn("delegateSingle failed: sourceKey={}, prompt={}", sourceKey, prompt, e);
            return failureResult("delegate", e.getMessage());
        }
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
                        DelegationResult result = delegateSingle(sourceKey, task);
                        result.setName(StrUtil.blankToDefault(task.getName(), "delegate"));
                        return result;
                    }
                }));
            }

            for (Future<DelegationResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    log.warn("delegateBatch child failed: sourceKey={}", sourceKey, e);
                    results.add(failureResult("delegate", e.getMessage()));
                }
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 复制父来源键的工具可见性。
     */
    private void cloneToolVisibility(String parentSourceKey, String childSourceKey) throws Exception {
        for (String toolName : ALL_TOOLS) {
            boolean enabled = preferenceStore.isToolEnabled(parentSourceKey, toolName);
            preferenceStore.setToolEnabled(childSourceKey, toolName, enabled);
        }
    }

    /**
     * 对子会话应用固定黑名单。
     */
    private void applyBlockedTools(String childSourceKey) throws Exception {
        for (String blockedTool : BLOCKED_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, blockedTool, false);
        }
    }

    private void applyAllowedTools(String childSourceKey, List<String> allowedTools) throws Exception {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return;
        }
        for (String toolName : ALL_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, toolName, false);
        }
        for (String toolName : allowedTools) {
            if (toolName != null && ALL_TOOLS.contains(toolName.trim())) {
                preferenceStore.setToolEnabled(childSourceKey, toolName.trim(), true);
            }
        }
    }

    /**
     * 预先创建子会话并写入父会话关系。
     */
    private void prepareChildSession(String childSourceKey, SessionRecord parentSession) throws Exception {
        SessionRecord existing = sessionRepository.getBoundSession(childSourceKey);
        if (existing != null) {
            return;
        }

        SessionRecord childSession = sessionRepository.bindNewSession(childSourceKey);
        if (parentSession != null) {
            childSession.setParentSessionId(parentSession.getSessionId());
        }
        sessionRepository.save(childSession);
    }

    /**
     * 拼接委托上下文。
     */
    private String decoratePrompt(DelegationTask task) {
        String prompt = task == null ? "" : task.getPrompt();
        String context = task == null ? "" : task.getContext();
        StringBuilder buffer = new StringBuilder();
        buffer.append("任务目标:\n").append(prompt);
        if (StrUtil.isBlank(context)) {
            context = "";
        }
        if (StrUtil.isNotBlank(context)) {
            buffer.append("\n\n补充上下文:\n").append(context);
        }
        if (task != null && StrUtil.isNotBlank(task.getExpectedOutput())) {
            buffer.append("\n\n期望输出:\n").append(task.getExpectedOutput());
        }
        if (task != null && StrUtil.isNotBlank(task.getWriteScope())) {
            buffer.append("\n\n写入范围:\n").append(task.getWriteScope());
        }
        return buffer.toString();
    }

    /**
     * 构造失败结果，避免单个子任务异常打断整个批次。
     */
    private DelegationResult failureResult(String name, String message) {
        DelegationResult result = new DelegationResult();
        result.setName(StrUtil.blankToDefault(name, "delegate"));
        result.setError(true);
        result.setContent(StrUtil.blankToDefault(message, "delegation failed"));
        return result;
    }
}
