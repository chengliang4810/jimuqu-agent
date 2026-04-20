package com.jimuqu.agent.gateway.service;

import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.MemoryManager;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.support.constants.GatewayCommandConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 网关主入口服务，负责把消息分流到授权、命令和对话主链。
 */
@RequiredArgsConstructor
public class DefaultGatewayService {
    /**
     * 网关日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultGatewayService.class);

    /**
     * 渠道消息去重窗口，单位毫秒。
     */
    private static final long DUPLICATE_WINDOW_MILLIS = 10L * 60L * 1000L;

    /**
     * 命令服务。
     */
    private final CommandService commandService;

    /**
     * 对话编排器。
     */
    private final ConversationOrchestrator conversationOrchestrator;

    /**
     * 渠道投递服务。
     */
    private final DeliveryService deliveryService;

    /**
     * 会话仓储。
     */
    private final SessionRepository sessionRepository;

    /**
     * 授权服务。
     */
    private final GatewayAuthorizationService gatewayAuthorizationService;

    /**
     * 任务后自动学习服务。
     */
    private final SkillLearningService skillLearningService;

    /**
     * 记忆管理器。
     */
    private final MemoryManager memoryManager;

    /**
     * 进程内最近已处理的消息键，用于抑制渠道重复投递。
     */
    private final ConcurrentMap<String, Long> recentMessageKeys = new ConcurrentHashMap<String, Long>();

    /**
     * 处理单条统一网关消息。
     *
     * @param message 渠道统一消息
     * @return 网关处理结果
     */
    public GatewayReply handle(GatewayMessage message) throws Exception {
        if (message == null) {
            return GatewayReply.error("消息体不能为空。");
        }

        pruneDuplicateKeys();
        String messageKey = messageKey(message);
        if (isDuplicate(messageKey)) {
            log.info("Ignore duplicate gateway message: {}", messageKey);
            return null;
        }

        boolean authorized = false;
        try {
            GatewayReply preAuth = gatewayAuthorizationService.preAuthorize(message);
            if (preAuth != null) {
                safeDeliver(message, preAuth.getContent());
                return preAuth;
            }

            String text = message.getText() == null ? "" : message.getText().trim();
            authorized = gatewayAuthorizationService.isAuthorized(message);
            if (!authorized) {
                return null;
            }

            GatewayReply reply;
            if (text.startsWith(GatewayCommandConstants.COMMAND_PREFIX)) {
                reply = commandService.handle(message, text);
                if (reply != null) {
                    reply.setCommandHandled(true);
                }
            } else {
                reply = conversationOrchestrator.handleIncoming(message);
            }

            if (reply != null) {
                safeDeliver(message, reply.getContent());
                safeScheduleLearning(message, reply);
            }
            return reply;
        } catch (Exception e) {
            if (messageKey != null) {
                recentMessageKeys.remove(messageKey);
            }
            log.warn("Gateway handle failed: platform={}, chatId={}, userId={}, text={}",
                    message.getPlatform(),
                    message.getChatId(),
                    message.getUserId(),
                    message.getText(),
                    e);
            GatewayReply errorReply = GatewayReply.error("处理消息失败：" + safeMessage(e));
            if (authorized) {
                safeDeliver(message, errorReply.getContent());
            }
            return errorReply;
        }
    }

    /**
     * 安全投递当前回复，不让渠道发送失败打断主链。
     */
    private void safeDeliver(GatewayMessage message, String content) {
        if (content == null || content.trim().length() == 0) {
            return;
        }
        try {
            DeliveryRequest request = new DeliveryRequest();
            request.setPlatform(message.getPlatform());
            request.setChatId(message.getChatId());
            request.setUserId(message.getUserId());
            request.setChatType(message.getChatType());
            request.setThreadId(message.getThreadId());
            request.setText(content);
            deliveryService.deliver(request);
        } catch (Exception e) {
            log.warn("Gateway delivery failed: platform={}, chatId={}, userId={}",
                    message.getPlatform(),
                    message.getChatId(),
                    message.getUserId(),
                    e);
        }
    }

    /**
     * 安全触发后台学习，不让后台线程调度问题影响当前回复。
     */
    private void safeScheduleLearning(GatewayMessage message, GatewayReply reply) {
        if (reply == null || reply.isCommandHandled() || reply.isError() || reply.getSessionId() == null) {
            return;
        }
        try {
            if (memoryManager != null) {
                memoryManager.syncTurn(message.sourceKey(), message.getText(), reply.getContent());
            }
            SessionRecord session = sessionRepository.findById(reply.getSessionId());
            if (session != null) {
                skillLearningService.schedulePostReplyLearning(session, message, reply);
            }
        } catch (Exception e) {
            log.warn("Post-reply learning schedule failed: sessionId={}", reply.getSessionId(), e);
        }
    }

    /**
     * 生成用于重复消息抑制的键。
     */
    private String messageKey(GatewayMessage message) {
        if (message.getThreadId() == null || message.getThreadId().trim().length() == 0) {
            return null;
        }
        return String.valueOf(message.getPlatform()) + ":" + message.getThreadId().trim();
    }

    /**
     * 记录并判断是否为重复消息。
     */
    private boolean isDuplicate(String messageKey) {
        if (messageKey == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long previous = recentMessageKeys.putIfAbsent(messageKey, now);
        if (previous == null) {
            return false;
        }
        if (now - previous < DUPLICATE_WINDOW_MILLIS) {
            return true;
        }
        recentMessageKeys.put(messageKey, now);
        return false;
    }

    /**
     * 清理过期的重复消息键，避免进程内表无限增长。
     */
    private void pruneDuplicateKeys() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, Long> entry : recentMessageKeys.entrySet()) {
            if (now - entry.getValue() >= DUPLICATE_WINDOW_MILLIS) {
                recentMessageKeys.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 提炼用户可见错误信息。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().length() == 0 ? e.getClass().getSimpleName() : message.trim();
    }
}
