package com.jimuqu.claw.skill;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import org.noear.solon.Utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileSkillCatalog implements SkillCatalog {
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final WorkspaceLayout workspaceLayout;

    public FileSkillCatalog(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public List<Map<String, Object>> list() {
        List<SkillDescriptor> skills = findAllSkills();
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (SkillDescriptor descriptor : skills) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", descriptor.name);
            item.put("description", descriptor.description);
            item.put("category", descriptor.category);
            item.put("path", descriptor.path);
            results.add(item);
        }

        return results;
    }

    @Override
    public Map<String, Object> view(String name, String filePath) {
        SkillDescriptor descriptor = findSkill(name);
        if (descriptor == null) {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("success", Boolean.FALSE);
            error.put("error", "Skill not found: " + name);
            return error;
        }

        if (Utils.isNotBlank(filePath)) {
            return viewSkillFile(descriptor, filePath);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        result.put("name", descriptor.name);
        result.put("description", descriptor.description);
        result.put("content", descriptor.content);
        result.put("path", descriptor.path);
        result.put("linked_files", collectLinkedFiles(descriptor.directory));
        return result;
    }

    private Map<String, Object> viewSkillFile(SkillDescriptor descriptor, String filePath) {
        Path file = descriptor.directory.resolve(filePath).normalize();
        if (!file.startsWith(descriptor.directory)) {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("success", Boolean.FALSE);
            error.put("error", "Path escapes skill boundary: " + filePath);
            return error;
        }

        File target = file.toFile();
        if (!target.exists() || !target.isFile()) {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("success", Boolean.FALSE);
            error.put("error", "Skill file not found: " + filePath);
            return error;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        result.put("name", descriptor.name);
        result.put("file", filePath);
        result.put("content", FileUtil.readString(target, StandardCharsets.UTF_8));
        result.put("path", descriptor.path);
        return result;
    }

    private List<Map<String, Object>> collectLinkedFiles(Path skillDirectory) {
        List<Map<String, Object>> linkedFiles = new ArrayList<Map<String, Object>>();
        addLinkedFiles(linkedFiles, skillDirectory, "references");
        addLinkedFiles(linkedFiles, skillDirectory, "templates");
        addLinkedFiles(linkedFiles, skillDirectory, "scripts");
        addLinkedFiles(linkedFiles, skillDirectory, "assets");
        return linkedFiles;
    }

    private void addLinkedFiles(List<Map<String, Object>> linkedFiles, Path skillDirectory, String directoryName) {
        File directory = skillDirectory.resolve(directoryName).toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        List<File> files = FileUtil.loopFiles(directory);
        Collections.sort(files);
        for (File file : files) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("group", directoryName);
            item.put("path", skillDirectory.relativize(file.toPath()).toString().replace('\\', '/'));
            linkedFiles.add(item);
        }
    }

    private SkillDescriptor findSkill(String name) {
        List<SkillDescriptor> skills = findAllSkills();
        for (SkillDescriptor descriptor : skills) {
            if (name.equals(descriptor.name) || name.equals(descriptor.directory.getFileName().toString())) {
                return descriptor;
            }
        }

        return null;
    }

    private List<SkillDescriptor> findAllSkills() {
        List<SkillDescriptor> results = new ArrayList<SkillDescriptor>();
        List<File> files = FileUtil.loopFiles(workspaceLayout.skillsDir().toFile(), file -> SKILL_FILE_NAME.equals(file.getName()));
        Collections.sort(files);
        for (File file : files) {
            SkillDescriptor descriptor = parseSkill(file.toPath());
            if (descriptor != null) {
                results.add(descriptor);
            }
        }

        return results;
    }

    private SkillDescriptor parseSkill(Path skillFile) {
        String content = FileUtil.readString(skillFile.toFile(), StandardCharsets.UTF_8);
        SkillFrontmatter frontmatter = parseFrontmatter(content);
        Path skillDirectory = skillFile.getParent();
        Path relative = workspaceLayout.skillsDir().relativize(skillDirectory);
        String category = relative.getNameCount() > 1 ? relative.getName(0).toString() : null;
        String path = relative.toString().replace('\\', '/');
        String name = StrUtil.blankToDefault(frontmatter.name, skillDirectory.getFileName().toString());
        String description = StrUtil.blankToDefault(frontmatter.description, deriveDescription(frontmatter.body));
        return new SkillDescriptor(name, description, category, skillDirectory, path, content);
    }

    private String deriveDescription(String body) {
        if (Utils.isBlank(body)) {
            return "";
        }

        String[] lines = body.split("\\r?\\n");
        for (String line : lines) {
            String candidate = line.trim();
            if (candidate.length() == 0 || candidate.startsWith("#")) {
                continue;
            }

            return candidate;
        }

        return "";
    }

    private SkillFrontmatter parseFrontmatter(String content) {
        SkillFrontmatter frontmatter = new SkillFrontmatter();
        if (!content.startsWith("---")) {
            frontmatter.body = content;
            return frontmatter;
        }

        int closingIndex = content.indexOf("\n---", 3);
        if (closingIndex < 0) {
            frontmatter.body = content;
            return frontmatter;
        }

        String yaml = content.substring(3, closingIndex).trim();
        frontmatter.body = content.substring(closingIndex + 4).trim();
        String[] lines = yaml.split("\\r?\\n");
        for (String line : lines) {
            int delimiterIndex = line.indexOf(':');
            if (delimiterIndex < 0) {
                continue;
            }

            String key = line.substring(0, delimiterIndex).trim();
            String value = line.substring(delimiterIndex + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }

            if ("name".equals(key)) {
                frontmatter.name = value;
            } else if ("description".equals(key)) {
                frontmatter.description = value;
            }
        }

        return frontmatter;
    }

    private static class SkillFrontmatter {
        private String name;
        private String description;
        private String body;
    }

    private static class SkillDescriptor {
        private final String name;
        private final String description;
        private final String category;
        private final Path directory;
        private final String path;
        private final String content;

        private SkillDescriptor(String name, String description, String category, Path directory, String path, String content) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.directory = directory;
            this.path = path;
            this.content = content;
        }
    }
}
