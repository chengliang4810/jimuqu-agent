package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.List;
import java.util.Map;

/** 工具层查询工具集定义的同步端口。 */
public interface SkillsQueryPort {
    /**
     * 查询工具集列表。
     *
     * @return 工具集列表。
     */
    List<Map<String, Object>> getToolsets();
}
