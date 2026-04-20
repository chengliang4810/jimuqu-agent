package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.core.service.InboundMessageHandler;
import com.jimuqu.agent.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class WeixinInboundDispatchTest {
    @Test
    void shouldDispatchInboundOffThePollingThread() throws Exception {
        AppConfig config = newConfig();
        config.getChannels().getWeixin().setEnabled(true);
        config.getChannels().getWeixin().setAccountId("wx-bot");
        config.getChannels().getWeixin().setGroupPolicy("open");

        WeiXinChannelAdapter adapter = new WeiXinChannelAdapter(
                config.getChannels().getWeixin(),
                new InMemoryChannelStateRepository(),
                new AttachmentCacheService(config)
        );

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> handlerThread = new AtomicReference<String>();
        final String callerThread = Thread.currentThread().getName();
        adapter.setInboundMessageHandler(new InboundMessageHandler() {
            @Override
            public void handle(com.jimuqu.agent.core.model.GatewayMessage message) {
                handlerThread.set(Thread.currentThread().getName());
                latch.countDown();
            }
        });

        Method processInbound = WeiXinChannelAdapter.class.getDeclaredMethod("processInboundMessage", ONode.class);
        processInbound.setAccessible(true);
        processInbound.invoke(adapter, ONode.ofJson("{"
                + "\"from_user_id\":\"wx-user\","
                + "\"message_id\":\"msg-1\","
                + "\"room_id\":\"room-1\","
                + "\"item_list\":[{\"type\":1,\"text_item\":{\"text\":\"hello\"}}]"
                + "}"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(handlerThread.get()).isNotBlank();
        assertThat(handlerThread.get()).isNotEqualTo(callerThread);

        adapter.disconnect();
    }

    private AppConfig newConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-weixin-dispatch-test").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime().setContextDir(new File(runtimeHome, "context").getAbsolutePath());
        config.getRuntime().setSkillsDir(new File(runtimeHome, "skills").getAbsolutePath());
        config.getRuntime().setCacheDir(new File(runtimeHome, "cache").getAbsolutePath());
        config.getRuntime().setStateDb(new File(runtimeHome, "state.db").getAbsolutePath());
        config.getRuntime().setConfigOverrideFile(new File(runtimeHome, "config.override.yml").getAbsolutePath());
        config.getRuntime().setEnvFile(new File(runtimeHome, ".env").getAbsolutePath());
        config.getRuntime().setLogsDir(new File(runtimeHome, "logs").getAbsolutePath());
        return config;
    }

    private static class InMemoryChannelStateRepository implements ChannelStateRepository {
        @Override
        public String get(PlatformType platform, String scopeKey, String stateKey) {
            return null;
        }

        @Override
        public void put(PlatformType platform, String scopeKey, String stateKey, String stateValue) {
        }

        @Override
        public void delete(PlatformType platform, String scopeKey, String stateKey) {
        }

        @Override
        public List<StateItem> list(PlatformType platform, String scopeKey) {
            return Collections.emptyList();
        }
    }
}
