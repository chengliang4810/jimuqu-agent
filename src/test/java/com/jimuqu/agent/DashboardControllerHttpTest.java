package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.Solon;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class DashboardControllerHttpTest {
    private static int port;
    private static File runtimeHome;

    @BeforeAll
    static void startApp() throws Exception {
        port = findFreePort();
        runtimeHome = Files.createTempDirectory("jimuqu-agent-dashboard-test").toFile();

        Solon.start(JimuquAgentApp.class, new String[]{
                "--server.port=" + port,
                "--jimuqu.runtime.home=" + runtimeHome.getAbsolutePath(),
                "--jimuqu.runtime.contextDir=" + new File(runtimeHome, "context").getAbsolutePath(),
                "--jimuqu.runtime.skillsDir=" + new File(runtimeHome, "skills").getAbsolutePath(),
                "--jimuqu.runtime.cacheDir=" + new File(runtimeHome, "cache").getAbsolutePath(),
                "--jimuqu.runtime.stateDb=" + new File(runtimeHome, "state.db").getAbsolutePath(),
                "--jimuqu.scheduler.enabled=false"
        });

        waitForHealth();
        createSampleSkill();
    }

    @AfterAll
    static void stopApp() {
        try {
            Solon.stopBlock(false, 0);
        } finally {
            if (runtimeHome != null) {
                FileUtil.del(runtimeHome);
            }
        }
    }

    @Test
    void shouldInjectDashboardTokenAndProtectSensitiveApis() throws Exception {
        HttpResult index = request("GET", "/", null, null);
        assertThat(index.status).isEqualTo(200);
        assertThat(index.body).contains("__JIMUQU_SESSION_TOKEN__");

        String token = extractToken(index.body);
        assertThat(token).isNotBlank();

        HttpResult status = request("GET", "/api/status", null, null);
        assertThat(status.status).isEqualTo(200);
        assertThat(status.body).contains("\"version\"");
        assertThat(status.body).contains("\"setup_state\"");

        HttpResult unauthorizedEnv = request("GET", "/api/env", null, null);
        assertThat(unauthorizedEnv.status).isEqualTo(401);

        HttpResult authorizedEnv = request("GET", "/api/env", null, token);
        assertThat(authorizedEnv.status).isEqualTo(200);
        assertThat(authorizedEnv.body).contains("JIMUQU_LLM_API_KEY");

        HttpResult unauthorizedDoctor = request("GET", "/api/gateway/doctor", null, null);
        assertThat(unauthorizedDoctor.status).isEqualTo(401);

        HttpResult authorizedDoctor = request("GET", "/api/gateway/doctor", null, token);
        assertThat(authorizedDoctor.status).isEqualTo(200);
        assertThat(authorizedDoctor.body).contains("\"platforms\"");

        HttpResult login = request("GET", "/login", null, null);
        assertThat(login.status).isEqualTo(200);
        assertThat(login.body).contains("__JIMUQU_SESSION_TOKEN__");

        HttpResult chat = request("GET", "/chat", null, null);
        assertThat(chat.status).isEqualTo(200);
        assertThat(chat.body).contains("__JIMUQU_SESSION_TOKEN__");

        HttpResult files = request("GET", "/files", null, null);
        assertThat(files.status).isEqualTo(200);
        assertThat(files.body).contains("__JIMUQU_SESSION_TOKEN__");
    }

    @Test
    void shouldPersistConfigEnvAndExposeDashboardResources() throws Exception {
        String token = extractToken(request("GET", "/", null, null).body);

        HttpResult saveConfig = request("PUT", "/api/config",
                "{\"config\":{\"llm\":{\"model\":\"dashboard-model\"},\"scheduler\":{\"tickSeconds\":45}}}",
                token);
        assertThat(saveConfig.status).isEqualTo(200);
        File overrideFile = new File(runtimeHome, "config.yml");
        assertThat(overrideFile).exists();
        assertThat(FileUtil.readUtf8String(overrideFile)).contains("dashboard-model");

        HttpResult saveEnv = request("PUT", "/api/env",
                "{\"key\":\"JIMUQU_LLM_API_KEY\",\"value\":\"secret12345678\"}",
                token);
        assertThat(saveEnv.status).isEqualTo(200);
        assertThat(overrideFile).exists();
        assertThat(FileUtil.readUtf8String(overrideFile)).contains("apiKey: secret12345678");

        HttpResult revealEnv = request("POST", "/api/env/reveal",
                "{\"key\":\"JIMUQU_LLM_API_KEY\"}",
                token);
        assertThat(revealEnv.status).isEqualTo(200);
        assertThat(revealEnv.body).contains("secret12345678");

        request("POST", "/api/gateway/message",
                "{\"platform\":\"MEMORY\",\"chatId\":\"dashboard-chat\",\"userId\":\"dashboard-user\",\"chatType\":\"dm\",\"chatName\":\"dashboard-chat\",\"userName\":\"dashboard-user\",\"text\":\"hello\"}",
                null);

        HttpResult sessions = request("GET", "/api/sessions?limit=20&offset=0", null, token);
        assertThat(sessions.status).isEqualTo(200);
        assertThat(sessions.body).contains("\"total\"");

        HttpResult skills = request("GET", "/api/skills", null, token);
        assertThat(skills.status).isEqualTo(200);
        assertThat(skills.body).contains("sample-skill");

        HttpResult toggleSkill = request("PUT", "/api/skills/toggle",
                "{\"name\":\"sample-skill\",\"enabled\":false}",
                token);
        assertThat(toggleSkill.status).isEqualTo(200);
        HttpResult skillsAfterToggle = request("GET", "/api/skills", null, token);
        assertThat(skillsAfterToggle.body).contains("\"enabled\":false");

        HttpResult createCron = request("POST", "/api/cron/jobs",
                "{\"prompt\":\"daily summary\",\"schedule\":\"0 9 * * *\",\"name\":\"Daily summary\",\"deliver\":\"local\"}",
                token);
        assertThat(createCron.status).isEqualTo(200);
        HttpResult cronJobs = request("GET", "/api/cron/jobs", null, token);
        assertThat(cronJobs.body).contains("Daily summary");

        HttpResult logs = request("GET", "/api/logs?file=agent&lines=20", null, token);
        assertThat(logs.status).isEqualTo(200);
        assertThat(logs.body).contains("\"lines\"");
    }

    private static void createSampleSkill() {
        File skillFile = FileUtil.file(runtimeHome, "skills", "sample-skill", "SKILL.md");
        String content = "---\nname: sample-skill\ndescription: Sample skill for dashboard tests\n---\n\n# Sample\n";
        FileUtil.writeUtf8String(content, skillFile);
    }

    private static String extractToken(String html) {
        Matcher matcher = Pattern.compile("__JIMUQU_SESSION_TOKEN__=\\\"([^\\\"]+)\\\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static HttpResult request(String method, String path, String body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(data);
            } finally {
                outputStream.close();
            }
        }

        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return new HttpResult(status, "");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            return new HttpResult(status, buffer.toString());
        } finally {
            reader.close();
            connection.disconnect();
        }
    }

    private static void waitForHealth() throws Exception {
        long deadline = System.currentTimeMillis() + 15000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResult response = request("GET", "/health", null, null);
                if (response.status == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // retry
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("health endpoint did not become ready");
    }

    private static int findFreePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        try {
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    private static class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
