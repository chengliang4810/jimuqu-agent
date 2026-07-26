package com.jimuqu.solon.claw.web;

import com.jimuqu.solon.claw.core.model.SessionSearchEntry;
import com.jimuqu.solon.claw.core.model.SessionSearchQuery;
import com.jimuqu.solon.claw.core.service.SessionSearchService;
import com.jimuqu.solon.claw.support.SecretRedactor;
import com.jimuqu.solon.claw.web.profile.DashboardProfileContext;
import com.jimuqu.solon.claw.web.profile.DashboardProfileNotFoundException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** 执行控制台搜索相关HTTP入口，负责请求参数转换与响应输出相关逻辑。 */
@Controller
public class DashboardSearchController {
    /** 与默认会话搜索服务一致的单次最大结果数。 */
    private static final int MAX_SEARCH_LIMIT = 10;

    /** 注入会话搜索服务，用于调用对应业务能力。 */
    private final SessionSearchService sessionSearchService;

    /** 解析请求指定的 Profile；旧单元测试构造路径为空时保持当前搜索。 */
    @Inject(required = false)
    private DashboardProfileContext profileContext;

    /**
     * 创建控制台搜索控制器实例，并注入运行所需依赖。
     *
     * @param sessionSearchService 会话搜索服务依赖。
     */
    public DashboardSearchController(SessionSearchService sessionSearchService) {
        this.sessionSearchService = sessionSearchService;
    }

    /**
     * 创建显式注入 Profile 上下文的搜索控制器，供嵌入测试和非 Solon 装配场景使用。
     *
     * @param sessionSearchService 会话搜索服务依赖。
     * @param profileContext Profile 请求上下文。
     */
    public DashboardSearchController(
            SessionSearchService sessionSearchService, DashboardProfileContext profileContext) {
        this.sessionSearchService = sessionSearchService;
        this.profileContext = profileContext;
    }

    /**
     * 执行搜索相关逻辑。
     *
     * @param context 当前请求或运行上下文。
     * @return 返回搜索结果。
     */
    @Mapping(value = "/api/search", method = MethodType.GET)
    public Map<String, Object> search(Context context) throws Exception {
        try {
            return DashboardResponse.ok(searchData(context));
        } catch (DashboardProfileNotFoundException e) {
            return DashboardResponse.error(context, 404, "PROFILE_NOT_FOUND", e);
        } catch (IllegalArgumentException e) {
            return DashboardResponse.error(context, 400, "SEARCH_BAD_REQUEST", e);
        }
    }

    /** 执行当前或显式 Profile 的只读会话搜索。 */
    private Map<String, Object> searchData(Context context) throws Exception {
        boolean allProfiles = isAllProfiles(context);
        DashboardProfileContext.Scope scope = allProfiles ? null : resolve(context);
        SessionSearchQuery query = new SessionSearchQuery();
        query.setSourceKey(first(context.param("sourceKey"), context.param("source")));
        query.setSessionId(context.param("sessionId"));
        query.setRunId(context.param("runId"));
        query.setToolName(context.param("toolName"));
        query.setChannel(first(context.param("channel"), context.param("platform")));
        query.setQuery(context.param("q"));
        query.setTimeFrom(asLong(context.param("timeFrom")));
        query.setTimeTo(asLong(context.param("timeTo")));
        query.setSummarize(Boolean.parseBoolean(context.param("summarize")));
        query.setConversationOnly(Boolean.parseBoolean(context.param("conversation_only")));
        query.setLimit(context.paramAsInt("limit", 10));
        if (!allProfiles && scope != null && !scope.isCurrent()) {
            query.setProfile(scope.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<SessionSearchEntry> entries =
                allProfiles ? searchAllProfiles(query) : searchSingleProfile(query, scope);
        for (SessionSearchEntry entry : entries) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("session_id", safe(entry.getSessionId(), 200));
            row.put("branch_name", safe(entry.getBranchName(), 400));
            row.put("title", safe(entry.getTitle(), 400));
            row.put("updated_at", entry.getUpdatedAt());
            row.put("match_preview", safe(entry.getMatchPreview(), 2000));
            row.put("summary", safe(entry.getSummary(), 4000));
            row.put("run_id", safe(entry.getRunId(), 200));
            row.put("tool_name", safe(entry.getToolName(), 200));
            row.put("channel", safe(entry.getChannel(), 400));
            String resultProfile = entry.getProfile();
            if ((resultProfile == null || resultProfile.trim().length() == 0)
                    && scope != null
                    && !scope.isCurrent()) {
                resultProfile = scope.getName();
            }
            if (resultProfile != null && resultProfile.trim().length() > 0) {
                row.put("profile", safe(resultProfile, 100));
            }
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("results", rows);
        result.put("tokenizer", "fts5/cjk-ngram-fallback");
        return result;
    }

    /** 搜索当前或单个显式 Profile；尚未初始化数据库的命名 Profile 返回空结果。 */
    private List<SessionSearchEntry> searchSingleProfile(
            SessionSearchQuery query, DashboardProfileContext.Scope scope) throws Exception {
        if (scope != null
                && !scope.isCurrent()
                && !Files.isRegularFile(scope.getHome().resolve("data").resolve("state.db"))) {
            return new ArrayList<SessionSearchEntry>();
        }
        return sessionSearchService.search(query);
    }

    /** 聚合机器上全部 Profile 的只读搜索结果，并在全局排序后执行最终数量限制。 */
    private List<SessionSearchEntry> searchAllProfiles(SessionSearchQuery query) throws Exception {
        if (profileContext == null) {
            throw new IllegalArgumentException("Dashboard Profile aggregation is unavailable.");
        }
        List<SessionSearchEntry> merged = new ArrayList<SessionSearchEntry>();
        for (String profile : profileContext.profileManager().listProfileNames()) {
            DashboardProfileContext.Scope scope = profileContext.resolve(profile);
            if (!scope.isCurrent()
                    && !Files.isRegularFile(scope.getHome().resolve("data").resolve("state.db"))) {
                continue;
            }
            SessionSearchQuery scopedQuery = copyQuery(query);
            scopedQuery.setProfile(scope.isCurrent() ? null : scope.getName());
            for (SessionSearchEntry entry : sessionSearchService.search(scopedQuery)) {
                if (entry == null) {
                    continue;
                }
                entry.setProfile(scope.getName());
                merged.add(entry);
            }
        }
        sortMergedResults(merged, query.getSort());
        int limit =
                Math.max(
                        1,
                        Math.min(
                                query.getLimit() <= 0 ? MAX_SEARCH_LIMIT : query.getLimit(),
                                MAX_SEARCH_LIMIT));
        return new ArrayList<SessionSearchEntry>(merged.subList(0, Math.min(limit, merged.size())));
    }

    /** 复制搜索参数，防止跨 Profile 查询互相覆盖 Profile 作用域。 */
    private SessionSearchQuery copyQuery(SessionSearchQuery source) {
        SessionSearchQuery target = new SessionSearchQuery();
        target.setSourceKey(source.getSourceKey());
        target.setSessionId(source.getSessionId());
        target.setRunId(source.getRunId());
        target.setToolName(source.getToolName());
        target.setChannel(source.getChannel());
        target.setQuery(source.getQuery());
        target.setAroundMessageId(source.getAroundMessageId());
        target.setSort(source.getSort());
        target.setWindow(source.getWindow());
        target.setRoleFilter(source.getRoleFilter());
        target.setTimeFrom(source.getTimeFrom());
        target.setTimeTo(source.getTimeTo());
        target.setSummarize(source.isSummarize());
        target.setConversationOnly(source.isConversationOnly());
        target.setLimit(source.getLimit());
        return target;
    }

    /** 按单 Profile 搜索的相关性与时间语义对聚合结果执行稳定全局排序。 */
    private void sortMergedResults(List<SessionSearchEntry> entries, String sort) {
        final String normalizedSort = sort == null ? "" : sort.trim();
        Collections.sort(
                entries,
                new Comparator<SessionSearchEntry>() {
                    /** 比较跨 Profile 搜索条目。 */
                    @Override
                    public int compare(SessionSearchEntry left, SessionSearchEntry right) {
                        if ("oldest".equalsIgnoreCase(normalizedSort)) {
                            int oldest = Long.compare(left.getUpdatedAt(), right.getUpdatedAt());
                            if (oldest != 0) {
                                return oldest;
                            }
                        } else if ("newest".equalsIgnoreCase(normalizedSort)) {
                            int newest = Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
                            if (newest != 0) {
                                return newest;
                            }
                        } else {
                            int score = Long.compare(right.getScore(), left.getScore());
                            if (score != 0) {
                                return score;
                            }
                            int updated = Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
                            if (updated != 0) {
                                return updated;
                            }
                        }
                        int profile = text(left.getProfile()).compareTo(text(right.getProfile()));
                        if (profile != 0) {
                            return profile;
                        }
                        return text(left.getSessionId()).compareTo(text(right.getSessionId()));
                    }
                });
    }

    /** 判断请求是否显式要求聚合全部 Profile。 */
    private boolean isAllProfiles(Context context) {
        String requested = DashboardProfileContext.requestedProfile(context);
        return requested != null && "all".equalsIgnoreCase(requested.trim());
    }

    /** 解析并校验 query.profile，空值和 current 保持旧行为。 */
    private DashboardProfileContext.Scope resolve(Context context) {
        String requested = DashboardProfileContext.requestedProfile(context);
        if (profileContext == null) {
            if (requested == null
                    || requested.trim().length() == 0
                    || "current".equalsIgnoreCase(requested.trim())) {
                return null;
            }
            throw new IllegalArgumentException("Dashboard Profile scope is unavailable.");
        }
        return profileContext.resolve(requested);
    }

    /**
     * 执行first相关逻辑。
     *
     * @param left 左侧比较对象。
     * @param right 右侧比较对象。
     * @return 返回first结果。
     */
    private String first(String left, String right) {
        return left == null || left.trim().length() == 0 ? right : left;
    }

    /** 把可空文本规范为稳定排序字符串。 */
    private String text(String value) {
        return value == null ? "" : value;
    }

    /**
     * 执行as长整型相关逻辑。
     *
     * @param value 待规范化或校验的原始值。
     * @return 返回as Long结果。
     */
    private long asLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 执行安全相关逻辑。
     *
     * @param value 待规范化或校验的原始值。
     * @param maxLength 最大保留字符数。
     * @return 返回safe结果。
     */
    private String safe(String value, int maxLength) {
        return SecretRedactor.redact(value, maxLength);
    }
}
