package com.jimuqu.agent;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.ChannelStatus;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.service.ChannelAdapter;
import com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.agent.support.RuntimeSettingsService;
import com.jimuqu.agent.support.TestEnvironment;
import com.jimuqu.agent.web.DashboardConfigService;
import com.jimuqu.agent.web.DashboardEnvService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RuntimeRefreshBehaviorTest {
    @Test
    void shouldUpdateLlmConfigWithoutReconnectingChannels() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter(PlatformType.WEIXIN);
        RuntimeSettingsService runtimeSettingsService = runtimeSettingsService(env, adapter);

        runtimeSettingsService.setConfigValue("llm.model", "gpt-5.2");

        assertThat(env.appConfig.getLlm().getModel()).isEqualTo("gpt-5.2");
        assertThat(adapter.disconnectCount).isZero();
        assertThat(adapter.connectCount).isZero();
    }

    @Test
    void shouldReconnectChannelsWhenUpdatingChannelConfig() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter(PlatformType.WEIXIN);
        RuntimeSettingsService runtimeSettingsService = runtimeSettingsService(env, adapter);

        runtimeSettingsService.setConfigValue("channels.weixin.enabled", "true");

        assertThat(env.appConfig.getChannels().getWeixin().isEnabled()).isTrue();
        assertThat(adapter.disconnectCount).isEqualTo(1);
        assertThat(adapter.connectCount).isEqualTo(1);
    }

    private RuntimeSettingsService runtimeSettingsService(TestEnvironment env, RecordingChannelAdapter adapter) {
        Map<PlatformType, ChannelAdapter> adapters = new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(adapter.platform(), adapter);
        GatewayRuntimeRefreshService refreshService = new GatewayRuntimeRefreshService(env.appConfig, adapters);
        DashboardConfigService configService = new DashboardConfigService(env.appConfig, refreshService);
        DashboardEnvService envService = new DashboardEnvService(env.appConfig, refreshService);
        return new RuntimeSettingsService(env.appConfig, env.globalSettingRepository, env.deliveryService, configService, envService);
    }

    private static class RecordingChannelAdapter implements ChannelAdapter {
        private final PlatformType platformType;
        private int connectCount;
        private int disconnectCount;

        private RecordingChannelAdapter(PlatformType platformType) {
            this.platformType = platformType;
        }

        @Override
        public PlatformType platform() {
            return platformType;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean connect() {
            connectCount++;
            return true;
        }

        @Override
        public void disconnect() {
            disconnectCount++;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String detail() {
            return "recording";
        }

        @Override
        public void send(DeliveryRequest request) {
        }

        @Override
        public ChannelStatus statusSnapshot() {
            ChannelStatus status = new ChannelStatus(platformType, true, true, "recording");
            status.setMissingEnv(Collections.<String>emptyList());
            return status;
        }
    }
}
