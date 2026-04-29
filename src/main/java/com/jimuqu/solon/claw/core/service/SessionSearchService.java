package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.SessionSearchEntry;
import java.util.List;

/** 会话搜索服务接口。 */
public interface SessionSearchService {
    /** 搜索或列出最近会话。 */
    List<SessionSearchEntry> search(String sourceKey, String query, int limit) throws Exception;
}
