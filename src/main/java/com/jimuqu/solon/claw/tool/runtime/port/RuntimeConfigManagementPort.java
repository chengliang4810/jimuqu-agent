package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层管理工作区运行配置的同步端口。 */
public interface RuntimeConfigManagementPort {
    /** 查询已脱敏配置项。 */
    Map<String, Object> getConfigItems();

    /** 写入非密钥配置项。 */
    Map<String, Object> writeNonSecret(String key, String value, boolean refresh);

    /** 删除配置项。 */
    Map<String, Object> remove(String key);
}
