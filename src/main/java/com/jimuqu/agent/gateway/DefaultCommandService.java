package com.jimuqu.agent.gateway;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.ChannelStatus;
import com.jimuqu.agent.core.CommandService;
import com.jimuqu.agent.core.ConversationOrchestrator;
import com.jimuqu.agent.core.CronJobRecord;
import com.jimuqu.agent.core.CronJobRepository;
import com.jimuqu.agent.core.DeliveryService;
import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.core.SessionRecord;
import com.jimuqu.agent.core.SessionRepository;
import com.jimuqu.agent.core.ToolRegistry;
import com.jimuqu.agent.support.CronSupport;
import com.jimuqu.agent.support.IdSupport;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.SourceKeySupport;

import java.util.ArrayList;
import java.util.List;

public class DefaultCommandService implements CommandService {
    private final SessionRepository sessionRepository;
    private final ToolRegistry toolRegistry;
    private final LocalSkillService localSkillService;
    private final CronJobRepository cronJobRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final DeliveryService deliveryService;
    private final GatewayAuthorizationService gatewayAuthorizationService;

    public DefaultCommandService(SessionRepository sessionRepository,
                                 ToolRegistry toolRegistry,
                                 LocalSkillService localSkillService,
                                 CronJobRepository cronJobRepository,
                                 ConversationOrchestrator conversationOrchestrator,
                                 DeliveryService deliveryService,
                                 GatewayAuthorizationService gatewayAuthorizationService) {
        this.sessionRepository = sessionRepository;
        this.toolRegistry = toolRegistry;
        this.localSkillService = localSkillService;
        this.cronJobRepository = cronJobRepository;
        this.conversationOrchestrator = conversationOrchestrator;
        this.deliveryService = deliveryService;
        this.gatewayAuthorizationService = gatewayAuthorizationService;
    }

    public boolean supports(String commandName) {
        return "new retry undo branch resume status model tools skills cron platforms sethome pairing help".contains(commandName);
    }

    public GatewayReply handle(GatewayMessage message, String commandLine) throws Exception {
        String withoutSlash = commandLine.substring(1).trim();
        String[] parts = withoutSlash.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        if ("new".equals(command)) {
            SessionRecord created = sessionRepository.bindNewSession(message.sourceKey());
            GatewayReply reply = GatewayReply.ok("已创建新会话：" + created.getSessionId());
            reply.setSessionId(created.getSessionId());
            reply.setBranchName(created.getBranchName());
            return reply;
        }

        if ("retry".equals(command)) {
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
            return conversationOrchestrator.handleIncoming(retryMessage);
        }

        if ("undo".equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            session.setNdjson(MessageSupport.removeLastTurn(session.getNdjson()));
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
            GatewayReply reply = GatewayReply.ok("已从会话中移除上一轮对话：" + session.getSessionId());
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if ("branch".equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String branchName = StrUtil.isBlank(args) ? "branch-" + System.currentTimeMillis() : args;
            SessionRecord clone = sessionRepository.cloneSession(message.sourceKey(), session.getSessionId(), branchName);
            GatewayReply reply = GatewayReply.ok("Created branch " + branchName + " -> " + clone.getSessionId());
            reply.setSessionId(clone.getSessionId());
            reply.setBranchName(clone.getBranchName());
            return reply;
        }

        if ("resume".equals(command)) {
            if (StrUtil.isBlank(args)) {
                return GatewayReply.error("用法：/resume <session-id-or-branch>");
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

        if ("status".equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            int count = MessageSupport.countMessages(session.getNdjson());
            GatewayReply reply = GatewayReply.ok("session=" + session.getSessionId() + ", branch=" + session.getBranchName() + ", messages=" + count + ", model=" + StrUtil.nullToDefault(session.getModelOverride(), "default"));
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if ("model".equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            sessionRepository.setModelOverride(session.getSessionId(), args);
            GatewayReply reply = GatewayReply.ok("已切换模型覆盖配置为：" + args);
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if ("tools".equals(command)) {
            return handleTools(message, args);
        }

        if ("skills".equals(command)) {
            return handleSkills(message, args);
        }

        if ("sethome".equals(command)) {
            return gatewayAuthorizationService.setHome(message);
        }

        if ("pairing".equals(command)) {
            return handlePairing(message, args);
        }

        if ("cron".equals(command)) {
            return handleCron(message, args);
        }

        if ("platforms".equals(command)) {
            return GatewayReply.ok(gatewayAuthorizationService.formatPlatformStatus(deliveryService.statuses()));
        }

        return GatewayReply.ok(helpText());
    }

    private GatewayReply handleTools(GatewayMessage message, String args) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0 || StrUtil.isBlank(parts[0]) || "list".equalsIgnoreCase(parts[0])) {
            return GatewayReply.ok("工具列表：" + toolRegistry.listToolNames());
        }

        List<String> names = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            if (StrUtil.isNotBlank(parts[i])) {
                names.add(parts[i]);
            }
        }

        if ("enable".equalsIgnoreCase(parts[0])) {
            toolRegistry.enableTools(message.sourceKey(), names);
            return GatewayReply.ok("已启用工具：" + names);
        }

        if ("disable".equalsIgnoreCase(parts[0])) {
            toolRegistry.disableTools(message.sourceKey(), names);
            return GatewayReply.ok("已禁用工具：" + names);
        }

        return GatewayReply.error("用法：/tools [list|enable|disable] [name...]");
    }

    private GatewayReply handleSkills(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? "list" : parts[0];
        String target = parts.length > 1 ? parts[1].trim() : "";
        if ("list".equalsIgnoreCase(action)) {
            return GatewayReply.ok("技能列表：" + localSkillService.listSkillNames());
        }
        if ("enable".equalsIgnoreCase(action)) {
            localSkillService.enable(message.sourceKey(), target);
            return GatewayReply.ok("已启用技能：" + target);
        }
        if ("disable".equalsIgnoreCase(action)) {
            localSkillService.disable(message.sourceKey(), target);
            return GatewayReply.ok("已禁用技能：" + target);
        }
        if ("inspect".equalsIgnoreCase(action)) {
            return GatewayReply.ok(localSkillService.inspect(target));
        }
        if ("reload".equalsIgnoreCase(action)) {
            return GatewayReply.ok("已从 runtime 目录重新加载本地技能。");
        }
        return GatewayReply.error("用法：/skills [list|enable|disable|inspect|reload] [name]");
    }

    private GatewayReply handleCron(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? "list" : parts[0];
        String tail = parts.length > 1 ? parts[1] : "";
        if ("list".equalsIgnoreCase(action)) {
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
        if ("create".equalsIgnoreCase(action)) {
            String[] fields = tail.split("\\|", 3);
            if (fields.length < 3) {
                return GatewayReply.error("用法：/cron create <name>|<cron>|<prompt>");
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
        if ("pause".equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(tail, "PAUSED");
            return GatewayReply.ok("已暂停定时任务：" + tail);
        }
        if ("resume".equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(tail, "ACTIVE");
            return GatewayReply.ok("已恢复定时任务：" + tail);
        }
        if ("delete".equalsIgnoreCase(action)) {
            cronJobRepository.delete(tail);
            return GatewayReply.ok("已删除定时任务：" + tail);
        }
        if ("run".equalsIgnoreCase(action)) {
            CronJobRecord job = cronJobRepository.findById(tail);
            if (job == null) {
                return GatewayReply.error("未找到定时任务：" + tail);
            }
            GatewayMessage synthetic = new GatewayMessage(message.getPlatform(), message.getChatId(), message.getUserId(), job.getPrompt());
            GatewayReply reply = conversationOrchestrator.runScheduled(synthetic);
            deliveryService.deliver(SourceKeySupport.toDeliveryRequest(job.getSourceKey(), reply.getContent()));
            return GatewayReply.ok("已执行定时任务：" + tail);
        }
        return GatewayReply.error("用法：/cron [list|create|pause|resume|delete|run]");
    }

    private GatewayReply handlePairing(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+");
        if (parts.length == 0 || StrUtil.isBlank(parts[0])) {
            return GatewayReply.error("用法：/pairing [claim-admin|pending|approve|revoke|approved] ...");
        }
        String action = parts[0].trim().toLowerCase();

        if ("claim-admin".equals(action)) {
            return gatewayAuthorizationService.claimAdmin(message);
        }

        PlatformType targetPlatform = message.getPlatform();
        if (parts.length >= 2) {
            targetPlatform = PlatformType.fromName(parts[1]);
        }

        if ("pending".equals(action)) {
            return gatewayAuthorizationService.pairingPending(message, targetPlatform);
        }

        if ("approved".equals(action)) {
            return gatewayAuthorizationService.pairingApproved(message, targetPlatform);
        }

        if ("approve".equals(action)) {
            if (parts.length < 3) {
                return GatewayReply.error("用法：/pairing approve <platform> <code>");
            }
            return gatewayAuthorizationService.pairingApprove(message, targetPlatform, parts[2]);
        }

        if ("revoke".equals(action)) {
            if (parts.length < 3) {
                return GatewayReply.error("用法：/pairing revoke <platform> <userId>");
            }
            return gatewayAuthorizationService.pairingRevoke(message, targetPlatform, parts[2]);
        }

        return GatewayReply.error("用法：/pairing [claim-admin|pending|approve|revoke|approved] ...");
    }

    private SessionRecord requireSession(String sourceKey) throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(sourceKey);
        if (session == null) {
            session = sessionRepository.bindNewSession(sourceKey);
        }
        return session;
    }

    private String helpText() {
        return "/new\n/retry\n/undo\n/branch [name]\n/resume <session-or-branch>\n/status\n/model <provider:model>\n/tools [list|enable|disable] [name...]\n/skills [list|enable|disable|inspect|reload]\n/cron [list|create|pause|resume|delete|run]\n/sethome\n/pairing [claim-admin|pending|approve|revoke|approved]\n/platforms\n/help";
    }
}
