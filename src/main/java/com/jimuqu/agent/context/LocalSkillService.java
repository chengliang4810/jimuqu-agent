package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.SkillDescriptor;
import com.jimuqu.agent.core.model.SkillView;
import com.jimuqu.agent.core.service.SkillCatalogService;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.constants.SkillConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地技能目录服务，支持 Hermes 风格分类目录与渐进披露读取。
 */
public class LocalSkillService implements SkillCatalogService {
    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    /**
     * 技能可见性偏好存储。
     */
    private final SqlitePreferenceStore preferenceStore;

    /**
     * 构造本地技能服务。
     */
    public LocalSkillService(AppConfig appConfig, SqlitePreferenceStore preferenceStore) {
        this.appConfig = appConfig;
        this.preferenceStore = preferenceStore;
        FileUtil.mkdir(appConfig.getRuntime().getSkillsDir());
    }

    /**
     * 为兼容旧命令面，返回全部可见技能的规范名。
     */
    public List<String> listSkillNames() {
        try {
            List<SkillDescriptor> skills = listSkills(null);
            List<String> names = new ArrayList<String>();
            for (SkillDescriptor descriptor : skills) {
                names.add(descriptor.canonicalName());
            }
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 为兼容旧命令面，查看技能主文件。
     */
    public String inspect(String skillName) {
        try {
            SkillView skillView = viewSkill(skillName, null);
            return skillView.getContent();
        } catch (Exception e) {
            return "Skill not found: " + skillName;
        }
    }

    /**
     * 将技能显式设为可见。
     */
    public void enable(String sourceKey, String skillName) throws Exception {
        setVisible(sourceKey, skillName, true);
    }

    /**
     * 将技能显式设为隐藏。
     */
    public void disable(String sourceKey, String skillName) throws Exception {
        setVisible(sourceKey, skillName, false);
    }

    /**
     * 当前实现中技能默认可见，只有显式 disable 才隐藏。
     */
    public boolean isEnabled(String sourceKey, String skillName) throws Exception {
        return isVisible(sourceKey, skillName);
    }

    @Override
    public List<SkillDescriptor> listSkills(String category) throws Exception {
        File root = FileUtil.file(appConfig.getRuntime().getSkillsDir());
        if (!root.exists()) {
            return Collections.emptyList();
        }

        List<SkillDescriptor> skills = new ArrayList<SkillDescriptor>();
        collectSkills(root, skills);
        Collections.sort(skills, new Comparator<SkillDescriptor>() {
            @Override
            public int compare(SkillDescriptor left, SkillDescriptor right) {
                String leftCategory = StrUtil.nullToDefault(left.getCategory(), "");
                String rightCategory = StrUtil.nullToDefault(right.getCategory(), "");
                int result = leftCategory.compareTo(rightCategory);
                if (result != 0) {
                    return result;
                }
                return left.getName().compareTo(right.getName());
            }
        });

        if (StrUtil.isBlank(category)) {
            return skills;
        }

        List<SkillDescriptor> filtered = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor descriptor : skills) {
            if (category.equals(descriptor.getCategory())) {
                filtered.add(descriptor);
            }
        }
        return filtered;
    }

    @Override
    public SkillView viewSkill(String nameOrPath, String filePath) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }

        File skillDir = FileUtil.file(descriptor.getSkillDir());
        File target = StrUtil.isBlank(filePath)
                ? FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME)
                : FileUtil.file(skillDir, filePath);
        if (!target.exists()) {
            throw new IllegalStateException("Skill file not found: " + target.getAbsolutePath());
        }

        SkillView view = new SkillView();
        view.setDescriptor(descriptor);
        view.setFilePath(filePath);
        view.setContent(FileUtil.readUtf8String(target));
        view.setLinkedFiles(new ArrayList<String>(descriptor.getLinkedFiles()));
        return view;
    }

    @Override
    public String renderSkillIndexPrompt(String sourceKey) throws Exception {
        List<SkillDescriptor> skills = listSkills(null);
        Map<String, List<SkillDescriptor>> grouped = new LinkedHashMap<String, List<SkillDescriptor>>();
        for (SkillDescriptor descriptor : skills) {
            if (!isVisible(sourceKey, descriptor.canonicalName())) {
                continue;
            }
            String category = StrUtil.blankToDefault(descriptor.getCategory(), SkillConstants.DEFAULT_CATEGORY);
            List<SkillDescriptor> items = grouped.get(category);
            if (items == null) {
                items = new ArrayList<SkillDescriptor>();
                grouped.put(category, items);
            }
            items.add(descriptor);
        }

        if (grouped.isEmpty()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append("## Skills (渐进披露)\n");
        buffer.append("在回复前先浏览下列技能索引；如果某个技能明显匹配当前任务，请先用 skill_view(name) 加载全文再执行。\n");
        buffer.append("<available_skills>\n");
        for (Map.Entry<String, List<SkillDescriptor>> entry : grouped.entrySet()) {
            buffer.append("  ").append(entry.getKey()).append(":\n");
            for (SkillDescriptor descriptor : entry.getValue()) {
                buffer.append("    - ")
                        .append(descriptor.canonicalName())
                        .append(": ")
                        .append(StrUtil.nullToDefault(descriptor.getDescription(), ""))
                        .append('\n');
            }
        }
        buffer.append("</available_skills>");
        return buffer.toString();
    }

    @Override
    public boolean isVisible(String sourceKey, String canonicalName) throws Exception {
        return preferenceStore.isSkillEnabled(sourceKey, canonicalName);
    }

    @Override
    public void setVisible(String sourceKey, String canonicalName, boolean visible) throws Exception {
        preferenceStore.setSkillEnabled(sourceKey, canonicalName, visible);
    }

    /**
     * 创建新技能。
     */
    public SkillDescriptor createSkill(String name, String category, String content) {
        File skillDir = resolveSkillDir(name, category);
        if (skillDir.exists()) {
            throw new IllegalStateException("Skill already exists: " + canonicalName(category, name));
        }
        writeSkillMainFile(skillDir, content);
        return buildDescriptor(skillDir, normalizeCategory(category));
    }

    /**
     * 全量改写技能主文件。
     */
    public SkillDescriptor editSkill(String nameOrPath, String content) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        writeSkillMainFile(FileUtil.file(descriptor.getSkillDir()), content);
        return buildDescriptor(FileUtil.file(descriptor.getSkillDir()), descriptor.getCategory());
    }

    /**
     * 在技能主文件或支持文件中做定点替换。
     */
    public String patchSkill(String nameOrPath, String oldText, String newText, String filePath) throws Exception {
        SkillView view = viewSkill(nameOrPath, filePath);
        if (StrUtil.isBlank(oldText) || !view.getContent().contains(oldText)) {
            throw new IllegalStateException("Patch target not found.");
        }
        File target = resolveSkillFile(view.getDescriptor(), filePath);
        FileUtil.writeUtf8String(view.getContent().replace(oldText, StrUtil.nullToEmpty(newText)), target);
        return "Patched skill file: " + target.getAbsolutePath();
    }

    /**
     * 删除技能目录。
     */
    public String deleteSkill(String nameOrPath) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        FileUtil.del(FileUtil.file(descriptor.getSkillDir()));
        return "Deleted skill: " + descriptor.canonicalName();
    }

    /**
     * 写入技能支持文件。
     */
    public String writeSkillFile(String nameOrPath, String filePath, String fileContent) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        File target = resolveSkillFile(descriptor, filePath);
        FileUtil.mkParentDirs(target);
        FileUtil.writeUtf8String(StrUtil.nullToEmpty(fileContent), target);
        return "Wrote skill file: " + target.getAbsolutePath();
    }

    /**
     * 删除技能支持文件。
     */
    public String removeSkillFile(String nameOrPath, String filePath) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        File target = resolveSkillFile(descriptor, filePath);
        if (!target.exists()) {
            throw new IllegalStateException("Skill file not found: " + target.getAbsolutePath());
        }
        FileUtil.del(target);
        return "Removed skill file: " + target.getAbsolutePath();
    }

    /**
     * 预测新技能主文件路径。
     */
    public File resolveSkillMainFile(String name, String category) {
        return FileUtil.file(resolveSkillDir(name, category), SkillConstants.SKILL_FILE_NAME);
    }

    /**
     * 递归扫描根目录与单层分类目录中的技能。
     */
    private void collectSkills(File root, List<SkillDescriptor> output) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }

            File directSkill = FileUtil.file(child, SkillConstants.SKILL_FILE_NAME);
            if (directSkill.exists()) {
                output.add(buildDescriptor(child, null));
                continue;
            }

            File[] nestedChildren = child.listFiles();
            if (nestedChildren == null) {
                continue;
            }
            for (File nested : nestedChildren) {
                if (nested.isDirectory() && FileUtil.file(nested, SkillConstants.SKILL_FILE_NAME).exists()) {
                    output.add(buildDescriptor(nested, child.getName()));
                }
            }
        }
    }

    /**
     * 构建技能元数据。
     */
    private SkillDescriptor buildDescriptor(File skillDir, String category) {
        File skillFile = FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME);
        String content = FileUtil.readUtf8String(skillFile);
        SkillDescriptor descriptor = new SkillDescriptor();
        descriptor.setName(skillDir.getName());
        descriptor.setCategory(category);
        descriptor.setSkillDir(skillDir.getAbsolutePath());
        descriptor.setDescription(extractDescription(skillDir.getName(), content));
        descriptor.setLinkedFiles(scanLinkedFiles(skillDir));
        return descriptor;
    }

    /**
     * 通过规范名或路径定位技能。
     */
    private SkillDescriptor findDescriptor(String nameOrPath) throws Exception {
        for (SkillDescriptor descriptor : listSkills(null)) {
            if (descriptor.canonicalName().equals(nameOrPath) || descriptor.getName().equals(nameOrPath)) {
                return descriptor;
            }
        }
        return null;
    }

    /**
     * 从 SKILL.md 中提取描述；若无 frontmatter，则回退到首行正文。
     */
    private String extractDescription(String fallbackName, String content) {
        if (StrUtil.isBlank(content)) {
            return fallbackName;
        }

        if (content.startsWith("---")) {
            String[] lines = content.split("\\R");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if ("---".equals(line)) {
                    break;
                }
                if (line.startsWith("description:")) {
                    return line.substring("description:".length()).trim().replace("\"", "").replace("'", "");
                }
            }
        }

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                continue;
            }
            return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
        }
        return fallbackName;
    }

    /**
     * 扫描技能支持文件列表。
     */
    private List<String> scanLinkedFiles(File skillDir) {
        List<String> linkedFiles = new ArrayList<String>();
        addRelativeFiles(skillDir, SkillConstants.REFERENCES_DIR, linkedFiles);
        addRelativeFiles(skillDir, SkillConstants.TEMPLATES_DIR, linkedFiles);
        addRelativeFiles(skillDir, SkillConstants.SCRIPTS_DIR, linkedFiles);
        addRelativeFiles(skillDir, SkillConstants.ASSETS_DIR, linkedFiles);
        Collections.sort(linkedFiles);
        return linkedFiles;
    }

    /**
     * 扫描指定支持目录内文件。
     */
    private void addRelativeFiles(File skillDir, String childDirName, List<String> output) {
        File childDir = FileUtil.file(skillDir, childDirName);
        if (!childDir.exists() || !childDir.isDirectory()) {
            return;
        }

        List<File> files = FileUtil.loopFiles(childDir);
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            String absolute = file.getAbsolutePath();
            String root = skillDir.getAbsolutePath() + File.separator;
            if (absolute.startsWith(root)) {
                output.add(absolute.substring(root.length()).replace(File.separatorChar, '/'));
            }
        }
    }

    /**
     * 解析技能目录。
     */
    private File resolveSkillDir(String name, String category) {
        String normalizedCategory = normalizeCategory(category);
        if (StrUtil.isBlank(normalizedCategory)) {
            return FileUtil.file(appConfig.getRuntime().getSkillsDir(), name);
        }
        return FileUtil.file(appConfig.getRuntime().getSkillsDir(), normalizedCategory, name);
    }

    /**
     * 规范化分类值。
     */
    private String normalizeCategory(String category) {
        if (StrUtil.isBlank(category) || SkillConstants.DEFAULT_CATEGORY.equalsIgnoreCase(category)) {
            return null;
        }
        return category.trim();
    }

    /**
     * 写技能主文件并创建默认目录结构。
     */
    private void writeSkillMainFile(File skillDir, String content) {
        FileUtil.mkdir(skillDir);
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.REFERENCES_DIR));
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.TEMPLATES_DIR));
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.SCRIPTS_DIR));
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.ASSETS_DIR));
        FileUtil.writeUtf8String(StrUtil.nullToEmpty(content), FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME));
    }

    /**
     * 解析技能支持文件路径。
     */
    private File resolveSkillFile(SkillDescriptor descriptor, String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return FileUtil.file(descriptor.getSkillDir(), SkillConstants.SKILL_FILE_NAME);
        }
        return FileUtil.file(descriptor.getSkillDir(), filePath);
    }

    /**
     * 生成规范名。
     */
    private String canonicalName(String category, String name) {
        return StrUtil.isBlank(category) ? name : category + "/" + name;
    }
}
