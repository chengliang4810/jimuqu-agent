package com.jimuqu.agent.gateway.command;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.CheckpointRecord;
import com.jimuqu.agent.core.model.CronJobRecord;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.service.ContextService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.support.CronSupport;
import com.jimuqu.agent.support.IdSupport;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.SourceKeySupport;
import com.jimuqu.agent.support.constants.AgentSettingConstants;
import com.jimuqu.agent.support.constants.GatewayCommandConstants;
import com.jimuqu.agent.tool.runtime.ProcessRegistry;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 默认 slash 命令实现，统一承接 Hermes 风格的会话控制命令。
 */
@RequiredArgsConstructor
public class DefaultCommandService implements CommandService {
    /**
     * 会话仓储。
     */
    private final SessionRepository sessionRepository;

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 本地技能服务。
     */
    private final LocalSkillService localSkillService;

    /**
     * 定时任务仓储。
     */
    private final CronJobRepository cronJobRepository;

    /**
     * 对话编排器。
     */
    private final ConversationOrchestrator conversationOrchestrator;

    /**
     * 上下文服务。
     */
    private final ContextService contextService;

    /**
     * 上下文压缩服务。
     */
    private final ContextCompressionService contextCompressionService;

    /**
     * 渠道投递服务。
     */
    private final DeliveryService deliveryService;

    /**
     * 授权服务。
     */
    private final GatewayAuthorizationService gatewayAuthorizationService;

    /**
     * checkpoint 服务。
     */
    private final CheckpointService checkpointService;

    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    /**
     * 全局设置仓储。
     */
    private final GlobalSettingRepository globalSettingRepository;

    /**
     * 进程注册表。
     */
    private final ProcessRegistry processRegistry;

    /**
     * 判断当前命令是否由默认命令服务承接。
     */
    @Override
    public boolean supports(String commandName) {
        return Arrays.asList(
                GatewayCommandConstants.COMMAND_NEW,
                GatewayCommandConstants.COMMAND_RESET,
                GatewayCommandConstants.COMMAND_RETRY,
                GatewayCommandConstants.COMMAND_UNDO,
                GatewayCommandConstants.COMMAND_BRANCH,
                GatewayCommandConstants.COMMAND_RESUME,
                GatewayCommandConstants.COMMAND_STATUS,
                GatewayCommandConstants.COMMAND_STOP,
                GatewayCommandConstants.COMMAND_PERSONALITY,
                GatewayCommandConstants.COMMAND_MODEL,
                GatewayCommandConstants.COMMAND_TOOLS,
                GatewayCommandConstants.COMMAND_SKILLS,
                GatewayCommandConstants.COMMAND_CRON,
                GatewayCommandConstants.COMMAND_PLATFORMS,
                GatewayCommandConstants.COMMAND_COMPRESS,
                GatewayCommandConstants.COMMAND_ROLLBACK,
                GatewayCommandConstants.COMMAND_SETHOME,
                GatewayCommandConstants.COMMAND_PAIRING,
                GatewayCommandConstants.COMMAND_HELP
        ).contains(commandName);
    }

    /**
     * 处理单条 slash 命令。
     */
    @Override
    public GatewayReply handle(GatewayMessage message, String commandLine) throws Exception {
        String withoutSlash = commandLine.substring(1).trim();
        String[] parts = withoutSlash.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (GatewayCommandConstants.COMMAND_NEW.equals(command)
                || GatewayCommandConstants.COMMAND_RESET.equals(command)) {
            SessionRecord created = sessionRepository.bindNewSession(message.sourceKey());
            GatewayReply reply = GatewayReply.ok("已创建新会话：" + created.getSessionId());
            reply.setSessionId(created.getSessionId());
            reply.setBranchName(created.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_RETRY.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String lastUser = MessageSupport.getLastUserMessage(session.getNdjson());
            if (StrUtil.isBlank(lastUser)) {
                return GatewayReply.error("没有可重试的上一条用户消息。");
            }
            session.setNdjson(MessageSupport.removeLastTurn(session.getNdjson()));
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);

            GatewayMessage retryMessage = new GatewayMessage(message.getPlatform(), message.getChatId(), message.getUserId(), lastUser);
            retryMessage.setThreadId(message.getThreadId());
            retryMessage.setChatType(message.getChatType());
            retryMessage.setChatName(message.getChatName());
            retryMessage.setUserName(message.getUserName());
            return conversationOrchestrator.handleIncoming(retryMessage);
        }

        if (GatewayCommandConstants.COMMAND_UNDO.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            session.setNdjson(MessageSupport.removeLastTurn(session.getNdjson()));
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
            GatewayReply reply = GatewayReply.ok("已从会话中移除上一轮对话：" + session.getSessionId());
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_BRANCH.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String branchName = StrUtil.isBlank(args) ? "branch-" + System.currentTimeMillis() : args;
            SessionRecord clone = sessionRepository.cloneSession(message.sourceKey(), session.getSessionId(), branchName);
            GatewayReply reply = GatewayReply.ok("已创建分支 " + branchName + " -> " + clone.getSessionId());
            reply.setSessionId(clone.getSessionId());
            reply.setBranchName(clone.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_RESUME.equals(command)) {
            if (StrUtil.isBlank(args)) {
                return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_RESUME + " <session-id-or-branch>");
            }
            SessionRecord session = sessionRepository.findById(args);
            if (session == null) {
                session = sessionRepository.findBySourceAndBranch(message.sourceKey(), args);
            }
            if (session == null) {
                return GatewayReply.error("未找到对应会话或分支：" + args);
            }
            sessionRepository.bindSource(message.sourceKey(), session.getSessionId());
            GatewayReply reply = GatewayReply.ok("已恢复会话：" + session.getSessionId());
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_STATUS.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            int count = MessageSupport.countMessages(session.getNdjson());
            GatewayReply reply = GatewayReply.ok(
                    "session=" + session.getSessionId()
                            + ", branch=" + session.getBranchName()
                            + ", messages=" + count
                            + ", model=" + StrUtil.nullToDefault(session.getModelOverride(), "default")
                            + ", personality=" + currentPersonalityName()
            );
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_STOP.equals(command)) {
            return GatewayReply.ok("已停止后台进程：" + processRegistry.stopAll() + " 个。");
        }

        if (GatewayCommandConstants.COMMAND_PERSONALITY.equals(command)) {
            return handlePersonality(args);
        }

        if (GatewayCommandConstants.COMMAND_MODEL.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            sessionRepository.setModelOverride(session.getSessionId(), args);
            GatewayReply reply = GatewayReply.ok("已切换模型覆盖配置为：" + args);
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_TOOLS.equals(command)) {
            return handleTools(message, args);
        }

        if (GatewayCommandConstants.COMMAND_SKILLS.equals(command)) {
            return handleSkills(message, args);
        }

        if (GatewayCommandConstants.COMMAND_SETHOME.equals(command)) {
            return gatewayAuthorizationService.setHome(message);
        }

        if (GatewayCommandConstants.COMMAND_PAIRING.equals(command)) {
            return handlePairing(message, args);
        }

        if (GatewayCommandConstants.COMMAND_CRON.equals(command)) {
            return handleCron(message, args);
        }

        if (GatewayCommandConstants.COMMAND_COMPRESS.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String systemPrompt = contextService.buildSystemPrompt(message.sourceKey());
            session.setSystemPromptSnapshot(systemPrompt);
            session = contextCompressionService.compressNow(session, systemPrompt, args);
            sessionRepository.save(session);
            GatewayReply reply = GatewayReply.ok(StrUtil.isBlank(args) ? "已完成当前会话的上下文压缩。" : "已按关注主题完成当前会话的上下文压缩。");
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_ROLLBACK.equals(command)) {
            if (StrUtil.isBlank(args)) {
                return GatewayReply.ok(formatCheckpointList(message.sourceKey()));
            }
            if ("latest".equalsIgnoreCase(args)) {
                return GatewayReply.ok("已回滚到最近一次 checkpoint：" + checkpointService.rollbackLatest(message.sourceKey()).getCheckpointId());
            }
            try {
                int index = Integer.parseInt(args);
                List<CheckpointRecord> recent = checkpointService.listRecent(message.sourceKey(), 10);
                if (index < 1 || index > recent.size()) {
                    return GatewayReply.error("checkpoint 序号无效，应在 1-" + recent.size() + " 之间。");
                }
                CheckpointRecord restored = checkpointService.rollback(recent.get(index - 1).getCheckpointId());
                return GatewayReply.ok("已按列表序号回滚到 checkpoint：" + restored.getCheckpointId());
            } catch (NumberFormatException ignored) {
                // fall through
            }
            return GatewayReply.ok("已回滚到指定 checkpoint：" + checkpointService.rollback(args).getCheckpointId());
        }

        if (GatewayCommandConstants.COMMAND_PLATFORMS.equals(command)) {
            return GatewayReply.ok(gatewayAuthorizationService.formatPlatformStatus(deliveryService.statuses()));
        }

        return GatewayReply.ok(helpText());
    }

    /**
     * 处理工具开关命令。
     */
    private GatewayReply handleTools(GatewayMessage message, String args) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0 || StrUtil.isBlank(parts[0]) || GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(parts[0])) {
            return GatewayReply.ok("工具列表：" + toolRegistry.listToolNames());
        }

        List<String> names = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            if (StrUtil.isNotBlank(parts[i])) {
                names.add(parts[i]);
            }
        }

        if (GatewayCommandConstants.ACTION_ENABLE.equalsIgnoreCase(parts[0])) {
            toolRegistry.enableTools(message.sourceKey(), names);
            return GatewayReply.ok("已启用工具：" + names);
        }

        if (GatewayCommandConstants.ACTION_DISABLE.equalsIgnoreCase(parts[0])) {
            toolRegistry.disableTools(message.sourceKey(), names);
            return GatewayReply.ok("已禁用工具：" + names);
        }

        return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_TOOLS + " [list|enable|disable] [name...]");
    }

    /**
     * 处理技能命令。
     */
    private GatewayReply handleSkills(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? GatewayCommandConstants.ACTION_LIST : parts[0];
        String target = parts.length > 1 ? parts[1].trim() : "";

        if (GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(action)) {
            return GatewayReply.ok("技能列表：" + localSkillService.listSkillNames());
        }
        if (GatewayCommandConstants.ACTION_ENABLE.equalsIgnoreCase(action)) {
            localSkillService.enable(message.sourceKey(), target);
            return GatewayReply.ok("已启用技能：" + target);
        }
        if (GatewayCommandConstants.ACTION_DISABLE.equalsIgnoreCase(action)) {
            localSkillService.disable(message.sourceKey(), target);
            return GatewayReply.ok("已禁用技能：" + target);
        }
        if (GatewayCommandConstants.ACTION_INSPECT.equalsIgnoreCase(action)) {
            return GatewayReply.ok(localSkillService.inspect(target));
        }
        if (GatewayCommandConstants.ACTION_RELOAD.equalsIgnoreCase(action)) {
            return GatewayReply.ok("已从 runtime 目录重新加载本地技能。");
        }

        return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_SKILLS + " [list|enable|disable|inspect|reload] [name]");
    }

    /**
     * 处理人格命令。
     */
    private GatewayReply handlePersonality(String args) throws Exception {
        Map<String, AppConfig.PersonalityConfig> personalities = appConfig.getAgent().getPersonalities();
        if (personalities == null || personalities.isEmpty()) {
            return GatewayReply.error("当前没有可用的人格配置。");
        }
        if (StrUtil.isBlank(args)) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("可用人格：\n");
            buffer.append("- none: 清除人格覆盖\n");
            for (Map.Entry<String, AppConfig.PersonalityConfig> entry : personalities.entrySet()) {
                String description = entry.getValue() == null ? "" : StrUtil.blankToDefault(entry.getValue().getDescription(), "无描述");
                buffer.append("- ").append(entry.getKey()).append(": ").append(description).append('\n');
            }
            buffer.append("当前激活：").append(currentPersonalityName());
            return GatewayReply.ok(buffer.toString().trim());
        }

        if ("none".equalsIgnoreCase(args) || "default".equalsIgnoreCase(args) || "neutral".equalsIgnoreCase(args)) {
            globalSettingRepository.remove(AgentSettingConstants.ACTIVE_PERSONALITY);
            return GatewayReply.ok("已清除人格覆盖，下一条消息恢复默认行为。");
        }

        String matchedName = null;
        for (String name : personalities.keySet()) {
            if (name.equalsIgnoreCase(args)) {
                matchedName = name;
                break;
            }
        }
        if (matchedName == null) {
            return GatewayReply.error("未知人格：" + args);
        }
        globalSettingRepository.set(AgentSettingConstants.ACTIVE_PERSONALITY, matchedName);
        return GatewayReply.ok("已切换人格为：" + matchedName + "，将从下一条消息开始生效。");
    }

    /**
     * 处理定时任务命令。
     */
    private GatewayReply handleCron(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? GatewayCommandConstants.ACTION_LIST : parts[0];
        String tail = parts.length > 1 ? parts[1] : "";

        if (GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(action)) {
            List<CronJobRecord> jobs = cronJobRepository.listBySource(message.sourceKey());
            StringBuilder buffer = new StringBuilder();
            for (CronJobRecord job : jobs) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(job.getJobId()).append(" ").append(job.getName()).append(" ").append(job.getStatus());
            }
            return GatewayReply.ok(buffer.length() == 0 ? "当前没有定时任务。" : buffer.toString());
        }

        if (GatewayCommandConstants.ACTION_CREATE.equalsIgnoreCase(action)) {
            String[] fields = tail.split("\\|", 3);
            if (fields.length < 3) {
                return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_CRON + " create <name>|<cron>|<prompt>");
            }

            long now = System.currentTimeMillis();
            String[] sourceParts = SourceKeySupport.split(message.sourceKey());
            CronJobRecord job = new CronJobRecord();
            job.setJobId(IdSupport.newId());
            job.setName(fields[0].trim());
            job.setCronExpr(fields[1].trim());
            job.setPrompt(fields[2].trim());
            job.setSourceKey(message.sourceKey());
            job.setDeliverPlatform(sourceParts[0]);
            job.setDeliverChatId(sourceParts[1]);
            job.setStatus("ACTIVE");
            job.setNextRunAt(CronSupport.nextRunAt(job.getCronExpr(), now));
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            cronJobRepository.save(job);
            return GatewayReply.ok("已创建定时任务：" + job.getJobId());
        }

        if (GatewayCommandConstants.ACTION_PAUSE.equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(tail, "PAUSED");
            return GatewayReply.ok("已暂停定时任务：" + tail);
        }

        if (GatewayCommandConstants.ACTION_RESUME.equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(tail, "ACTIVE");
            return GatewayReply.ok("已恢复定时任务：" + tail);
        }

        if (GatewayCommandConstants.ACTION_DELETE.equalsIgnoreCase(action)) {
            cronJobRepository.delete(tail);
            return GatewayReply.ok("已删除定时任务：" + tail);
        }

        if (GatewayCommandConstants.ACTION_RUN.equalsIgnoreCase(action)) {
            CronJobRecord job = cronJobRepository.findById(tail);
            if (job == null) {
                return GatewayReply.error("未找到定时任务：" + tail);
            }

            GatewayMessage synthetic = new GatewayMessage(message.getPlatform(), message.getChatId(), message.getUserId(), job.getPrompt());
            synthetic.setChatType(message.getChatType());
            synthetic.setChatName(message.getChatName());
            synthetic.setUserName(message.getUserName());
            GatewayReply reply = conversationOrchestrator.runScheduled(synthetic);
            deliveryService.deliver(SourceKeySupport.toDeliveryRequest(job.getSourceKey(), reply.getContent()));
            return GatewayReply.ok("已执行定时任务：" + tail);
        }

        return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_CRON + " [list|create|pause|resume|delete|run]");
    }

    /**
     * 处理 pairing 相关命令。
     */
    private GatewayReply handlePairing(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+");
        if (parts.length == 0 || StrUtil.isBlank(parts[0])) {
            return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_PAIRING + " [claim-admin|pending|approve|revoke|approved] ...");
        }
        String action = parts[0].trim().toLowerCase();

        if (GatewayCommandConstants.ACTION_CLAIM_ADMIN.equals(action)) {
            return gatewayAuthorizationService.claimAdmin(message);
        }

        PlatformType targetPlatform = message.getPlatform();
        if (parts.length >= 2) {
            targetPlatform = PlatformType.fromName(parts[1]);
        }

        if (GatewayCommandConstants.ACTION_PENDING.equals(action)) {
            return gatewayAuthorizationService.pairingPending(message, targetPlatform);
        }
        if (GatewayCommandConstants.ACTION_APPROVED.equals(action)) {
            return gatewayAuthorizationService.pairingApproved(message, targetPlatform);
        }
        if (GatewayCommandConstants.ACTION_APPROVE.equals(action)) {
            if (parts.length < 3) {
                return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_PAIRING + " approve <platform> <code>");
            }
            return gatewayAuthorizationService.pairingApprove(message, targetPlatform, parts[2]);
        }
        if (GatewayCommandConstants.ACTION_REVOKE.equals(action)) {
            if (parts.length < 3) {
                return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_PAIRING + " revoke <platform> <userId>");
            }
            return gatewayAuthorizationService.pairingRevoke(message, targetPlatform, parts[2]);
        }

        return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_PAIRING + " [claim-admin|pending|approve|revoke|approved] ...");
    }

    /**
     * 获取当前来源键的会话；若不存在则立即创建。
     */
    private SessionRecord requireSession(String sourceKey) throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(sourceKey);
        if (session == null) {
            session = sessionRepository.bindNewSession(sourceKey);
        }
        return session;
    }

    private String formatCheckpointList(String sourceKey) throws Exception {
        List<CheckpointRecord> checkpoints = checkpointService.listRecent(sourceKey, 10);
        if (checkpoints.isEmpty()) {
            return "当前来源键没有可回滚的 checkpoint。";
        }
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < checkpoints.size(); i++) {
            CheckpointRecord record = checkpoints.get(i);
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(i + 1)
                    .append(". ")
                    .append(record.getCheckpointId())
                    .append(" created=")
                    .append(DateUtil.formatDateTime(new java.util.Date(record.getCreatedAt())))
                    .append(", restored=")
                    .append(record.getRestoredAt() > 0 ? DateUtil.formatDateTime(new java.util.Date(record.getRestoredAt())) : "never");
            if (StrUtil.isNotBlank(record.getSessionId())) {
                buffer.append(", session=").append(record.getSessionId());
            }
        }
        return buffer.toString();
    }

    private String currentPersonalityName() {
        try {
            String value = globalSettingRepository.get(AgentSettingConstants.ACTIVE_PERSONALITY);
            return StrUtil.blankToDefault(value, "default");
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * 生成帮助文本。
     */
    private String helpText() {
        return GatewayCommandConstants.SLASH_NEW + "\n"
                + GatewayCommandConstants.SLASH_RESET + "\n"
                + GatewayCommandConstants.SLASH_RETRY + "\n"
                + GatewayCommandConstants.SLASH_UNDO + "\n"
                + GatewayCommandConstants.SLASH_BRANCH + " [name]\n"
                + GatewayCommandConstants.SLASH_RESUME + " <session-or-branch>\n"
                + GatewayCommandConstants.SLASH_STATUS + "\n"
                + GatewayCommandConstants.SLASH_STOP + "\n"
                + GatewayCommandConstants.SLASH_PERSONALITY + " [name]\n"
                + GatewayCommandConstants.SLASH_MODEL + " <provider:model>\n"
                + GatewayCommandConstants.SLASH_TOOLS + " [list|enable|disable] [name...]\n"
                + GatewayCommandConstants.SLASH_SKILLS + " [list|enable|disable|inspect|reload]\n"
                + GatewayCommandConstants.SLASH_CRON + " [list|create|pause|resume|delete|run]\n"
                + GatewayCommandConstants.SLASH_COMPRESS + " [focus]\n"
                + GatewayCommandConstants.SLASH_ROLLBACK + " [latest|checkpoint-id|number]\n"
                + GatewayCommandConstants.SLASH_SETHOME + "\n"
                + GatewayCommandConstants.SLASH_PAIRING + " [claim-admin|pending|approve|revoke|approved]\n"
                + GatewayCommandConstants.SLASH_PLATFORMS + "\n"
                + GatewayCommandConstants.SLASH_HELP;
    }
}
