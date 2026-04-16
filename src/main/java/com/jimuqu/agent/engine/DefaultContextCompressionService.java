package com.jimuqu.agent.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.support.constants.CompressionConstants;
import com.jimuqu.agent.support.MessageSupport;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认上下文压缩服务。
 */
@RequiredArgsConstructor
public class DefaultContextCompressionService implements ContextCompressionService {
    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    @Override
    public SessionRecord compressIfNeeded(SessionRecord session, String systemPrompt, String userMessage) throws Exception {
        if (!appConfig.getCompression().isEnabled()) {
            return session;
        }

        int contextWindow = Math.max(1024, appConfig.getLlm().getContextWindowTokens());
        int threshold = (int) (contextWindow * appConfig.getCompression().getThresholdPercent());
        int estimatedTokens = estimateTokens(systemPrompt) + estimateTokens(userMessage) + estimateTokens(session.getNdjson());
        if (shouldSkipForFailureCooldown(session)) {
            return session;
        }
        if (estimatedTokens < threshold) {
            return session;
        }
        if (shouldSkipForThrashing(session, estimatedTokens)) {
            return session;
        }

        session.setLastCompressionInputTokens(estimatedTokens);
        return compressNow(session, systemPrompt, null);
    }

    @Override
    public SessionRecord compressNow(SessionRecord session, String systemPrompt) throws Exception {
        return compressNow(session, systemPrompt, null);
    }

    @Override
    public SessionRecord compressNow(SessionRecord session, String systemPrompt, String focus) throws Exception {
        try {
            List<ChatMessage> history = MessageSupport.loadMessages(session.getNdjson());
            if (history.size() <= appConfig.getCompression().getProtectHeadMessages() + 1) {
                return session;
            }

            List<ChatMessage> normalized = new ArrayList<ChatMessage>();
            String previousSummary = StrUtil.nullToEmpty(session.getCompressedSummary()).trim();
            for (ChatMessage message : history) {
                if (message.getRole() == ChatRole.ASSISTANT
                        && StrUtil.startWithIgnoreCase(message.getContent(), CompressionConstants.SUMMARY_PREFIX)) {
                    if (StrUtil.isBlank(previousSummary)) {
                        previousSummary = message.getContent().trim();
                    }
                    continue;
                }
                normalized.add(message);
            }

            if (normalized.size() <= appConfig.getCompression().getProtectHeadMessages() + 1) {
                return session;
            }

            List<ChatMessage> pruned = pruneOldToolResults(normalized);
            int protectHead = Math.min(appConfig.getCompression().getProtectHeadMessages(), pruned.size());
            int protectTailStart = findTailStart(pruned);
            if (protectTailStart <= protectHead) {
                protectTailStart = Math.max(protectHead + 1, pruned.size() - 1);
                if (protectTailStart <= protectHead) {
                    return session;
                }
            }

            List<ChatMessage> head = new ArrayList<ChatMessage>(pruned.subList(0, protectHead));
            List<ChatMessage> middle = new ArrayList<ChatMessage>(pruned.subList(protectHead, protectTailStart));
            List<ChatMessage> tail = new ArrayList<ChatMessage>(pruned.subList(protectTailStart, pruned.size()));

            if (middle.isEmpty() || shouldSkipMiddleCompression(middle)) {
                return session;
            }

            String summaryBody = buildStructuredSummary(session, systemPrompt, middle, tail, previousSummary, focus);
            String summaryText = CompressionConstants.SUMMARY_PREFIX + "\n" + summaryBody;

            List<ChatMessage> compacted = new ArrayList<ChatMessage>();
            compacted.addAll(head);
            compacted.add(ChatMessage.ofAssistant(summaryText));
            compacted.addAll(tail);

            session.setCompressedSummary(summaryText);
            session.setNdjson(MessageSupport.toNdjson(compacted));
            session.setLastCompressionAt(System.currentTimeMillis());
            session.setCompressionFailureCount(0);
            session.setLastCompressionFailedAt(0L);
            session.setUpdatedAt(System.currentTimeMillis());
            return session;
        } catch (Exception e) {
            session.setCompressionFailureCount(session.getCompressionFailureCount() + 1);
            session.setLastCompressionFailedAt(System.currentTimeMillis());
            return session;
        }
    }

    /**
     * 对较早的工具结果做预裁剪。
     */
    private List<ChatMessage> pruneOldToolResults(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        int tailStart = findTailStart(messages);
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (i < tailStart
                    && message.getRole() == ChatRole.TOOL
                    && message.getContent() != null
                    && message.getContent().length() > 200) {
                result.add(ChatMessage.ofTool(
                        CompressionConstants.PRUNED_TOOL_PLACEHOLDER,
                        "tool",
                        "compacted-" + i
                ));
            } else {
                result.add(message);
            }
        }
        return result;
    }

    /**
     * 根据尾部 token 预算反推出应保护的 tail 起点。
     */
    private int findTailStart(List<ChatMessage> messages) {
        int contextWindow = Math.max(1024, appConfig.getLlm().getContextWindowTokens());
        int tailBudget = (int) (contextWindow * appConfig.getCompression().getTailRatio());
        int accumulated = 0;
        int start = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int tokens = estimateTokens(messages.get(i).getContent()) + 10;
            if (accumulated + tokens > tailBudget) {
                break;
            }
            accumulated += tokens;
            start = i;
        }
        return start;
    }

    /**
     * 生成结构化摘要。
     */
    private String buildStructuredSummary(SessionRecord session,
                                          String systemPrompt,
                                          List<ChatMessage> middle,
                                          List<ChatMessage> tail,
                                          String previousSummary,
                                          String focus) {
        String goal = extractFirstUserMessage(middle, tail);
        String progress = collectByRole(middle, ChatRole.ASSISTANT, 3);
        String decisions = collectKeywords(middle, new String[]{"决定", "改为", "使用", "切换", "采用"});
        String files = collectFileMentions(middle);
        String nextSteps = collectByRole(tail, ChatRole.USER, 1);

        StringBuilder buffer = new StringBuilder();
        if (StrUtil.isNotBlank(previousSummary)) {
            buffer.append("Previous Summary\n")
                    .append(trimContent(previousSummary.replace(CompressionConstants.SUMMARY_PREFIX, "").trim(), 600))
                    .append("\n\n");
        }
        if (StrUtil.isNotBlank(focus)) {
            buffer.append("Focus\n").append(trimContent(focus, 200)).append("\n\n");
        }
        buffer.append("Goal\n").append(StrUtil.blankToDefault(goal, inferGoalFromPrompt(systemPrompt))).append("\n\n");
        buffer.append("Progress\n").append(StrUtil.blankToDefault(progress, "已对较早轮次进行压缩，后续请基于当前文件状态继续。")).append("\n\n");
        buffer.append("Decisions\n").append(StrUtil.blankToDefault(decisions, "未提取到明确决策，请结合当前工程状态判断。")).append("\n\n");
        buffer.append("Files\n").append(StrUtil.blankToDefault(files, "未提取到明确文件列表。")).append("\n\n");
        buffer.append("Next Steps\n").append(StrUtil.blankToDefault(nextSteps, "继续处理最近用户要求，并避免重复之前已完成的工作。"));
        return buffer.toString().trim();
    }

    /**
     * 提取第一条用户目标。
     */
    private String extractFirstUserMessage(List<ChatMessage> middle, List<ChatMessage> tail) {
        for (ChatMessage message : middle) {
            if (message.getRole() == ChatRole.USER && StrUtil.isNotBlank(message.getContent())) {
                return trimContent(message.getContent(), 240);
            }
        }
        for (ChatMessage message : tail) {
            if (message.getRole() == ChatRole.USER && StrUtil.isNotBlank(message.getContent())) {
                return trimContent(message.getContent(), 240);
            }
        }
        return "";
    }

    /**
     * 按角色收集最近若干条消息。
     */
    private String collectByRole(List<ChatMessage> messages, ChatRole role, int maxItems) {
        StringBuilder buffer = new StringBuilder();
        int count = 0;
        for (int i = messages.size() - 1; i >= 0 && count < maxItems; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != role || StrUtil.isBlank(message.getContent())) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.insert(0, '\n');
            }
            buffer.insert(0, "- " + trimContent(message.getContent(), 220));
            count++;
        }
        return buffer.toString();
    }

    /**
     * 收集中间消息中的关键决策文本。
     */
    private String collectKeywords(List<ChatMessage> messages, String[] keywords) {
        StringBuilder buffer = new StringBuilder();
        for (ChatMessage message : messages) {
            String content = StrUtil.nullToEmpty(message.getContent());
            for (String keyword : keywords) {
                if (content.contains(keyword)) {
                    if (buffer.length() > 0) {
                        buffer.append('\n');
                    }
                    buffer.append("- ").append(trimContent(content, 220));
                    break;
                }
            }
        }
        return buffer.toString();
    }

    /**
     * 归纳中间消息里出现的文件路径。
     */
    private String collectFileMentions(List<ChatMessage> messages) {
        StringBuilder buffer = new StringBuilder();
        for (ChatMessage message : messages) {
            String content = StrUtil.nullToEmpty(message.getContent());
            String[] parts = content.split("\\s+");
            for (String part : parts) {
                if (part.contains("/") || part.contains("\\")) {
                    if (buffer.indexOf(part) < 0) {
                        if (buffer.length() > 0) {
                            buffer.append('\n');
                        }
                        buffer.append("- ").append(trimContent(part, 180));
                    }
                }
            }
        }
        return buffer.toString();
    }

    /**
     * 从当前系统提示词回推目标。
     */
    private String inferGoalFromPrompt(String systemPrompt) {
        if (StrUtil.isBlank(systemPrompt)) {
            return "继续当前任务。";
        }
        return trimContent(systemPrompt, 160);
    }

    /**
     * 压缩失败冷却期内直接跳过。
     */
    private boolean shouldSkipForFailureCooldown(SessionRecord session) {
        return session.getLastCompressionFailedAt() > 0
                && System.currentTimeMillis() - session.getLastCompressionFailedAt() < CompressionConstants.FAILURE_COOLDOWN_MILLIS;
    }

    /**
     * 压缩后短时间内若上下文增长不明显，则跳过重压缩。
     */
    private boolean shouldSkipForThrashing(SessionRecord session, int estimatedTokens) {
        if (session.getLastCompressionAt() <= 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - session.getLastCompressionAt();
        if (elapsed >= CompressionConstants.RECOMPRESS_COOLDOWN_MILLIS) {
            return false;
        }
        return estimatedTokens <= session.getLastCompressionInputTokens() + CompressionConstants.MIN_RECOMPRESS_DELTA_TOKENS;
    }

    /**
     * 如果中间区间已经只剩占位内容，则无需继续压缩。
     */
    private boolean shouldSkipMiddleCompression(List<ChatMessage> middle) {
        for (ChatMessage message : middle) {
            String content = StrUtil.nullToEmpty(message.getContent()).trim();
            if (content.length() == 0) {
                continue;
            }
            if (CompressionConstants.PRUNED_TOOL_PLACEHOLDER.equals(content)) {
                continue;
            }
            if (StrUtil.startWithIgnoreCase(content, CompressionConstants.SUMMARY_PREFIX)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * 粗略估算 token。
     */
    private int estimateTokens(String content) {
        if (StrUtil.isBlank(content)) {
            return 0;
        }
        return Math.max(1, content.length() / CompressionConstants.CHARS_PER_TOKEN);
    }

    /**
     * 限长文本。
     */
    private String trimContent(String content, int maxLength) {
        String normalized = content.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
