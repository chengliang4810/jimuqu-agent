package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层管理渠道工具集的同步端口。 */
public interface PlatformToolsetsManagementPort {
    /** 查询渠道工具集总览。 */
    Map<String, Object> overview();

    /** 更新指定渠道的工具集。 */
    Map<String, Object> update(String platform, Map<String, Object> body);
}
