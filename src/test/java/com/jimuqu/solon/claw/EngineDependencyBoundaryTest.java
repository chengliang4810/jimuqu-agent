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

/** 验证编排层不会重新直接依赖持久化适配器实现。 */
public class EngineDependencyBoundaryTest {
    /** engine 源码禁止导入 storage 包，具体实现必须由 bootstrap 通过端口注入。 */
    @Test
    void engineDoesNotImportStorageAdapters() throws IOException {
        Path engineRoot =
                Paths.get("src", "main", "java", "com", "jimuqu", "solon", "claw", "engine");
        List<String> violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(engineRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectStorageImports(engineRoot, path, violations));
        }
        assertThat(violations).isEmpty();
    }

    /** 收集单个 engine 源文件中的 storage import。 */
    private void collectStorageImports(Path engineRoot, Path source, List<String> violations) {
        try {
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                if (line.startsWith("import com.jimuqu.solon.claw.storage.")) {
                    violations.add(engineRoot.relativize(source) + ": " + line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 engine 源文件: " + source, e);
        }
    }
}
