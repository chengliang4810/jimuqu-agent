package com.jimuqu.agent.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能扫描结果。
 */
@Getter
@Setter
@NoArgsConstructor
public class ScanResult {
    private String skillName;
    private String source;
    private String trustLevel;
    private String verdict;
    private List<Finding> findings = new ArrayList<Finding>();
    private String scannedAt;
    private String summary;
}
