package com.jimuqu.agent.storage.repository;

import com.jimuqu.agent.agent.AgentProfile;
import com.jimuqu.agent.agent.AgentProfileRepository;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class SqliteAgentProfileRepository implements AgentProfileRepository {
    private final SqliteDatabase database;

    @Override
    public AgentProfile save(AgentProfile profile) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into agent_profiles (agent_name, role_prompt, model, allowed_tools_json, skills_json, memory, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, profile.getAgentName());
            statement.setString(2, profile.getRolePrompt());
            statement.setString(3, profile.getModel());
            statement.setString(4, profile.getAllowedToolsJson());
            statement.setString(5, profile.getSkillsJson());
            statement.setString(6, profile.getMemory());
            statement.setLong(7, profile.getCreatedAt());
            statement.setLong(8, profile.getUpdatedAt());
            statement.executeUpdate();
            statement.close();
            return profile;
        } finally {
            connection.close();
        }
    }

    @Override
    public AgentProfile findByName(String agentName) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from agent_profiles where agent_name = ?");
            statement.setString(1, agentName);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public List<AgentProfile> listAll() throws Exception {
        List<AgentProfile> profiles = new ArrayList<AgentProfile>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from agent_profiles order by updated_at desc");
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    profiles.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return profiles;
    }

    private AgentProfile map(ResultSet rs) throws Exception {
        AgentProfile profile = new AgentProfile();
        profile.setAgentName(rs.getString("agent_name"));
        profile.setRolePrompt(rs.getString("role_prompt"));
        profile.setModel(rs.getString("model"));
        profile.setAllowedToolsJson(rs.getString("allowed_tools_json"));
        profile.setSkillsJson(rs.getString("skills_json"));
        profile.setMemory(rs.getString("memory"));
        profile.setCreatedAt(rs.getLong("created_at"));
        profile.setUpdatedAt(rs.getLong("updated_at"));
        return profile;
    }
}
