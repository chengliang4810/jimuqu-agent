package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询和维护会话的同步端口。 */
public interface SessionManagementPort {
    /** 查询分页会话。 */
    Map<String, Object> getSessions(int limit, int offset) throws Exception;

    /** 查询会话消息。 */
    Map<String, Object> getSessionMessages(String sessionId) throws Exception;

    /** 生成会话回顾。 */
    Map<String, Object> recap(String sessionId, int maxExchanges) throws Exception;

    /** 查询会话轨迹。 */
    Map<String, Object> trajectory(String sessionId, String branchName, boolean includeMessages)
            throws Exception;

    /** 保存会话轨迹。 */
    Map<String, Object> saveTrajectory(String sessionId, String branchName, boolean includeMessages)
            throws Exception;

    /** 更新会话元数据。 */
    Map<String, Object> updateSession(String sessionId, Map<String, Object> body) throws Exception;

    /** 查询会话树。 */
    Map<String, Object> sessionTree(String sessionId) throws Exception;

    /** 查询最新后代会话。 */
    Map<String, Object> latestDescendant(String sessionId) throws Exception;

    /** 查询会话检查点。 */
    Map<String, Object> checkpoints(String sessionId) throws Exception;

    /** 预览会话检查点。 */
    Map<String, Object> checkpointPreview(String checkpointId) throws Exception;

    /** 回滚会话检查点。 */
    Map<String, Object> rollbackCheckpoint(String checkpointId) throws Exception;
}
