package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本地 skill 管理服务。
 */
public class LocalSkillService {
    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    /**
     * skill 启停偏好存储。
     */
    private final SqlitePreferenceStore preferenceStore;

    /**
     * 构造本地 skill 服务。
     */
    public LocalSkillService(AppConfig appConfig, SqlitePreferenceStore preferenceStore) {
        this.appConfig = appConfig;
        this.preferenceStore = preferenceStore;
        FileUtil.mkdir(appConfig.getRuntime().getSkillsDir());
    }

    /**
     * 列出 runtime 目录下可见的 skill 名称。
     */
    public List<String> listSkillNames() {
        File root = FileUtil.file(appConfig.getRuntime().getSkillsDir());
        if (!root.exists()) {
            return Collections.emptyList();
        }

        File[] children = root.listFiles();
        List<String> names = new ArrayList<String>();
        if (children == null) {
            return names;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                names.add(child.getName());
            }
        }

        Collections.sort(names);
        return names;
    }

    /**
     * 查看指定 skill 的 `SKILL.md` 内容。
     */
    public String inspect(String skillName) {
        File skillFile = FileUtil.file(appConfig.getRuntime().getSkillsDir(), skillName, "SKILL.md");
        if (!skillFile.exists()) {
            return "Skill not found: " + skillName;
        }

        return FileUtil.readUtf8String(skillFile);
    }

    /**
     * 启用指定来源键下的 skill。
     */
    public void enable(String sourceKey, String skillName) throws Exception {
        preferenceStore.setSkillEnabled(sourceKey, skillName, true);
    }

    /**
     * 禁用指定来源键下的 skill。
     */
    public void disable(String sourceKey, String skillName) throws Exception {
        preferenceStore.setSkillEnabled(sourceKey, skillName, false);
    }

    /**
     * 判断 skill 是否已启用。
     */
    public boolean isEnabled(String sourceKey, String skillName) throws Exception {
        return preferenceStore.isSkillEnabled(sourceKey, skillName);
    }

    /**
     * 渲染来源键下全部已启用的本地 skill 文本。
     */
    public String renderEnabledSkillPrompts(String sourceKey) throws Exception {
        StringBuilder buffer = new StringBuilder();
        List<String> skillNames = listSkillNames();
        for (String skillName : skillNames) {
            if (!isEnabled(sourceKey, skillName)) {
                continue;
            }

            File skillFile = FileUtil.file(appConfig.getRuntime().getSkillsDir(), skillName, "SKILL.md");
            if (!skillFile.exists()) {
                continue;
            }

            String skillBody = FileUtil.readUtf8String(skillFile).trim();
            if (StrUtil.isBlank(skillBody)) {
                continue;
            }

            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }

            buffer.append("[Local Skill: ").append(skillName).append("]\n").append(skillBody);
        }

        return buffer.toString();
    }
}
