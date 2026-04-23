package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreEnvOverrideLoadTest {
    @Test
    void shouldLoadCoreAndChannelConfigFromRuntimeConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-agent-core-env").toFile();
        File envFile = new File(runtimeHome, "config.yml");
        FileUtil.writeUtf8String(
                "jimuqu:\n"
                        + "  scheduler:\n"
                        + "    enabled: false\n"
                        + "    tickSeconds: 15\n"
                        + "  react:\n"
                        + "    maxSteps: 14\n"
                        + "    retryMax: 5\n"
                        + "    retryDelayMs: 3000\n"
                        + "    delegateMaxSteps: 22\n"
                        + "    delegateRetryMax: 6\n"
                        + "    delegateRetryDelayMs: 3500\n"
                        + "    summarizationEnabled: false\n"
                        + "    summarizationMaxMessages: 55\n"
                        + "    summarizationMaxTokens: 45000\n"
                        + "  compression:\n"
                        + "    enabled: false\n"
                        + "    thresholdPercent: 0.75\n"
                        + "    summaryModel: gpt-5.4-mini\n"
                        + "  channels:\n"
                        + "    feishu:\n"
                        + "      enabled: true\n"
                        + "      websocketUrl: wss://feishu.example/ws\n"
                        + "    dingtalk:\n"
                        + "      streamUrl: wss://dingtalk.example/stream\n"
                        + "    wecom:\n"
                        + "      websocketUrl: wss://wecom.example/ws\n"
                        + "      groups:\n"
                        + "        room-a:\n"
                        + "          allowFrom:\n"
                        + "            - alice\n"
                        + "            - bob\n"
                        + "        '*':\n"
                        + "          allowFrom:\n"
                        + "            - admin\n"
                        + "    weixin:\n"
                        + "      enabled: true\n"
                        + "      baseUrl: https://weixin.example\n"
                        + "      cdnBaseUrl: https://cdn.example\n"
                        + "      longPollUrl: https://poll.example/ilink/bot/getupdates\n"
                        + "      splitMultilineMessages: true\n"
                        + "      sendChunkRetries: 9\n",
                envFile
        );

        Props props = new Props();
        props.put("jimuqu.runtime.home", runtimeHome.getAbsolutePath());
        props.put("jimuqu.runtime.contextDir", new File(runtimeHome, "context").getAbsolutePath());
        props.put("jimuqu.runtime.skillsDir", new File(runtimeHome, "skills").getAbsolutePath());
        props.put("jimuqu.runtime.cacheDir", new File(runtimeHome, "cache").getAbsolutePath());
        props.put("jimuqu.runtime.stateDb", new File(runtimeHome, "state.db").getAbsolutePath());
        props.put("jimuqu.scheduler.enabled", "true");
        props.put("jimuqu.channels.feishu.enabled", "false");
        props.put("jimuqu.channels.weixin.enabled", "false");

        AppConfig config = AppConfig.load(props);

        assertThat(config.getScheduler().isEnabled()).isFalse();
        assertThat(config.getScheduler().getTickSeconds()).isEqualTo(15);
        assertThat(config.getReact().getMaxSteps()).isEqualTo(14);
        assertThat(config.getReact().getRetryMax()).isEqualTo(5);
        assertThat(config.getReact().getRetryDelayMs()).isEqualTo(3000);
        assertThat(config.getReact().getDelegateMaxSteps()).isEqualTo(22);
        assertThat(config.getReact().getDelegateRetryMax()).isEqualTo(6);
        assertThat(config.getReact().getDelegateRetryDelayMs()).isEqualTo(3500);
        assertThat(config.getReact().isSummarizationEnabled()).isFalse();
        assertThat(config.getReact().getSummarizationMaxMessages()).isEqualTo(55);
        assertThat(config.getReact().getSummarizationMaxTokens()).isEqualTo(45000);
        assertThat(config.getCompression().isEnabled()).isFalse();
        assertThat(config.getCompression().getThresholdPercent()).isEqualTo(0.75D);
        assertThat(config.getCompression().getSummaryModel()).isEqualTo("gpt-5.4-mini");

        assertThat(config.getChannels().getFeishu().isEnabled()).isTrue();
        assertThat(config.getChannels().getFeishu().getWebsocketUrl()).isEqualTo("wss://feishu.example/ws");
        assertThat(config.getChannels().getDingtalk().getStreamUrl()).isEqualTo("wss://dingtalk.example/stream");
        assertThat(config.getChannels().getWecom().getWebsocketUrl()).isEqualTo("wss://wecom.example/ws");
        assertThat(config.getChannels().getWecom().getGroupMemberAllowedUsers().get("room-a")).containsExactly("alice", "bob");
        assertThat(config.getChannels().getWecom().getGroupMemberAllowedUsers().get("*")).containsExactly("admin");
        assertThat(config.getChannels().getWeixin().isEnabled()).isTrue();
        assertThat(config.getChannels().getWeixin().getBaseUrl()).isEqualTo("https://weixin.example");
        assertThat(config.getChannels().getWeixin().getCdnBaseUrl()).isEqualTo("https://cdn.example");
        assertThat(config.getChannels().getWeixin().getLongPollUrl()).isEqualTo("https://poll.example/ilink/bot/getupdates");
        assertThat(config.getChannels().getWeixin().isSplitMultilineMessages()).isTrue();
        assertThat(config.getChannels().getWeixin().getSendChunkRetries()).isEqualTo(9);
    }
}
