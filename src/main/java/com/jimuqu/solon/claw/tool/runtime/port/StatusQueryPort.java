package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询运行状态的同步端口。 */
public interface StatusQueryPort {
    /** 查询健康运行快照。 */
    Map<String, Object> getHealthRuntimeSnapshot() throws Exception;

    /** 查询模型信息。 */
    Map<String, Object> getModelInfo(boolean detailed);

    /** 查询运行状态。 */
    Map<String, Object> getStatus(boolean detailed) throws Exception;
}
