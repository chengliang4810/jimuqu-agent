package com.jimuqu.agent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.noear.snack4.ONode;

import java.util.Arrays;
import java.util.List;

public class AgentProfileService {
    private final AgentProfileRepository repository;

    public AgentProfileService(AgentProfileRepository repository) {
        this.repository = repository;
    }

    public AgentProfile ensureDefault(String agentName, String rolePrompt) throws Exception {
        AgentProfile existing = repository.findByName(agentName);
        if (existing != null) return existing;
        long now = System.currentTimeMillis();
        AgentProfile profile = new AgentProfile();
        profile.setAgentName(agentName);
        profile.setRolePrompt(StrUtil.blankToDefault(rolePrompt, "Project worker agent."));
        profile.setModel("");
        profile.setAllowedToolsJson(toJson(Arrays.asList("files", "commands", "tools")));
        profile.setSkillsJson("[]");
        profile.setMemory("");
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return repository.save(profile);
    }

    public String handleCommand(String args) throws Exception {
        String[] parts = StrUtil.nullToEmpty(args).trim().split("\\s+", 3);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? "list" : parts[0].toLowerCase();
        if ("list".equals(action)) return formatList();
        if ("create".equals(action)) return create(parts);
        if ("show".equals(action)) return formatShow(requireProfile(parts, "Usage: /agent show <name>"));
        if ("model".equals(action)) return updateModel(parts);
        if ("tools".equals(action)) return updateTools(parts);
        if ("skills".equals(action)) return updateSkills(parts);
        if ("memory".equals(action)) return appendMemory(parts);
        return "Usage: /agent list/create/show/model/tools/skills/memory";
    }

    public AgentProfile findByName(String name) throws Exception {
        return repository.findByName(name);
    }

    private String create(String[] parts) throws Exception {
        if (parts.length < 2 || StrUtil.isBlank(parts[1])) return "Usage: /agent create <name> [role prompt]";
        String role = parts.length > 2 ? parts[2] : "Project worker agent.";
        AgentProfile profile = ensureDefault(parts[1], role);
        return "Created Agent: " + profile.getAgentName();
    }

    private String updateModel(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2])) return "Usage: /agent model <name> <model>";
        AgentProfile profile = ensureDefault(parts[1], "Project worker agent.");
        profile.setModel(parts[2].trim());
        profile.setUpdatedAt(System.currentTimeMillis());
        repository.save(profile);
        return "Updated Agent model: " + profile.getAgentName() + " -> " + profile.getModel();
    }

    private String updateTools(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2])) return "Usage: /agent tools <name> <tool1,tool2>";
        AgentProfile profile = ensureDefault(parts[1], "Project worker agent.");
        profile.setAllowedToolsJson(csvToJson(parts[2]));
        profile.setUpdatedAt(System.currentTimeMillis());
        repository.save(profile);
        return "Updated Agent tools: " + profile.getAgentName();
    }

    private String updateSkills(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2])) return "Usage: /agent skills <name> <skill1,skill2>";
        AgentProfile profile = ensureDefault(parts[1], "Project worker agent.");
        profile.setSkillsJson(csvToJson(parts[2]));
        profile.setUpdatedAt(System.currentTimeMillis());
        repository.save(profile);
        return "Updated Agent skills: " + profile.getAgentName();
    }

    private String appendMemory(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2])) return "Usage: /agent memory <name> <memory text>";
        AgentProfile profile = ensureDefault(parts[1], "Project worker agent.");
        profile.setMemory(StrUtil.nullToEmpty(profile.getMemory()) + (StrUtil.isBlank(profile.getMemory()) ? "" : "\n") + parts[2].trim());
        profile.setUpdatedAt(System.currentTimeMillis());
        repository.save(profile);
        return "Appended Agent memory: " + profile.getAgentName();
    }

    private AgentProfile requireProfile(String[] parts, String usage) throws Exception {
        if (parts.length < 2 || StrUtil.isBlank(parts[1])) throw new IllegalStateException(usage);
        AgentProfile profile = repository.findByName(parts[1]);
        if (profile == null) throw new IllegalStateException("Agent not found: " + parts[1]);
        return profile;
    }

    private String formatList() throws Exception {
        List<AgentProfile> profiles = repository.listAll();
        if (CollUtil.isEmpty(profiles)) return "No agents. Use /agent create <name> <role>.";
        StringBuilder builder = new StringBuilder("Agents:");
        for (AgentProfile profile : profiles) {
            builder.append("\n- ").append(profile.getAgentName());
            if (StrUtil.isNotBlank(profile.getModel())) builder.append(" model=").append(profile.getModel());
        }
        return builder.toString();
    }

    private String formatShow(AgentProfile profile) {
        return "Agent: " + profile.getAgentName()
                + "\nRole: " + StrUtil.nullToDefault(profile.getRolePrompt(), "")
                + "\nModel: " + StrUtil.blankToDefault(profile.getModel(), "default")
                + "\nTools: " + StrUtil.blankToDefault(profile.getAllowedToolsJson(), "[]")
                + "\nSkills: " + StrUtil.blankToDefault(profile.getSkillsJson(), "[]")
                + "\nMemory: " + StrUtil.blankToDefault(profile.getMemory(), "none");
    }

    private String csvToJson(String csv) {
        return toJson(Arrays.asList(csv.split("\\s*,\\s*")));
    }

    private String toJson(Object value) {
        return ONode.serialize(value);
    }
}
