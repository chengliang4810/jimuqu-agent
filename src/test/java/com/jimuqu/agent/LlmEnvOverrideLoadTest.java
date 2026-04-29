package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class LlmEnvOverrideLoadTest {
    @Test
    void shouldLoadStructuredProviderModelAndApiKeyFromRuntimeConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-agent-llm-env").toFile();
        File envFile = new File(runtimeHome, "config.yml");
        FileUtil.writeUtf8String(
                "providers:\n"
                        + "  default:\n"
                        + "    name: Default Provider\n"
                        + "    baseUrl: https://api.jimuqu.com\n"
                        + "    apiKey: test-key\n"
                        + "    defaultModel: gpt-5.4\n"
                        + "    dialect: openai-responses\n"
                        + "model:\n"
                        + "  providerKey: default\n"
                        + "  default: \"\"\n",
                envFile
        );

        Props props = new Props();
        props.put("jimuqu.runtime.home", runtimeHome.getAbsolutePath());
        props.put("providers.default.dialect", "ollama");
        props.put("providers.default.baseUrl", "http://127.0.0.1:11434");
        props.put("providers.default.defaultModel", "qwen");

        AppConfig config = AppConfig.load(props);

        assertThat(config.getLlm().getProvider()).isEqualTo("default");
        assertThat(config.getLlm().getDialect()).isEqualTo("openai-responses");
        assertThat(config.getLlm().getApiUrl()).isEqualTo("https://api.jimuqu.com/v1/responses");
        assertThat(config.getLlm().getModel()).isEqualTo("gpt-5.4");
        assertThat(config.getLlm().getApiKey()).isEqualTo("test-key");
    }
}
