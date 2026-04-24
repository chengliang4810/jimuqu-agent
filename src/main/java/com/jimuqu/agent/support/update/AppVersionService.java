package com.jimuqu.agent.support.update;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.JimuquAgentApp;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.config.RuntimeConfigResolver;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用版本与部署形态识别服务。
 */
public class AppVersionService {
    private static final String DEFAULT_REPO = "chengliang4810/jimuqu-agent";

    private final AppConfig appConfig;

    public AppVersionService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public String currentVersion() {
        Package pkg = JimuquAgentApp.class.getPackage();
        if (pkg != null && StrUtil.isNotBlank(pkg.getImplementationVersion())) {
            return pkg.getImplementationVersion().trim();
        }

        String pomVersion = loadPomPropertiesVersion();
        if (StrUtil.isNotBlank(pomVersion)) {
            return pomVersion;
        }

        String projectVersion = readPomXmlVersion();
        return StrUtil.blankToDefault(projectVersion, "0.0.1");
    }

    public String currentTag() {
        return "v" + stripLeadingV(currentVersion());
    }

    public String releaseRepo() {
        String override = firstNonBlank(
                RuntimeConfigResolver.getValue("JIMUQU_UPDATE_REPO"),
                System.getProperty("jimuqu.update.repo")
        );
        return StrUtil.blankToDefault(override, DEFAULT_REPO).trim();
    }

    public String releaseApiUrl() {
        String override = firstNonBlank(
                RuntimeConfigResolver.getValue("JIMUQU_UPDATE_RELEASE_API_URL"),
                System.getProperty("jimuqu.update.releaseApiUrl")
        );
        if (StrUtil.isNotBlank(override)) {
            return override.trim();
        }
        return "https://api.github.com/repos/" + releaseRepo() + "/releases/latest";
    }

    public String tagsApiUrl() {
        String override = firstNonBlank(
                RuntimeConfigResolver.getValue("JIMUQU_UPDATE_TAGS_API_URL"),
                System.getProperty("jimuqu.update.tagsApiUrl")
        );
        if (StrUtil.isNotBlank(override)) {
            return override.trim();
        }
        return "https://api.github.com/repos/" + releaseRepo() + "/tags?per_page=5";
    }

    public String updateProxyUrl() {
        String override = firstNonBlank(
                RuntimeConfigResolver.getValue("JIMUQU_UPDATE_HTTP_PROXY"),
                System.getProperty("jimuqu.update.httpProxy")
        );
        return StrUtil.nullToEmpty(override).trim();
    }

    public String deploymentMode() {
        if (isDocker()) {
            return "docker";
        }
        File codeSource = currentCodeSourceFile();
        if (codeSource != null && codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
            return "jar";
        }
        return "dev";
    }

    public boolean isDocker() {
        if (new File("/.dockerenv").exists()) {
            return true;
        }
        String cgroup = firstNonBlank(readFileQuietly("/proc/1/cgroup"), readFileQuietly("/proc/self/cgroup"));
        return cgroup != null && (cgroup.contains("docker") || cgroup.contains("kubepods") || cgroup.contains("containerd"));
    }

    public boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public File currentCodeSourceFile() {
        try {
            URL location = JimuquAgentApp.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            String path = URLDecoder.decode(location.getPath(), "UTF-8");
            return new File(path).getAbsoluteFile();
        } catch (Exception e) {
            return null;
        }
    }

    public File currentJarFile() {
        File file = currentCodeSourceFile();
        if (file != null && file.isFile() && file.getName().endsWith(".jar")) {
            return file;
        }
        return null;
    }

    public String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String executable = isWindows() ? "java.exe" : "java";
        return new File(new File(javaHome, "bin"), executable).getAbsolutePath();
    }

    public String[] startupArgs() {
        return JimuquAgentApp.startupArgs();
    }

    public File runtimeHome() {
        return new File(appConfig.getRuntime().getHome()).getAbsoluteFile();
    }

    public static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int size = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < size; i++) {
            int l = i < leftParts.length ? parseInt(leftParts[i]) : 0;
            int r = i < rightParts.length ? parseInt(rightParts[i]) : 0;
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    public static String normalizeVersion(String value) {
        String normalized = stripLeadingV(value);
        int dash = normalized.indexOf('-');
        if (dash >= 0) {
            normalized = normalized.substring(0, dash);
        }
        return normalized.trim();
    }

    public static String stripLeadingV(String value) {
        String normalized = StrUtil.nullToEmpty(value).trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private String loadPomPropertiesVersion() {
        InputStream inputStream = null;
        try {
            inputStream = JimuquAgentApp.class.getClassLoader()
                    .getResourceAsStream("META-INF/maven/com.jimuqu.agent/jimuqu-agent/pom.properties");
            if (inputStream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty("version");
        } catch (Exception e) {
            return null;
        } finally {
            IoUtil.close(inputStream);
        }
    }

    private String readPomXmlVersion() {
        File pomFile = new File(System.getProperty("user.dir"), "pom.xml");
        if (!pomFile.isFile()) {
            return null;
        }
        String content = cn.hutool.core.io.FileUtil.readUtf8String(pomFile);
        Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(content);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String readFileQuietly(String path) {
        try {
            return cn.hutool.core.io.FileUtil.readUtf8String(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
