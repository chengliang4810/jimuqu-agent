package com.jimuqu.agent;

import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.gateway.service.DefaultGatewayService;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 gateway 的异常隔离与重复消息抑制行为。
 */
public class GatewayResilienceTest {
    @Test
    void shouldIgnoreLearningSchedulerFailureAndStillReturnReply() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room:user");
        DefaultGatewayService service = new DefaultGatewayService(
                unsupportedCommandService(),
                replyingOrchestrator(session, "ok"),
                env.deliveryService,
                env.sessionRepository,
                allowAllAuthorization(env),
                new SkillLearningService() {
                    @Override
                    public void schedulePostReplyLearning(SessionRecord session, GatewayMessage message, GatewayReply reply) {
                        throw new IllegalStateException("learning scheduler boom");
                    }
                },
                env.memoryManager
        );

        GatewayReply reply = service.handle(env.message("room", "user", "hello"));

        assertThat(reply.getContent()).isEqualTo("ok");
        assertThat(reply.isError()).isFalse();
        assertThat(env.memoryChannelAdapter.getLastRequest().getText()).isEqualTo("ok");
    }

    @Test
    void shouldSuppressDuplicateThreadMessages() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room:user");
        final AtomicInteger calls = new AtomicInteger();
        DefaultGatewayService service = new DefaultGatewayService(
                unsupportedCommandService(),
                new ConversationOrchestrator() {
                    @Override
                    public GatewayReply handleIncoming(GatewayMessage message) {
                        calls.incrementAndGet();
                        GatewayReply reply = GatewayReply.ok("ok");
                        reply.setSessionId(session.getSessionId());
                        return reply;
                    }

                    @Override
                    public GatewayReply runScheduled(GatewayMessage syntheticMessage) {
                        return handleIncoming(syntheticMessage);
                    }

                    @Override
                    public GatewayReply resumePending(String sourceKey) {
                        return GatewayReply.ok("ok");
                    }
                },
                env.deliveryService,
                env.sessionRepository,
                allowAllAuthorization(env),
                new SkillLearningService() {
                    @Override
                    public void schedulePostReplyLearning(SessionRecord session, GatewayMessage message, GatewayReply reply) {
                    }
                },
                env.memoryManager
        );

        GatewayMessage firstMessage = env.message("room", "user", "hello");
        firstMessage.setThreadId("msg-1");
        GatewayReply first = service.handle(firstMessage);
        GatewayReply second = service.handle(firstMessage);

        assertThat(first.getContent()).isEqualTo("ok");
        assertThat(second).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    private CommandService unsupportedCommandService() {
        return new CommandService() {
            @Override
            public boolean supports(String commandName) {
                return false;
            }

            @Override
            public GatewayReply handle(GatewayMessage message, String commandLine) {
                throw new UnsupportedOperationException("commands are not expected in this test");
            }
        };
    }

    private ConversationOrchestrator replyingOrchestrator(final SessionRecord session, final String content) {
        return new ConversationOrchestrator() {
            @Override
            public GatewayReply handleIncoming(GatewayMessage message) {
                GatewayReply reply = GatewayReply.ok(content);
                reply.setSessionId(session.getSessionId());
                return reply;
            }

            @Override
            public GatewayReply runScheduled(GatewayMessage syntheticMessage) {
                return handleIncoming(syntheticMessage);
            }

            @Override
            public GatewayReply resumePending(String sourceKey) {
                return GatewayReply.ok(content);
            }
        };
    }

    private GatewayAuthorizationService allowAllAuthorization(TestEnvironment env) {
        return new GatewayAuthorizationService(env.gatewayPolicyRepository, env.appConfig) {
            @Override
            public GatewayReply preAuthorize(GatewayMessage message) {
                return null;
            }

            @Override
            public boolean isAuthorized(GatewayMessage message) {
                return true;
            }
        };
    }
}
