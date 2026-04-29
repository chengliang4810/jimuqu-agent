package com.jimuqu.agent;

import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentRunStopCommandTest {
    @Test
    void shouldStopActiveAgentRunForCurrentSource() throws Exception {
        SlowLlmGateway slowLlmGateway = new SlowLlmGateway();
        TestEnvironment env = TestEnvironment.withLlm(slowLlmGateway);
        bootstrapAdmin(env);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<GatewayReply> running = executorService.submit(() -> env.send("admin-chat", "admin-user", "执行一个长任务"));

        assertThat(slowLlmGateway.started.await(2, TimeUnit.SECONDS)).isTrue();

        GatewayReply stopReply = env.send("admin-chat", "admin-user", "/stop");

        assertThat(stopReply.getContent()).contains("已请求停止当前任务");
        assertThat(stopReply.getContent()).contains("已停止后台进程");
        GatewayReply cancelledReply = running.get(3, TimeUnit.SECONDS);
        assertThat(cancelledReply.getContent()).contains("当前任务已停止");
        assertThat(slowLlmGateway.interrupted).isTrue();
        assertThat(env.agentRunControlService.isRunning("MEMORY:admin-chat:admin-user")).isFalse();

        executorService.shutdownNow();
    }

    private void bootstrapAdmin(TestEnvironment env) throws Exception {
        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
    }

    private static class SlowLlmGateway implements LlmGateway {
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile boolean interrupted;

        @Override
        public LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception {
            started.countDown();
            try {
                while (true) {
                    Thread.sleep(1000L);
                }
            } catch (InterruptedException e) {
                interrupted = true;
                throw e;
            }
        }

        @Override
        public LlmResult resume(SessionRecord session, String systemPrompt, List<Object> toolObjects) throws Exception {
            return chat(session, systemPrompt, null, toolObjects);
        }
    }
}
