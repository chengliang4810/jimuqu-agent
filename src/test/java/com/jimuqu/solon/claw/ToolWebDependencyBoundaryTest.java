package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 验证工具层不会重新直接依赖 Dashboard Web 实现。 */
public class ToolWebDependencyBoundaryTest {
    /** tool 源码禁止导入 web 包，具体实现必须由 bootstrap 通过工具端口注入。 */
    @Test
    void toolDoesNotImportWebImplementations() throws IOException {
        Path toolRoot = Paths.get("src", "main", "java", "com", "jimuqu", "solon", "claw", "tool");
        List<String> violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(toolRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectWebImports(toolRoot, path, violations));
        }
        assertThat(violations).isEmpty();
    }

    /** 收集单个 tool 源文件中的 web import。 */
    private void collectWebImports(Path toolRoot, Path source, List<String> violations) {
        try {
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                if (line.startsWith("import com.jimuqu.solon.claw.web.")) {
                    violations.add(toolRoot.relativize(source) + ": " + line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 tool 源文件: " + source, e);
        }
    }
}
