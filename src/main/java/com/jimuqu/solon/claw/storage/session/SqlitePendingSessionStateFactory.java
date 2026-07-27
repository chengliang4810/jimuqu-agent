package com.jimuqu.solon.claw.storage.session;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.PendingSessionState;
import com.jimuqu.solon.claw.core.service.PendingSessionStateFactory;

/** 基于 SQLite 会话适配器的 pending 会话状态工厂。 */
public class SqlitePendingSessionStateFactory implements PendingSessionStateFactory {
    /**
     * 根据会话记录创建 SQLite pending 状态对象。
     *
     * @param session 会话记录。
     * @return 返回 pending 会话状态对象。
     */
    @Override
    public PendingSessionState create(SessionRecord session) {
        return new SqliteAgentSession(session);
    }

    /**
     * 根据会话记录和仓储创建 SQLite pending 状态对象。
     *
     * @param session 会话记录。
     * @param sessionRepository 会话仓储。
     * @return 返回 pending 会话状态对象。
     */
    @Override
    public PendingSessionState create(SessionRecord session, SessionRepository sessionRepository) {
        return new SqliteAgentSession(session, sessionRepository);
    }
}
