package com.jimuqu.agent;

import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.storage.session.SqliteAgentSession;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DangerousCommandApprovalCommandTest {
    @Test
    void shouldApproveDangerousCommandForSessionAndResume() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.gatewayService.handle(env.message("room-1", "user-1", "hello"));
        env.gatewayAuthorizationService.claimAdmin(env.message("room-1", "user-1", "/pairing claim-admin"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-1:user-1");
        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        env.dangerousCommandApprovalService.storePendingApproval(
                agentSession,
                "execute_shell",
                "recursive_delete",
                "recursive delete",
                "rm -rf runtime/cache"
        );

        GatewayReply reply = env.send("room-1", "user-1", "/approve session");
        SessionRecord updated = env.sessionRepository.getBoundSession("MEMORY:room-1:user-1");
        SqliteAgentSession updatedAgentSession = new SqliteAgentSession(updated, env.sessionRepository);

        assertThat(reply.getContent()).isEqualTo("echo:resume");
        assertThat(env.dangerousCommandApprovalService.getPendingApproval(updatedAgentSession)).isNull();
        assertThat(env.dangerousCommandApprovalService.isSessionApproved(updatedAgentSession, "recursive_delete")).isFalse();
        assertThat(env.dangerousCommandApprovalService.isSessionApproved(updatedAgentSession, "execute_shell", "recursive_delete", "rm -rf runtime/cache")).isTrue();
        assertThat(env.dangerousCommandApprovalService.isSessionApproved(updatedAgentSession, "execute_shell", "recursive_delete", "rm -rf runtime/logs")).isFalse();
    }

    @Test
    void shouldPersistAlwaysApprovalPattern() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.gatewayService.handle(env.message("room-2", "user-2", "hello"));
        env.gatewayAuthorizationService.claimAdmin(env.message("room-2", "user-2", "/pairing claim-admin"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-2:user-2");
        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        env.dangerousCommandApprovalService.storePendingApproval(
                agentSession,
                "execute_shell",
                "recursive_delete",
                "recursive delete",
                "rm -rf runtime/cache"
        );

        GatewayReply reply = env.send("room-2", "user-2", "/approve always");

        assertThat(reply.getContent()).isEqualTo("echo:resume");
        assertThat(env.dangerousCommandApprovalService.isAlwaysApproved("recursive_delete")).isFalse();
        assertThat(env.dangerousCommandApprovalService.isAlwaysApproved("execute_shell", "recursive_delete", "rm -rf runtime/cache")).isTrue();
        assertThat(env.dangerousCommandApprovalService.isAlwaysApproved("execute_shell", "recursive_delete", "rm -rf runtime/logs")).isFalse();
    }
}
