package com.jimuqu.agent.web;

import com.jimuqu.agent.context.PersonaWorkspaceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 人格工作区文件服务。
 */
public class DashboardWorkspaceService {
    private final PersonaWorkspaceService personaWorkspaceService;

    public DashboardWorkspaceService(PersonaWorkspaceService personaWorkspaceService) {
        this.personaWorkspaceService = personaWorkspaceService;
    }

    public Map<String, Object> getFiles() {
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        for (String key : personaWorkspaceService.orderedKeys()) {
            files.add(describeFile(key));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("files", files);
        return result;
    }

    public Map<String, Object> getFile(String key) {
        return describeFile(key);
    }

    public Map<String, Object> saveFile(String key, String content) {
        personaWorkspaceService.write(key, content);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("file", describeFile(key));
        return result;
    }

    public Map<String, Object> restoreFile(String key) {
        personaWorkspaceService.restoreTemplate(key);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("file", describeFile(key));
        return result;
    }

    private Map<String, Object> describeFile(String key) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key);
        result.put("name", personaWorkspaceService.fileName(key));
        result.put("path", personaWorkspaceService.absolutePath(key));
        result.put("exists", personaWorkspaceService.exists(key));
        result.put("content", personaWorkspaceService.read(key));
        return result;
    }
}
