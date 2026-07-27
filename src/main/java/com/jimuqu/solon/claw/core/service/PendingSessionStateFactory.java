package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;

/** pending 会话状态工厂端口，负责屏蔽具体实现创建逻辑。 */
public interface PendingSessionStateFactory {
    /**
     * 根据会话记录创建 pending 会话状态对象。
     *
     * @param session 会话记录。
     * @return 返回 pending 会话状态对象。
     */
    PendingSessionState create(SessionRecord session);

    /**
     * 根据会话记录和仓储创建 pending 会话状态对象。
     *
     * @param session 会话记录。
     * @param sessionRepository 会话仓储。
     * @return 返回 pending 会话状态对象。
     */
    default PendingSessionState create(SessionRecord session, SessionRepository sessionRepository) {
        return create(session);
    }
}
