package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询 Dashboard 诊断和审批队列的同步端口。 */
public interface DiagnosticsManagementPort {
    /** 查询运行诊断总览。 */
    Map<String, Object> diagnostics();

    /** 查询子进程环境变量可见性。 */
    Map<String, Object> subprocessEnvironmentProbe(Map<String, Object> body);

    /** 查询待处理审批。 */
    Map<String, Object> pendingApprovals(int limit) throws Exception;

    /** 查询审批历史。 */
    Map<String, Object> approvalHistory(int limit) throws Exception;

    /** 查询长期授权。 */
    Map<String, Object> alwaysApprovals(int limit);

    /** 查询待处理斜杠确认。 */
    Map<String, Object> pendingSlashConfirms(int limit);
}
