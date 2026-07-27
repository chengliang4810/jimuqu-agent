package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层管理媒体索引的同步端口。 */
public interface MediaManagementPort {
    /** 查询媒体列表。 */
    Map<String, Object> list(String platform, int limit) throws Exception;

    /** 索引本地媒体。 */
    Map<String, Object> indexLocal(Map<String, Object> body) throws Exception;

    /** 查询媒体详情。 */
    Map<String, Object> detail(String mediaId) throws Exception;

    /** 刷新媒体索引。 */
    Map<String, Object> refresh(String mediaId) throws Exception;

    /** 下载媒体。 */
    Map<String, Object> download(String mediaId) throws Exception;

    /** 获取媒体引用。 */
    Map<String, Object> reference(String mediaId) throws Exception;
}
