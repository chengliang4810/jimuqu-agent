package com.jimuqu.agent.storage.session;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import org.noear.solon.ai.agent.Agent;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.session.InMemoryAgentSession;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.flow.FlowContext;
import org.noear.solon.flow.FlowContextInternal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 基于现有 SessionRecord / SQLite 的 AgentSession 适配层。
 */
public class SqliteAgentSession implements AgentSession {
    private static final String META_PENDING = "_agent_pending_";
    private static final String META_PENDING_REASON = "_pending_reason_";

    private final SessionRecord sessionRecord;
    private final SessionRepository sessionRepository;
    private final InMemoryAgentSession cache;

    public SqliteAgentSession(SessionRecord sessionRecord) {
        this(sessionRecord, null);
    }

    public SqliteAgentSession(SessionRecord sessionRecord, SessionRepository sessionRepository) {
        if (sessionRecord == null || StrUtil.isBlank(sessionRecord.getSessionId())) {
            throw new IllegalArgumentException("SessionRecord with sessionId is required");
        }
        this.sessionRecord = sessionRecord;
        this.sessionRepository = sessionRepository;
        this.cache = new InMemoryAgentSession(loadContext(sessionRecord));
        this.cache.getContext().put(Agent.KEY_SESSION, this);
        loadMessages(sessionRecord);
    }

    @Override
    public String getSessionId() {
        return cache.getSessionId();
    }

    @Override
    public List<ChatMessage> getMessages() {
        return cache.getMessages();
    }

    @Override
    public List<ChatMessage> getLatestMessages(int windowSize) {
        return cache.getLatestMessages(windowSize);
    }

    @Override
    public void addMessage(Collection<? extends ChatMessage> messages) {
        cache.addMessage(messages);
        syncRecord(false);
    }

    @Override
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    @Override
    public void clear() {
        cache.clear();
        syncRecord(true);
    }

    @Override
    public Map<String, Object> attrs() {
        return cache.attrs();
    }

    @Override
    public void updateSnapshot() {
        syncRecord(true);
    }

    @Override
    public FlowContext getContext() {
        return cache.getContext();
    }

    @Override
    public void pending(boolean pending, String reason) {
        AgentSession.super.pending(pending, reason);
        cache.getContext().put(META_PENDING, pending);
        if (reason == null) {
            cache.getContext().remove(META_PENDING_REASON);
        } else {
            cache.getContext().put(META_PENDING_REASON, reason);
        }
    }

    @Override
    public boolean isPending() {
        return isTruthy(cache.getContext().get(META_PENDING)) || AgentSession.super.isPending();
    }

    @Override
    public String getPendingReason() {
        Object reason = cache.getContext().get(META_PENDING_REASON);
        return reason == null ? AgentSession.super.getPendingReason() : String.valueOf(reason);
    }

    private void loadMessages(SessionRecord sessionRecord) {
        try {
            if (StrUtil.isNotBlank(sessionRecord.getNdjson())) {
                cache.loadNdjson(sessionRecord.getNdjson());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load session ndjson: " + sessionRecord.getSessionId(), e);
        }
    }

    private FlowContext loadContext(SessionRecord sessionRecord) {
        try {
            if (StrUtil.isNotBlank(sessionRecord.getAgentSnapshotJson())) {
                FlowContext context = FlowContext.fromJson(sessionRecord.getAgentSnapshotJson());
                if (isTruthy(context.get(META_PENDING))) {
                    if (context instanceof FlowContextInternal) {
                        ((FlowContextInternal) context).stopped(true);
                    } else {
                        context.stop();
                    }
                }
                return context;
            }
        } catch (Throwable ignored) {
            // fallback to a fresh context if the old snapshot cannot be restored
        }
        return FlowContext.of(sessionRecord.getSessionId());
    }

    private void syncRecord(boolean persist) {
        try {
            sessionRecord.setNdjson(ChatMessage.toNdjson(cache.getMessages()));
            cache.getContext().put(META_PENDING, isPending());
            sessionRecord.setAgentSnapshotJson(cache.getContext().toJson());
            sessionRecord.setUpdatedAt(System.currentTimeMillis());
            if (persist && sessionRepository != null) {
                sessionRepository.save(sessionRecord);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sync sqlite agent session: " + sessionRecord.getSessionId(), e);
        }
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }
}
