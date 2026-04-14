package com.jimuqu.claw.integration;

import cn.hutool.core.util.IdUtil;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.config.ClawConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class OpenaiResponsesSmokeTest {
    private static final Path TARGET_CONFIG_PATH = Paths.get("target", "local-subapi.properties");
    private static final String EXTERNAL_CONFIG_PROPERTY = "jimuqu.claw.smoke.config";
    private static final String EXTERNAL_CONFIG_ENV = "JIMUQU_CLAW_SMOKE_CONFIG";
    private static boolean copiedExternalConfig;

    @AfterAll
    public static void afterAll() {
        if (Solon.app() != null) {
            Solon.stopBlock();
        }

        if (copiedExternalConfig) {
            try {
                Files.deleteIfExists(TARGET_CONFIG_PATH);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    public void openaiResponsesProviderRoundTrip() {
        copiedExternalConfig = prepareExternalConfig();
        Assumptions.assumeTrue(
                Files.exists(TARGET_CONFIG_PATH),
                "target/local-subapi.properties is required for the real provider smoke test "
                        + "(or set -D" + EXTERNAL_CONFIG_PROPERTY + "=<path> / "
                        + EXTERNAL_CONFIG_ENV + "=<path>)");
        ensureAppStarted();

        String sessionId = "sess-openai-responses-smoke-" + IdUtil.fastSimpleUUID();
        RuntimeService runtimeService = Solon.context().getBean(RuntimeService.class);
        Assertions.assertNotNull(runtimeService, "RuntimeService bean should be available");
        Assertions.assertFalse(
                runtimeService.getClass().getName().contains("CapturingRuntimeService"),
                "Smoke test must use the real runtime service");

        RunRecord runRecord = runtimeService.handleRequest(RunRequest.builder()
                .sessionContext(SessionContext.builder()
                        .sessionId(sessionId)
                        .platform("debug")
                        .userId("local-smoke")
                        .build())
                .userMessage("Reply with exactly: pong")
                .modelAlias("default")
                .source("test:openai-responses-smoke")
                .build());

        Assertions.assertEquals(RunStatus.SUCCEEDED, runRecord.getStatus(), runRecord.getErrorMessage());
        Assertions.assertNotNull(runRecord.getResponseText());
        Assertions.assertTrue(
                normalize(runRecord.getResponseText()).contains("pong"),
                "Unexpected response text: " + runRecord.getResponseText());
    }

    private String normalize(String text) {
        return text == null
                ? ""
                : text.trim()
                .replace("`", "")
                .replace("\"", "")
                .toLowerCase();
    }

    private static boolean prepareExternalConfig() {
        if (Files.exists(TARGET_CONFIG_PATH)) {
            return false;
        }

        String externalPath = System.getProperty(EXTERNAL_CONFIG_PROPERTY);
        if (isBlank(externalPath)) {
            externalPath = System.getenv(EXTERNAL_CONFIG_ENV);
        }
        if (isBlank(externalPath)) {
            return false;
        }

        Path sourcePath = Paths.get(externalPath.trim());
        if (!Files.exists(sourcePath)) {
            return false;
        }

        try {
            Path parent = TARGET_CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourcePath, TARGET_CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stage smoke config from " + sourcePath, e);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static void ensureAppStarted() {
        if (Solon.app() != null) {
            return;
        }

        Solon.start(TestApp.class, new String[]{
                "--server.port=7086",
                "--config.add=" + normalizeConfigPath(TARGET_CONFIG_PATH),
                "--jimuqu.claw.models.default.providerProfile=standard-openai-responses",
                "--jimuqu.claw.models.default.model=gpt-5.4",
                "--jimuqu.claw.models.default.maxOutputTokens=1024"
        });
    }

    private static String normalizeConfigPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    @Import(classes = {ClawConfiguration.class})
    public static class TestApp {
        public static void main(String[] args) {
            Solon.start(TestApp.class, args);
        }
    }
}
