package com.jimuqu.agent.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能目录中的最小元数据描述。
 */
@Getter
@Setter
@NoArgsConstructor
public class SkillDescriptor {
    /**
     * 技能名。
     */
    private String name;

    /**
     * 分类名，为空表示根目录技能。
     */
    private String category;

    /**
     * 技能描述。
     */
    private String description;

    /**
     * 技能目录绝对路径。
     */
    private String skillDir;

    /**
     * 目录中可见的支持文件相对路径列表。
     */
    private List<String> linkedFiles = new ArrayList<String>();

    /**
     * 返回用于展示/定位的规范名。
     */
    public String canonicalName() {
        return category == null || category.trim().length() == 0 ? name : category + "/" + name;
    }
}
