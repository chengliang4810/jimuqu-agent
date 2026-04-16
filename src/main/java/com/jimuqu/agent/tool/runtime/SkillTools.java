package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.model.SkillDescriptor;
import com.jimuqu.agent.core.model.SkillView;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.support.constants.SkillConstants;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hermes 风格 skills 工具集合。
 */
public class SkillTools {
    /**
     * 本地技能目录服务。
     */
    private final LocalSkillService localSkillService;

    /**
     * checkpoint 服务。
     */
    private final CheckpointService checkpointService;

    /**
     * 会话仓储。
     */
    private final SessionRepository sessionRepository;

    /**
     * 当前来源键。
     */
    private final String sourceKey;

    /**
     * 构造技能工具。
     */
    public SkillTools(LocalSkillService localSkillService,
                      CheckpointService checkpointService,
                      SessionRepository sessionRepository,
                      String sourceKey) {
        this.localSkillService = localSkillService;
        this.checkpointService = checkpointService;
        this.sessionRepository = sessionRepository;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "skills_list", description = "List available skills. Optional category filter.")
    public String skillsList(@Param(name = "category", description = "可选分类名", required = false) String category) throws Exception {
        List<SkillDescriptor> skills = localSkillService.listSkills(category);
        List<SkillDescriptor> visible = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor descriptor : skills) {
            if (localSkillService.isVisible(sourceKey, descriptor.canonicalName())) {
                visible.add(descriptor);
            }
        }
        return ONode.serialize(visible);
    }

    @ToolMapping(name = "skill_view", description = "Load full SKILL.md or a supporting file from a skill directory.")
    public String skillView(@Param(name = "name", description = "技能名或 category/name") String name,
                            @Param(name = "filePath", description = "可选支持文件相对路径", required = false) String filePath) throws Exception {
        SkillView view = localSkillService.viewSkill(name, filePath);
        return ONode.serialize(view);
    }

    @ToolMapping(name = "skill_manage", description = "Create, patch, edit, delete or manage supporting files for a local skill.")
    public String skillManage(@Param(name = "action", description = "create、edit、patch、delete、write_file、remove_file") String action,
                              @Param(name = "name", description = "技能名或 category/name") String name,
                              @Param(name = "category", description = "create 时可选分类", required = false) String category,
                              @Param(name = "content", description = "create/edit 时的主文件内容", required = false) String content,
                              @Param(name = "oldText", description = "patch 时要匹配的旧文本", required = false) String oldText,
                              @Param(name = "newText", description = "patch 时替换后的新文本", required = false) String newText,
                              @Param(name = "filePath", description = "支持文件相对路径", required = false) String filePath,
                              @Param(name = "fileContent", description = "write_file 时写入的内容", required = false) String fileContent) throws Exception {
        if (SkillConstants.ACTION_CREATE.equalsIgnoreCase(action)) {
            checkpoint(Collections.singletonList(localSkillService.resolveSkillMainFile(name, category)));
            return ONode.serialize(localSkillService.createSkill(name, category, content));
        }
        if (SkillConstants.ACTION_EDIT.equalsIgnoreCase(action)) {
            checkpoint(skillFiles(name));
            return ONode.serialize(localSkillService.editSkill(name, content));
        }
        if (SkillConstants.ACTION_PATCH.equalsIgnoreCase(action)) {
            checkpoint(skillFiles(name));
            return localSkillService.patchSkill(name, oldText, newText, filePath);
        }
        if (SkillConstants.ACTION_DELETE.equalsIgnoreCase(action)) {
            checkpoint(skillFiles(name));
            return localSkillService.deleteSkill(name);
        }
        if (SkillConstants.ACTION_WRITE_FILE.equalsIgnoreCase(action)) {
            checkpoint(skillFiles(name));
            return localSkillService.writeSkillFile(name, filePath, fileContent);
        }
        if (SkillConstants.ACTION_REMOVE_FILE.equalsIgnoreCase(action)) {
            checkpoint(skillFiles(name));
            return localSkillService.removeSkillFile(name, filePath);
        }
        return "Unsupported skill_manage action";
    }

    /**
     * 收集技能目录中的全部文件，用于 checkpoint。
     */
    private List<File> skillFiles(String nameOrPath) throws Exception {
        SkillView view = localSkillService.viewSkill(nameOrPath, null);
        File skillDir = FileUtil.file(view.getDescriptor().getSkillDir());
        List<File> files = FileUtil.loopFiles(skillDir);
        if (files.isEmpty()) {
            files.add(FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME));
        }
        return files;
    }

    /**
     * 创建 checkpoint。
     */
    private void checkpoint(List<File> files) throws Exception {
        if (checkpointService == null) {
            return;
        }
        SessionRecord session = sessionRepository == null ? null : sessionRepository.getBoundSession(sourceKey);
        checkpointService.createCheckpoint(sourceKey, session == null ? null : session.getSessionId(), files);
    }
}
