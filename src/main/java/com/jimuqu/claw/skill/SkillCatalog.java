package com.jimuqu.claw.skill;

import java.util.List;
import java.util.Map;

public interface SkillCatalog {
    List<Map<String, Object>> list();

    default Map<String, Object> view(String name) {
        return view(name, null);
    }

    Map<String, Object> view(String name, String filePath);
}
