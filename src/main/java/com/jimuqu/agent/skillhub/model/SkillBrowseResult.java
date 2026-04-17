package com.jimuqu.agent.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Browse/search 结果页。
 */
@Getter
@Setter
@NoArgsConstructor
public class SkillBrowseResult {
    private List<SkillMeta> items = new ArrayList<SkillMeta>();
    private int total;
    private int page;
    private int pageSize;
    private List<String> timedOutSources = new ArrayList<String>();
}
