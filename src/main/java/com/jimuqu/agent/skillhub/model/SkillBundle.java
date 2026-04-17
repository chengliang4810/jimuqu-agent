package com.jimuqu.agent.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skills Hub 统一技能包。
 */
@Getter
@Setter
@NoArgsConstructor
public class SkillBundle {
    private String name;
    private Map<String, String> files = new LinkedHashMap<String, String>();
    private String source;
    private String identifier;
    private String trustLevel;
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
