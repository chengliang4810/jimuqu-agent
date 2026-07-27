package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.SecureTokenCompare;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.noear.snack4.ONode;
import org.noear.solon.core.handle.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dashboard 访问控制与 token 注入服务。 */
public class DashboardAuthService {
    /** 记录 Dashboard 鉴权安全日志。 */
    private static final Logger log = LoggerFactory.getLogger(DashboardAuthService.class);

    /** 默认弱口令常量，仅用于诊断告警，不能作为空配置回退令牌。 */
    private static final String DEFAULT_WEAK_TOKEN = "admin";

    /** 避免默认弱口令告警在测试或热刷新时刷屏。 */
    private static final AtomicBoolean WEAK_TOKEN_WARNING_LOGGED = new AtomicBoolean(false);

    /** Dashboard 短会话 Cookie 名称。 */
    public static final String SESSION_COOKIE_NAME = "solonclaw_dashboard_session";

    /** Dashboard 短会话 Cookie 仅发送到 API 路径。 */
    private static final String SESSION_COOKIE_PATH = "/api";

    /** Dashboard 短会话空闲超时为 30 分钟。 */
    private static final long SESSION_IDLE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    /** Dashboard 短会话绝对超时为 8 小时。 */
    private static final long SESSION_ABSOLUTE_TIMEOUT_MILLIS = 8L * 60L * 60L * 1000L;

    /** Dashboard 短会话 Cookie 的最长浏览器保留时间。 */
    private static final int SESSION_COOKIE_MAX_AGE_SECONDS = 8 * 60 * 60;

    /** 单进程最多保留的 Dashboard 短会话数量。 */
    private static final int MAX_SESSION_COUNT = 1024;

    /** 每个 Dashboard 短会话使用 256 位随机票据。 */
    private static final int SESSION_TICKET_BYTES = 32;

    /** 公开API路径列表的统一常量值。 */
    private static final List<String> PUBLIC_API_PATHS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "/api/status",
                            "/api/config/defaults",
                            "/api/config/schema",
                            "/api/model/info"));

    /** 注入应用配置，用于控制台认证。 */
    private final AppConfig appConfig;

    /** 生成不可预测 Dashboard 短会话票据的安全随机源。 */
    private final SecureRandom secureRandom;

    /** 提供可测试的当前毫秒时间。 */
    private final LongSupplier currentTimeMillis;

    /** 保护短会话清理、签发、验证和撤销的进程内锁。 */
    private final Object sessionLock = new Object();

    /** 按最近访问顺序保存票据摘要到短会话记录的有界映射。 */
    private final LinkedHashMap<String, DashboardSession> sessions =
            new LinkedHashMap<String, DashboardSession>(16, 0.75f, true);

    /** 保存revealTimestamps集合，维持调用顺序或去重语义。 */
    private final List<Long> revealTimestamps = new ArrayList<Long>();

    /**
     * 创建控制台认证服务实例，并注入运行所需依赖。
     *
     * @param appConfig 应用运行配置。
     */
    public DashboardAuthService(AppConfig appConfig) {
        this(appConfig, new SecureRandom(), System::currentTimeMillis);
    }

    /**
     * 创建可注入时间与随机源的控制台认证服务，供同包测试验证会话边界。
     *
     * @param appConfig 应用运行配置。
     * @param secureRandom 安全随机源。
     * @param currentTimeMillis 当前毫秒时间提供器。
     */
    DashboardAuthService(
            AppConfig appConfig, SecureRandom secureRandom, LongSupplier currentTimeMillis) {
        this.appConfig = appConfig;
        this.secureRandom = secureRandom == null ? new SecureRandom() : secureRandom;
        this.currentTimeMillis =
                currentTimeMillis == null ? System::currentTimeMillis : currentTimeMillis;
        warnWeakDefaultTokenIfNeeded();
    }

    /**
     * 判断是否公开Api路径。
     *
     * @param path 文件或目录路径。
     * @return 如果公开Api路径满足条件则返回 true，否则返回 false。
     */
    public boolean isPublicApiPath(String path) {
        return PUBLIC_API_PATHS.contains(path);
    }

    /**
     * 判断是否公开Api路径。
     *
     * @param path 文件或目录路径。
     * @param method method 参数。
     * @return 如果公开Api路径满足条件则返回 true，否则返回 false。
     */
    public boolean isPublicApiPath(String path, String method) {
        return isPublicApiPath(path);
    }

    /**
     * 执行会话token相关逻辑。
     *
     * @return 返回会话token结果。
     */
    public String sessionToken() {
        return accessToken();
    }

    /**
     * 判断是否已授权。
     *
     * @param context 当前请求或运行上下文。
     * @return 如果已授权满足条件则返回 true，否则返回 false。
     */
    public boolean isAuthorized(Context context) {
        return authenticationMethod(context) != AuthenticationMethod.NONE;
    }

    /**
     * 判断 Dashboard 请求使用的认证方式。
     *
     * <p>只要请求显式携带 Authorization 头，就只校验 Bearer，不会在错误 Bearer 后回退 Cookie。
     *
     * @param context 当前请求或运行上下文。
     * @return Bearer、短会话或未认证。
     */
    public AuthenticationMethod authenticationMethod(Context context) {
        if (context == null) {
            return AuthenticationMethod.NONE;
        }
        String auth = context.header("Authorization");
        if (auth != null) {
            return matchesBearerToken(auth, accessToken())
                    ? AuthenticationMethod.BEARER
                    : AuthenticationMethod.NONE;
        }
        return validateSessionCookie(context)
                ? AuthenticationMethod.SESSION
                : AuthenticationMethod.NONE;
    }

    /**
     * 使用有效长期 Bearer 签发短会话并写入 HttpOnly Cookie。
     *
     * @param context 当前请求上下文。
     * @return 仅当长期 Bearer 有效且 Cookie 已写入时返回 true。
     */
    public boolean issueBrowserSession(Context context) {
        AccessTokenState accessTokenState = accessTokenState();
        if (context == null
                || !matchesBearerToken(
                        context.header("Authorization"), accessTokenState.accessToken)) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        byte[] ticketBytes = new byte[SESSION_TICKET_BYTES];
        secureRandom.nextBytes(ticketBytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(ticketBytes);
        String ticketDigest = ticketDigest(ticket);
        synchronized (sessionLock) {
            cleanExpiredSessions(now, accessTokenState);
            while (sessions.size() >= MAX_SESSION_COUNT) {
                Iterator<Map.Entry<String, DashboardSession>> iterator =
                        sessions.entrySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            sessions.put(
                    ticketDigest,
                    new DashboardSession(
                            accessTokenState.accessTokenDigest,
                            accessTokenState.revision,
                            now,
                            now + SESSION_ABSOLUTE_TIMEOUT_MILLIS));
        }
        writeSessionCookie(context, ticket, SESSION_COOKIE_MAX_AGE_SECONDS);
        return true;
    }

    /**
     * 撤销当前 Cookie 对应的 Dashboard 短会话，并清除浏览器 Cookie。
     *
     * @param context 当前请求上下文。
     */
    public void revokeBrowserSession(Context context) {
        if (context == null) {
            return;
        }
        String ticket = context.cookie(SESSION_COOKIE_NAME);
        if (StrUtil.isNotBlank(ticket)) {
            synchronized (sessionLock) {
                sessions.remove(ticketDigest(ticket));
            }
        }
        writeSessionCookie(context, "", 0);
    }

    /**
     * 判断是否可以Reveal token。
     *
     * @param context 当前请求或运行上下文。
     * @return 如果Reveal token满足条件则返回 true，否则返回 false。
     */
    public boolean canRevealToken(Context context) {
        return isAuthorized(context);
    }

    /**
     * 返回原始页面内容，避免把 Dashboard 访问令牌注入到浏览器全局变量。
     *
     * @param html Dashboard 前端页面内容。
     * @return 返回未携带访问令牌的页面内容。
     */
    public String injectToken(String html) {
        return html;
    }

    /**
     * 写入未授权。
     *
     * @param context 当前请求或运行上下文。
     */
    public void writeUnauthorized(Context context) {
        context.status(401);
        context.contentType("application/json;charset=UTF-8");
        context.output(ONode.serialize(Collections.singletonMap("detail", "Unauthorized")));
    }

    /**
     * 应用Cors。
     *
     * @param context 当前请求或运行上下文。
     */
    public void applyCors(Context context) {
        String origin = context.header("Origin");
        if (StrUtil.isBlank(origin)) {
            return;
        }

        if (!isAllowedDashboardOrigin(context, origin)) {
            return;
        }

        context.headerSet("Access-Control-Allow-Origin", origin);
        context.headerSet("Vary", "Origin");
        context.headerSet("Access-Control-Allow-Headers", "Authorization, Content-Type");
        context.headerSet("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
    }

    /**
     * 判断是否允许Reveal。
     *
     * @return 如果Reveal满足条件则返回 true，否则返回 false。
     */
    public boolean allowReveal() {
        synchronized (revealTimestamps) {
            long now = System.currentTimeMillis();
            long windowStart = now - 30_000L;
            for (int i = revealTimestamps.size() - 1; i >= 0; i--) {
                if (revealTimestamps.get(i) < windowStart) {
                    revealTimestamps.remove(i);
                }
            }
            if (revealTimestamps.size() >= 5) {
                return false;
            }
            revealTimestamps.add(now);
            return true;
        }
    }

    /**
     * 判断 Dashboard 是否配置了默认弱令牌。
     *
     * @return 若令牌仍为 admin 则返回 true，调用方应展示明显安全告警。
     */
    public boolean hasWeakDefaultToken() {
        return DEFAULT_WEAK_TOKEN.equals(accessToken());
    }

    /** 对显式配置的 Dashboard 默认弱口令输出一次明显告警。 */
    private void warnWeakDefaultTokenIfNeeded() {
        if (hasWeakDefaultToken() && WEAK_TOKEN_WARNING_LOGGED.compareAndSet(false, true)) {
            log.warn(
                    "Dashboard access token is the default weak value 'admin'; set solonclaw.dashboard.accessToken to a high-entropy secret before remote exposure.");
        }
    }

    /**
     * 判断浏览器请求 Origin 是否允许访问当前 Dashboard 请求入口。
     *
     * @param context 当前请求上下文，用于识别实际访问 Host 或反向代理 Host。
     * @param origin 浏览器提交的 Origin 头。
     * @return 仅当 scheme、host、port 与当前请求入口严格同源时返回 true。
     */
    public boolean isAllowedDashboardOrigin(Context context, String origin) {
        return isSameRequestOrigin(context, origin);
    }

    /**
     * 判断 Origin 是否与当前请求入口同源，支持直连 Host 与常见反向代理 Host 头。
     *
     * @param context 当前请求上下文。
     * @param origin 浏览器提交的 Origin 头。
     * @return 如果 scheme、host、port 都匹配当前请求入口则返回 true。
     */
    private boolean isSameRequestOrigin(Context context, String origin) {
        if (context == null || StrUtil.isBlank(origin)) {
            return false;
        }
        try {
            URI originUri = URI.create(origin);
            String originScheme = originUri.getScheme();
            if (!("http".equalsIgnoreCase(originScheme) || "https".equalsIgnoreCase(originScheme))
                    || StrUtil.isBlank(originUri.getHost())
                    || StrUtil.isNotBlank(originUri.getUserInfo())
                    || StrUtil.isNotBlank(originUri.getQuery())
                    || StrUtil.isNotBlank(originUri.getFragment())
                    || (StrUtil.isNotBlank(originUri.getPath())
                            && !"/".equals(originUri.getPath()))) {
                return false;
            }
            String requestHost = firstHeaderValue(context.header("X-Forwarded-Host"));
            if (StrUtil.isBlank(requestHost)) {
                requestHost = firstHeaderValue(context.header("Host"));
            }
            if (StrUtil.isBlank(requestHost)) {
                return false;
            }
            String requestScheme =
                    StrUtil.blankToDefault(
                            firstHeaderValue(context.header("X-Forwarded-Proto")),
                            context.isSecure() ? "https" : "http");
            URI requestUri = URI.create(requestScheme + "://" + requestHost.trim());
            return originUri.getHost().equalsIgnoreCase(requestUri.getHost())
                    && originScheme.equalsIgnoreCase(requestScheme)
                    && normalizeOriginPort(originUri) == normalizeOriginPort(requestUri);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 提取代理头中的第一个值，避免多级代理追加列表影响同源判断。
     *
     * @param value 原始请求头。
     * @return 去掉逗号后续内容的首个头值。
     */
    private String firstHeaderValue(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        return StrUtil.subBefore(value, ",", false).trim();
    }

    /**
     * 规范化Origin Port。
     *
     * @param uri 待校验或访问的地址参数。
     * @return 返回Origin Port结果。
     */
    private int normalizeOriginPort(URI uri) {
        int port = uri.getPort();
        if (port > 0) {
            return port;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return -1;
    }

    /**
     * 执行access token相关逻辑。
     *
     * @return 返回access token结果。
     */
    private String accessToken() {
        return accessTokenState().accessToken;
    }

    /** 匹配 Dashboard Bearer 认证头，协议名按 HTTP 规范忽略大小写，令牌值保持精确匹配。 */
    private boolean matchesBearerToken(String auth, String token) {
        if (StrUtil.isBlank(auth) || StrUtil.isBlank(token)) {
            return false;
        }
        int splitIndex = auth.indexOf(' ');
        if (splitIndex < 0) {
            return false;
        }
        String scheme = auth.substring(0, splitIndex).trim();
        String actualToken = auth.substring(splitIndex + 1).trim();
        return "Bearer".equalsIgnoreCase(scheme) && SecureTokenCompare.matches(token, actualToken);
    }

    /**
     * 校验并刷新当前 Dashboard 短会话的空闲时间。
     *
     * @param context 当前请求上下文。
     * @return 票据存在、未过期且仍绑定当前长期令牌时返回 true。
     */
    private boolean validateSessionCookie(Context context) {
        String ticket = context.cookie(SESSION_COOKIE_NAME);
        if (StrUtil.isBlank(ticket)) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        AccessTokenState accessTokenState = accessTokenState();
        String ticketDigest = ticketDigest(ticket);
        synchronized (sessionLock) {
            cleanExpiredSessions(now, accessTokenState);
            DashboardSession session = sessions.get(ticketDigest);
            if (session == null
                    || session.absoluteExpiresAt <= now
                    || session.lastSeenAt + SESSION_IDLE_TIMEOUT_MILLIS <= now
                    || session.accessTokenRevision != accessTokenState.revision
                    || !MessageDigest.isEqual(
                            session.accessTokenDigest, accessTokenState.accessTokenDigest)) {
                sessions.remove(ticketDigest);
                return false;
            }
            session.lastSeenAt = now;
            return true;
        }
    }

    /**
     * 删除过期或不再绑定当前长期令牌的短会话。
     *
     * @param now 当前毫秒时间。
     * @param accessTokenState 当前长期令牌及其单调变更代际。
     */
    private void cleanExpiredSessions(long now, AccessTokenState accessTokenState) {
        Iterator<Map.Entry<String, DashboardSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            DashboardSession session = iterator.next().getValue();
            if (session.absoluteExpiresAt <= now
                    || session.lastSeenAt + SESSION_IDLE_TIMEOUT_MILLIS <= now
                    || session.accessTokenRevision != accessTokenState.revision
                    || !MessageDigest.isEqual(
                            session.accessTokenDigest, accessTokenState.accessTokenDigest)) {
                iterator.remove();
            }
        }
    }

    /**
     * 写入带严格浏览器属性的 Dashboard 短会话 Cookie。
     *
     * @param context 当前请求上下文。
     * @param value Cookie 票据；撤销时为空。
     * @param maxAgeSeconds Cookie 最长保留秒数。
     */
    private void writeSessionCookie(Context context, String value, int maxAgeSeconds) {
        StringBuilder header =
                new StringBuilder(SESSION_COOKIE_NAME)
                        .append('=')
                        .append(StrUtil.nullToEmpty(value))
                        .append("; Max-Age=")
                        .append(Math.max(0, maxAgeSeconds))
                        .append("; Path=")
                        .append(SESSION_COOKIE_PATH)
                        .append("; HttpOnly; SameSite=Strict");
        if (isSecureDashboardRequest(context)) {
            header.append("; Secure");
        }
        context.headerAdd("Set-Cookie", header.toString());
    }

    /**
     * 判断 Dashboard 外部请求入口是否为 HTTPS。
     *
     * @param context 当前请求上下文。
     * @return 直连 TLS 或反向代理明确声明 HTTPS 时返回 true。
     */
    private boolean isSecureDashboardRequest(Context context) {
        String forwardedProto = firstHeaderValue(context.header("X-Forwarded-Proto"));
        return "https".equalsIgnoreCase(forwardedProto) || context.isSecure();
    }

    /**
     * 计算短会话票据摘要，服务端不保留浏览器持有的原始票据。
     *
     * @param ticket 原始短会话票据。
     * @return Base64URL 编码的 SHA-256 摘要。
     */
    private String ticketDigest(String ticket) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sha256(StrUtil.nullToEmpty(ticket)));
    }

    /**
     * 原子读取当前 Dashboard 长期令牌及其单调变更代际。
     *
     * @return 用于一次认证或会话校验的稳定令牌快照。
     */
    private AccessTokenState accessTokenState() {
        AppConfig.DashboardConfig dashboard = appConfig == null ? null : appConfig.getDashboard();
        if (dashboard == null) {
            return new AccessTokenState("", 0L);
        }
        synchronized (dashboard) {
            return new AccessTokenState(
                    StrUtil.nullToEmpty(dashboard.getAccessToken()).trim(),
                    dashboard.getAccessTokenRevision());
        }
    }

    /**
     * 计算 UTF-8 文本的 SHA-256 摘要。
     *
     * @param value 待摘要文本。
     * @return 32 字节 SHA-256 摘要。
     */
    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 一次认证校验使用的 Dashboard 长期令牌稳定快照。 */
    private final class AccessTokenState {
        /** 当前长期 Dashboard 令牌。 */
        private final String accessToken;

        /** 当前长期 Dashboard 令牌摘要。 */
        private final byte[] accessTokenDigest;

        /** 当前长期 Dashboard 令牌的单调变更代际。 */
        private final long revision;

        /**
         * 创建 Dashboard 长期令牌快照。
         *
         * @param accessToken 当前长期令牌。
         * @param revision 当前令牌变更代际。
         */
        private AccessTokenState(String accessToken, long revision) {
            this.accessToken = StrUtil.nullToEmpty(accessToken);
            this.accessTokenDigest = sha256(this.accessToken);
            this.revision = revision;
        }
    }

    /** Dashboard API 请求实际使用的认证方式。 */
    public enum AuthenticationMethod {
        /** 请求没有通过认证。 */
        NONE,

        /** 请求使用长期 Bearer 令牌认证。 */
        BEARER,

        /** 请求使用 HttpOnly 短会话认证。 */
        SESSION
    }

    /** 服务端保存的 Dashboard 短会话记录，不包含原始票据或长期令牌。 */
    private static final class DashboardSession {
        /** 签发时长期 Dashboard 令牌的 SHA-256 摘要。 */
        private final byte[] accessTokenDigest;

        /** 签发时长期 Dashboard 令牌的单调变更代际。 */
        private final long accessTokenRevision;

        /** 短会话绝对失效时间。 */
        private final long absoluteExpiresAt;

        /** 短会话最近一次成功访问时间。 */
        private long lastSeenAt;

        /**
         * 创建 Dashboard 短会话记录。
         *
         * @param accessTokenDigest 签发时长期令牌摘要。
         * @param accessTokenRevision 签发时长期令牌变更代际。
         * @param lastSeenAt 最近访问时间。
         * @param absoluteExpiresAt 绝对失效时间。
         */
        private DashboardSession(
                byte[] accessTokenDigest,
                long accessTokenRevision,
                long lastSeenAt,
                long absoluteExpiresAt) {
            this.accessTokenDigest =
                    accessTokenDigest == null
                            ? new byte[0]
                            : Arrays.copyOf(accessTokenDigest, accessTokenDigest.length);
            this.accessTokenRevision = accessTokenRevision;
            this.lastSeenAt = lastSeenAt;
            this.absoluteExpiresAt = absoluteExpiresAt;
        }
    }
}
