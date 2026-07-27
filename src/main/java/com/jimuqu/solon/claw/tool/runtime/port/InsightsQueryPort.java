package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询 Dashboard 洞察的同步端口。 */
public interface InsightsQueryPort {
    /** 查询运行洞察总览。 */
    Map<String, Object> overview();

    /** 查询技能用量。 */
    Map<String, Object> skillUsage();
}
