package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.List;
import java.util.Map;

/** 工具层查询审批事件的同步端口。 */
public interface ApprovalEventsQueryPort {
    /**
     * 查询最近审批事件。
     *
     * @param limit 最大返回数量。
     * @return 审批事件列表。
     */
    List<Map<String, Object>> recentEvents(int limit);

    /**
     * 查询审批事件统计。
     *
     * @return 审批事件统计。
     */
    Map<String, Object> stats();
}
