package com.jimuqu.agent.context;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.model.SkillDescriptor;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.support.MessageSupport;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主回复后的异步学习闭环服务。
 */
@RequiredArgsConstructor
public class AsyncSkillLearningService implements SkillLearningService {
    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final MemoryService memoryService;
    private final LocalSkillService localSkillService;
    private final CheckpointService checkpointService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void schedulePostReplyLearning(final SessionRecord session,
                                          final GatewayMessage message,
                                          final GatewayReply reply) throws Exception {
        if (!appConfig.getLearning().isEnabled() || session == null || reply == null || reply.isError()) {
            return;
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    int toolMessages = countToolMessages(session);
                    boolean hasRecentCheckpoint = checkpointService.hasRecentCheckpoint(
                            message.sourceKey(),
                            Math.max(session.getLastLearningAt(), session.getUpdatedAt() - 60_000L)
                    );

                    if (toolMessages < appConfig.getLearning().getToolCallThreshold() && !hasRecentCheckpoint) {
                        return;
                    }
                    runLearning(session, message, toolMessages, hasRecentCheckpoint);
                } catch (Exception ignored) {
                    // 学习失败不影响主回复。
                }
            }
        });
    }

    private void runLearning(SessionRecord session,
                             GatewayMessage message,
                             int toolMessages,
                             boolean hasRecentCheckpoint) throws Exception {
        if (toolMessages >= appConfig.getLearning().getToolCallThreshold()) {
            String skillName = inferSkillName(session);
            if (!skillExists(skillName)) {
                localSkillService.createSkill(skillName, null, buildSkillContent(session, message, hasRecentCheckpoint));
            }
        }

        session.setLastLearningAt(System.currentTimeMillis());
        sessionRepository.save(session);
    }

    private int countToolMessages(SessionRecord session) throws Exception {
        int count = 0;
        try {
            for (org.noear.solon.ai.chat.message.ChatMessage chatMessage : MessageSupport.loadMessages(session.getNdjson())) {
                if (chatMessage.getRole() == org.noear.solon.ai.chat.ChatRole.TOOL) {
                    count++;
                }
            }
        } catch (Exception ignored) {
            return 0;
        }
        return count;
    }

    private boolean skillExists(String skillName) throws Exception {
        List<SkillDescriptor> skills = localSkillService.listSkills(null);
        for (SkillDescriptor descriptor : skills) {
            if (descriptor.getName().equals(skillName)) {
                return true;
            }
        }
        return false;
    }

    private String inferSkillName(SessionRecord session) {
        String base = StrUtil.blankToDefault(session.getTitle(), "learned-workflow").toLowerCase();
        base = base.replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        base = base.replaceAll("^-+", "").replaceAll("-+$", "");
        return StrUtil.blankToDefault(base, "learned-workflow");
    }

    private String buildSkillContent(SessionRecord session, GatewayMessage message, boolean hasRecentCheckpoint) {
        String name = inferSkillName(session);
        String description = StrUtil.blankToDefault(session.getTitle(), "从复杂任务中沉淀出的可复用流程。");
        String progress = StrUtil.blankToDefault(session.getCompressedSummary(), replySafeExcerpt(session.getNdjson()));
        String nextStep = StrUtil.blankToDefault(message.getText(), "参考当前任务上下文继续执行。");

        StringBuilder buffer = new StringBuilder();
        buffer.append("---\n");
        buffer.append("name: ").append(name).append("\n");
        buffer.append("description: ").append(description).append("\n");
        buffer.append("---\n\n");
        buffer.append("# 触发条件\n");
        buffer.append("- 当遇到与本技能相似的复杂任务时使用。\n\n");
        buffer.append("# 执行步骤\n");
        buffer.append("1. 先确认当前任务目标与上下文是否匹配。\n");
        buffer.append("2. 参考下述已验证流程执行。\n");
        buffer.append("3. 结束后根据结果继续补充技能内容。\n\n");
        buffer.append("# 已验证流程\n");
        buffer.append(progress).append("\n\n");
        buffer.append("# Pitfalls\n");
        buffer.append("- 如上下文差异较大，先重新检查输入条件再复用。\n\n");
        buffer.append("# Verification\n");
        buffer.append("- 核对输出是否满足当前用户要求：").append(nextStep).append("\n");
        if (hasSessionHints(session, hasRecentCheckpoint)) {
            buffer.append("- 当前流程涉及结构化文件修改，执行前先确认 checkpoint 策略。\n");
        }
        return buffer.toString();
    }

    /**
     * 判断是否需要额外补充会话提示。
     */
    private boolean hasSessionHints(SessionRecord session, boolean hasRecentCheckpoint) {
        return hasRecentCheckpoint && session != null && StrUtil.isNotBlank(session.getTitle());
    }

    private String replySafeExcerpt(String ndjson) {
        String normalized = StrUtil.nullToEmpty(ndjson).replace('\n', ' ').trim();
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "...";
    }
}
