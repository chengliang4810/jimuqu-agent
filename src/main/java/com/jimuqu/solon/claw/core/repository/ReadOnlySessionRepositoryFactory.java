package com.jimuqu.solon.claw.core.repository;

import java.nio.file.Path;

/** 按数据库路径打开只读会话仓储的端口。 */
public interface ReadOnlySessionRepositoryFactory {
    /**
     * 打开指定状态数据库的只读会话仓储。
     *
     * @param stateDb Profile 状态数据库路径。
     * @return 只读会话仓储。
     * @throws Exception 数据库不存在或无法只读打开。
     */
    SessionRepository open(Path stateDb) throws Exception;
}
