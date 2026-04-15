package com.jimuqu.agent;

import com.jimuqu.agent.core.SessionRecord;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StorageRepositoryTest {
    @Test
    void shouldPersistAndSearchSessions() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");
        session.setNdjson("hello world");
        env.sessionRepository.save(session);

        assertThat(env.sessionRepository.findById(session.getSessionId())).isNotNull();
        assertThat(env.sessionRepository.search("hello", 10)).hasSize(1);

        SessionRecord clone = env.sessionRepository.cloneSession("MEMORY:room-a:user-a", session.getSessionId(), "review");
        assertThat(clone.getParentSessionId()).isEqualTo(session.getSessionId());
        assertThat(env.sessionRepository.findBySourceAndBranch("MEMORY:room-a:user-a", "review")).isNotNull();
    }
}
