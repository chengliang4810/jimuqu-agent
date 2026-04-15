package com.jimuqu.agent.tool;

import com.jimuqu.agent.core.SessionRecord;
import com.jimuqu.agent.core.SessionRepository;
import org.noear.solon.ai.annotation.ToolMapping;

import java.util.List;

public class SessionSearchTools {
    private final SessionRepository sessionRepository;

    public SessionSearchTools(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @ToolMapping(name = "session_search", description = "Search historical sessions by keyword and return matching session ids and branch names.")
    public String sessionSearch(String keyword) throws Exception {
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
