package com.jimuqu.agent;

import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.storage.session.SqliteAgentSession;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class SqliteAgentSessionTest {
    @Test
    void shouldPersistMessagesAndFlowSnapshotIntoSqlite() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");

        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        agentSession.addMessage(Arrays.asList(
                ChatMessage.ofUser("你好"),
                ChatMessage.ofAssistant("收到")
        ));
        agentSession.getContext().put("flag", "demo");
        agentSession.pending(true, "need-review");
        agentSession.updateSnapshot();

        SessionRecord reloaded = env.sessionRepository.findById(session.getSessionId());
        assertThat(reloaded.getNdjson()).contains("你好");
        assertThat(reloaded.getNdjson()).contains("收到");
        assertThat(reloaded.getAgentSnapshotJson()).isNotBlank();

        SqliteAgentSession restored = new SqliteAgentSession(reloaded);
        assertThat(restored.getMessages()).hasSize(2);
        assertThat(restored.getContext().<String>getAs("flag")).isEqualTo("demo");
        assertThat(restored.isPending()).isTrue();
        assertThat(restored.getPendingReason()).isEqualTo("need-review");
    }
}
