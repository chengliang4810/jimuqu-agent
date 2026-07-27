package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询用量分析数据的同步端口。 */
public interface AnalyticsManagementPort {
    /**
     * 查询指定时间窗口的用量分析。
     *
     * @param days 统计天数。
     * @return 用量分析结果。
     * @throws Exception 查询失败。
     */
    Map<String, Object> getUsage(int days) throws Exception;
}
