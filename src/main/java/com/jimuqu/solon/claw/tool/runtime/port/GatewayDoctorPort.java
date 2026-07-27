package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询网关 Doctor 诊断的同步端口。 */
public interface GatewayDoctorPort {
    /**
     * 查询网关 Doctor 诊断。
     *
     * @return Doctor 诊断结果。
     * @throws Exception 诊断失败。
     */
    Map<String, Object> doctor() throws Exception;
}
