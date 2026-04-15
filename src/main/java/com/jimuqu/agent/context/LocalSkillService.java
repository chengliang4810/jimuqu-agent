package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.storage.SqlitePreferenceStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalSkillService {
    private final AppConfig appConfig;
    private final SqlitePreferenceStore preferenceStore;

    public LocalSkillService(AppConfig appConfig, SqlitePreferenceStore preferenceStore) {
        this.appConfig = appConfig;
        this.preferenceStore = preferenceStore;
        FileUtil.mkdir(appConfig.getRuntime().getSkillsDir());
    }

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

    public String inspect(String skillName) {
        File skillFile = FileUtil.file(appConfig.getRuntime().getSkillsDir(), skillName, "SKILL.md");
        if (!skillFile.exists()) {
            return "Skill not found: " + skillName;
        }

        return FileUtil.readUtf8String(skillFile);
    }

    public void enable(String sourceKey, String skillName) throws Exception {
        preferenceStore.setSkillEnabled(sourceKey, skillName, true);
    }

    public void disable(String sourceKey, String skillName) throws Exception {
        preferenceStore.setSkillEnabled(sourceKey, skillName, false);
    }

    public boolean isEnabled(String sourceKey, String skillName) throws Exception {
        return preferenceStore.isSkillEnabled(sourceKey, skillName);
    }

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
