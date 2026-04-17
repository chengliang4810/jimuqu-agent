package com.jimuqu.agent.core.service;

import com.jimuqu.agent.skillhub.model.HubInstallRecord;
import com.jimuqu.agent.skillhub.model.ScanResult;
import com.jimuqu.agent.skillhub.model.SkillBrowseResult;
import com.jimuqu.agent.skillhub.model.SkillMeta;

import java.util.List;

/**
 * Skills Hub 服务接口。
 */
public interface SkillHubService {
    SkillBrowseResult browse(String sourceFilter, int page, int pageSize) throws Exception;

    SkillBrowseResult search(String query, String sourceFilter, int limit) throws Exception;

    SkillMeta inspect(String identifier) throws Exception;

    HubInstallRecord install(String identifier, String category, boolean force) throws Exception;

    List<HubInstallRecord> listInstalled() throws Exception;

    List<HubInstallRecord> check(String name) throws Exception;

    List<HubInstallRecord> update(String name, boolean force) throws Exception;

    List<ScanResult> audit(String name) throws Exception;

    String uninstall(String name) throws Exception;

    List<com.jimuqu.agent.skillhub.model.TapRecord> listTaps() throws Exception;

    String addTap(String repo, String path) throws Exception;

    String removeTap(String repo) throws Exception;
}
