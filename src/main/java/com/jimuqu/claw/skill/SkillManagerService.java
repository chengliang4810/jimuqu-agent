package com.jimuqu.claw.skill;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.support.FileStoreSupport;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SkillManagerService {
    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]*$");

    private final WorkspaceLayout workspaceLayout;
    private final SkillCatalog skillCatalog;

    public SkillManagerService(WorkspaceLayout workspaceLayout, SkillCatalog skillCatalog) {
        this.workspaceLayout = workspaceLayout;
        this.skillCatalog = skillCatalog;
    }

    public Map<String, Object> manage(
            String action,
            String name,
            String content,
            String category,
            String filePath,
            String fileContent,
            String oldString,
            String newString,
            boolean replaceAll) {
        if ("create".equals(action)) {
            return create(name, content, category);
        }
        if ("edit".equals(action)) {
            return edit(name, content);
        }
        if ("patch".equals(action)) {
            return patch(name, filePath, oldString, newString, replaceAll);
        }
        if ("delete".equals(action)) {
            return delete(name);
        }
        if ("write_file".equals(action)) {
            return writeFile(name, filePath, fileContent);
        }
        if ("remove_file".equals(action)) {
            return removeFile(name, filePath);
        }

        return error("Unknown action: " + action);
    }

    private Map<String, Object> create(String name, String content, String category) {
        String nameError = validateName(name, "skill");
        if (nameError != null) {
            return error(nameError);
        }
        if (StrUtil.isBlank(content)) {
            return error("content is required for create");
        }
        if (skillCatalog.view(name).containsKey("success") && Boolean.TRUE.equals(skillCatalog.view(name).get("success"))) {
            return error("Skill already exists: " + name);
        }

        Path skillDir = resolveSkillDir(name, category);
        FileUtil.mkdir(skillDir.toFile());
        FileStoreSupport.writeUtf8Atomic(skillDir.resolve("SKILL.md"), content);

        Map<String, Object> result = success();
        result.put("message", "Skill created");
        result.put("path", workspaceLayout.skillsDir().relativize(skillDir).toString().replace('\\', '/'));
        return result;
    }

    private Map<String, Object> edit(String name, String content) {
        if (StrUtil.isBlank(content)) {
            return error("content is required for edit");
        }
        Path skillDir = findSkillDir(name);
        if (skillDir == null) {
            return error("Skill not found: " + name);
        }

        FileStoreSupport.writeUtf8Atomic(skillDir.resolve("SKILL.md"), content);
        Map<String, Object> result = success();
        result.put("message", "Skill updated");
        return result;
    }

    private Map<String, Object> patch(String name, String filePath, String oldString, String newString, boolean replaceAll) {
        if (StrUtil.isBlank(oldString)) {
            return error("old_string is required for patch");
        }
        if (newString == null) {
            return error("new_string is required for patch");
        }

        Path target = resolveSkillFile(name, filePath);
        if (target == null || !target.toFile().exists()) {
            return error("Skill file not found");
        }

        String current = FileUtil.readString(target.toFile(), StandardCharsets.UTF_8);
        int occurrences = StrUtil.count(current, oldString);
        if (occurrences == 0) {
            return error("old_string not found");
        }
        if (!replaceAll && occurrences > 1) {
            return error("old_string is not unique; use replace_all=true");
        }

        String updated = replaceAll ? current.replace(oldString, newString) : StrUtil.replace(current, oldString, newString, false);
        FileStoreSupport.writeUtf8Atomic(target, updated);

        Map<String, Object> result = success();
        result.put("message", "Skill file patched");
        result.put("replacements", replaceAll ? occurrences : 1);
        return result;
    }

    private Map<String, Object> delete(String name) {
        Path skillDir = findSkillDir(name);
        if (skillDir == null) {
            return error("Skill not found: " + name);
        }

        FileUtil.del(skillDir.toFile());
        Map<String, Object> result = success();
        result.put("message", "Skill deleted");
        return result;
    }

    private Map<String, Object> writeFile(String name, String filePath, String fileContent) {
        if (StrUtil.isBlank(filePath)) {
            return error("file_path is required for write_file");
        }
        if (fileContent == null) {
            return error("file_content is required for write_file");
        }

        Path target = resolveSkillFile(name, filePath);
        if (target == null) {
            return error("Skill not found: " + name);
        }

        FileStoreSupport.writeUtf8Atomic(target, fileContent);
        Map<String, Object> result = success();
        result.put("message", "Skill file written");
        result.put("path", workspaceLayout.skillsDir().relativize(target).toString().replace('\\', '/'));
        return result;
    }

    private Map<String, Object> removeFile(String name, String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return error("file_path is required for remove_file");
        }

        Path target = resolveSkillFile(name, filePath);
        if (target == null || !target.toFile().exists()) {
            return error("Skill file not found");
        }

        FileUtil.del(target.toFile());
        Map<String, Object> result = success();
        result.put("message", "Skill file removed");
        return result;
    }

    private Path resolveSkillDir(String name, String category) {
        Path base = workspaceLayout.skillsDir();
        if (StrUtil.isNotBlank(category)) {
            String categoryError = validateName(category, "category");
            if (categoryError != null) {
                throw new IllegalArgumentException(categoryError);
            }
            base = base.resolve(category);
        }

        return base.resolve(name).normalize();
    }

    private Path resolveSkillFile(String name, String filePath) {
        Path skillDir = findSkillDir(name);
        if (skillDir == null) {
            return null;
        }

        if (StrUtil.isBlank(filePath)) {
            return skillDir.resolve("SKILL.md");
        }

        Path target = skillDir.resolve(filePath).normalize();
        if (!target.startsWith(skillDir)) {
            throw new IllegalArgumentException("Path escapes skill directory: " + filePath);
        }

        return target;
    }

    private Path findSkillDir(String name) {
        Map<String, Object> skill = skillCatalog.view(name);
        if (!Boolean.TRUE.equals(skill.get("success"))) {
            return null;
        }

        Object path = skill.get("path");
        if (path == null) {
            return null;
        }

        return workspaceLayout.skillsDir().resolve(path.toString()).normalize();
    }

    private String validateName(String value, String kind) {
        if (StrUtil.isBlank(value)) {
            return kind + " name is required";
        }
        if (!VALID_NAME.matcher(value).matches()) {
            return "Invalid " + kind + " name: " + value;
        }

        return null;
    }

    private Map<String, Object> success() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.FALSE);
        result.put("error", message);
        return result;
    }
}
