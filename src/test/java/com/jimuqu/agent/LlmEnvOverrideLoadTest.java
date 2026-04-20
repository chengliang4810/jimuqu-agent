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
    void shouldLoadProviderApiUrlModelAndApiKeyFromRuntimeEnv() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-agent-llm-env").toFile();
        File envFile = new File(runtimeHome, ".env");
        FileUtil.writeUtf8String(
                "JIMUQU_LLM_PROVIDER=openai-responses\n"
                        + "JIMUQU_LLM_API_URL=https://api.jimuqu.com/v1/responses\n"
                        + "JIMUQU_LLM_MODEL=gpt-5.4\n"
                        + "JIMUQU_LLM_API_KEY=test-key\n",
                envFile
        );

        Props props = new Props();
        props.put("jimuqu.runtime.home", runtimeHome.getAbsolutePath());
        props.put("jimuqu.runtime.contextDir", new File(runtimeHome, "context").getAbsolutePath());
        props.put("jimuqu.runtime.skillsDir", new File(runtimeHome, "skills").getAbsolutePath());
        props.put("jimuqu.runtime.cacheDir", new File(runtimeHome, "cache").getAbsolutePath());
        props.put("jimuqu.runtime.stateDb", new File(runtimeHome, "state.db").getAbsolutePath());
        props.put("jimuqu.llm.provider", "ollama");
        props.put("jimuqu.llm.apiUrl", "http://127.0.0.1:11434");
        props.put("jimuqu.llm.model", "qwen");

        AppConfig config = AppConfig.load(props);

        assertThat(config.getLlm().getProvider()).isEqualTo("openai-responses");
        assertThat(config.getLlm().getApiUrl()).isEqualTo("https://api.jimuqu.com/v1/responses");
        assertThat(config.getLlm().getModel()).isEqualTo("gpt-5.4");
        assertThat(config.getLlm().getApiKey()).isEqualTo("test-key");
    }
}
