package com.jimuqu.solon.claw.core.service;

/** pending 会话状态读写端口，屏蔽具体存储实现。 */
public interface PendingSessionState {
    /** 判断当前会话是否处于 pending 状态。 */
    boolean isPending();

    /** 设置当前会话的 pending 状态与原因。 */
    void pending(boolean pending, String reason);

    /** 读取当前 pending 原因。 */
    String getPendingReason();

    /** 读取当前 pending 标记时间。 */
    long getPendingMarkedAt();

    /** 持久化当前会话快照。 */
    void updateSnapshot();
}
