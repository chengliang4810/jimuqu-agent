package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询 Dashboard 配置元数据的同步端口。 */
public interface ConfigManagementPort {
    /**
     * 查询当前配置。
     *
     * @return 当前配置。
     */
    Map<String, Object> getConfig();

    /**
     * 查询默认配置。
     *
     * @return 默认配置。
     */
    Map<String, Object> getDefaults();

    /**
     * 查询配置结构。
     *
     * @return 配置结构。
     */
    Map<String, Object> getSchema();

    /**
     * 查询配置诊断。
     *
     * @return 配置诊断。
     */
    Map<String, Object> diagnostics();
}
