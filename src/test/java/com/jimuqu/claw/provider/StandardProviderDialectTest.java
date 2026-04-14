package com.jimuqu.claw.provider;

import com.jimuqu.claw.provider.model.ResolvedModelConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class StandardProviderDialectTest {
    @Test
    public void openaiResponsesUsesSingleSnakeCaseTokenLimit() {
        StandardProviderDialect dialect = new StandardProviderDialect("openai-responses");

        Map<String, Object> options = dialect.buildModelOptions(ResolvedModelConfig.builder()
                .dialect("openai-responses")
                .temperature(0.2)
                .maxTokens(4096L)
                .maxOutputTokens(1024L)
                .build());

        Assertions.assertEquals(0.2, options.get("temperature"));
        Assertions.assertEquals(1024L, options.get("max_tokens"));
        Assertions.assertFalse(options.containsKey("maxOutputTokens"));
    }

    @Test
    public void geminiUsesCamelCaseOutputTokenLimit() {
        StandardProviderDialect dialect = new StandardProviderDialect("gemini");

        Map<String, Object> options = dialect.buildModelOptions(ResolvedModelConfig.builder()
                .dialect("gemini")
                .maxTokens(4096L)
                .maxOutputTokens(1024L)
                .build());

        Assertions.assertEquals(1024L, options.get("maxOutputTokens"));
        Assertions.assertFalse(options.containsKey("max_tokens"));
    }

    @Test
    public void openaiFallsBackToMaxOutputTokensWhenMaxTokensMissing() {
        StandardProviderDialect dialect = new StandardProviderDialect("openai");

        Map<String, Object> options = dialect.buildModelOptions(ResolvedModelConfig.builder()
                .dialect("openai")
                .maxOutputTokens(1024L)
                .build());

        Assertions.assertEquals(1024L, options.get("max_tokens"));
        Assertions.assertFalse(options.containsKey("maxOutputTokens"));
    }
}
