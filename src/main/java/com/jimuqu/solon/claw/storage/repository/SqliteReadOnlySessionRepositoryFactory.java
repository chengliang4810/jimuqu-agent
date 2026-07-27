package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.repository.ReadOnlySessionRepositoryFactory;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import java.nio.file.Path;

/** 使用 SQLite 只读仓储实现跨 Profile 会话访问。 */
public class SqliteReadOnlySessionRepositoryFactory implements ReadOnlySessionRepositoryFactory {
    /** 打开目标 Profile 的 SQLite 只读会话仓储。 */
    @Override
    public SessionRepository open(Path stateDb) throws Exception {
        return new ReadOnlyProfileSessionRepository(stateDb);
    }
}
