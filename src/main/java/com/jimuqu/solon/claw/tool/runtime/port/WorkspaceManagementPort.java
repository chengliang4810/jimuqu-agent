package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询和维护人格工作区的同步端口。 */
public interface WorkspaceManagementPort {
    /** 查询工作区文件列表。 */
    Map<String, Object> getFiles();

    /** 查询工作区文件。 */
    Map<String, Object> getFile(String key);

    /** 保存工作区文件。 */
    Map<String, Object> saveFile(String key, String content);

    /** 恢复工作区文件。 */
    Map<String, Object> restoreFile(String key);

    /** 查询日记文件列表。 */
    Map<String, Object> listDiaryFiles();

    /** 查询日记文件。 */
    Map<String, Object> getDiaryFile(String relativePath);

    /** 查询记忆归档状态。 */
    Map<String, Object> memoryArchiveState();

    /** 运行记忆归档。 */
    Map<String, Object> runMemoryArchive() throws Exception;

    /** 恢复记忆归档。 */
    Map<String, Object> restoreMemoryArchive(String relativePath) throws Exception;
}
