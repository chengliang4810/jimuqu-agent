package com.jimuqu.agent.tool.runtime;

import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;

import java.util.List;

/**
 * SessionSearchTools 实现。
 */
@RequiredArgsConstructor
public class SessionSearchTools {
    private final SessionRepository sessionRepository;

    @ToolMapping(name = "session_search", description = "Search historical sessions by keyword and return matching session ids and branch names.")
    public String sessionSearch(@Param(name = "keyword", description = "检索关键词") String keyword) throws Exception {
        List<SessionRecord> sessions = sessionRepository.search(keyword, 10);
        StringBuilder buffer = new StringBuilder();
        for (SessionRecord session : sessions) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(session.getSessionId()).append(" [").append(session.getBranchName()).append(']');
        }
        return buffer.length() == 0 ? "No matching sessions" : buffer.toString();
    }
}
