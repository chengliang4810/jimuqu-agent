package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.SensitiveConfigAuditEvent;
import com.jimuqu.solon.claw.core.repository.SensitiveConfigAuditRepository;
import java.util.Locale;
import java.util.UUID;
import org.noear.solon.core.handle.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 记录 Dashboard 敏感配置写入与明文读取的安全审计事件。 */
public final class DashboardSensitiveConfigAuditService {
    /** 敏感配置写入尝试的审计操作类型。 */
    public static final String OPERATION_SECRET_SET_ATTEMPT = "SECRET_SET_ATTEMPT";

    /** 敏感配置明文读取的审计操作类型。 */
    public static final String OPERATION_SECRET_REVEAL = "SECRET_REVEAL";

    /** 已认证 Dashboard 请求的安全主体类型。 */
    public static final String ACTOR_DASHBOARD = "DASHBOARD";

    /** 本机首次初始化 Dashboard 令牌的安全主体类型。 */
    public static final String ACTOR_LOCAL_BOOTSTRAP = "LOCAL_BOOTSTRAP";

    /** 本机首次初始化使用的认证方式。 */
    public static final String AUTH_METHOD_LOCAL = "LOCAL";

    /** 记录审计存储故障的固定元数据，不记录异常消息或敏感请求内容。 */
    private static final Logger log =
            LoggerFactory.getLogger(DashboardSensitiveConfigAuditService.class);

    /** 追加敏感配置操作的审计仓储。 */
    private final SensitiveConfigAuditRepository repository;

    /** 识别 Dashboard 请求实际使用的认证方式。 */
    private final DashboardAuthService authService;

    /**
     * 创建 Dashboard 敏感配置审计服务。
     *
     * @param repository 追加式敏感配置审计仓储。
     * @param authService Dashboard 认证服务。
     */
    public DashboardSensitiveConfigAuditService(
            SensitiveConfigAuditRepository repository, DashboardAuthService authService) {
        if (repository == null || authService == null) {
            throw new IllegalArgumentException(
                    "Sensitive configuration audit dependencies are required.");
        }
        this.repository = repository;
        this.authService = authService;
    }

    /**
     * 在修改敏感配置前记录已认证 Dashboard 写入尝试。
     *
     * @param context 当前 HTTP 请求。
     * @param configKey 敏感配置键。
     * @param profile 已解析的实际 Profile 名。
     * @return 可用于响应对账的审计事件标识。
     */
    public String recordSecretSetAttempt(Context context, String configKey, String profile) {
        return recordAuthenticated(context, OPERATION_SECRET_SET_ATTEMPT, configKey, profile);
    }

    /**
     * 在返回敏感配置明文前记录已认证 Dashboard 读取事件。
     *
     * @param context 当前 HTTP 请求。
     * @param configKey 敏感配置键。
     * @param profile 已解析的实际 Profile 名。
     * @return 可用于响应对账的审计事件标识。
     */
    public String recordSecretReveal(Context context, String configKey, String profile) {
        return recordAuthenticated(context, OPERATION_SECRET_REVEAL, configKey, profile);
    }

    /**
     * 在本机首次写入 Dashboard 令牌前记录初始化尝试。
     *
     * @param context 当前 HTTP 请求。
     * @param configKey Dashboard 令牌配置键。
     * @param profile 已解析的实际 Profile 名。
     * @return 可用于响应对账的审计事件标识。
     */
    public String recordLocalBootstrap(Context context, String configKey, String profile) {
        return append(
                context,
                OPERATION_SECRET_SET_ATTEMPT,
                ACTOR_LOCAL_BOOTSTRAP,
                AUTH_METHOD_LOCAL,
                configKey,
                profile);
    }

    /**
     * 记录经过 Dashboard Bearer 或短会话认证的敏感配置操作。
     *
     * @param context 当前 HTTP 请求。
     * @param operation 审计操作类型。
     * @param configKey 敏感配置键。
     * @param profile 已解析的实际 Profile 名。
     * @return 审计事件标识。
     */
    private String recordAuthenticated(
            Context context, String operation, String configKey, String profile) {
        DashboardAuthService.AuthenticationMethod authenticationMethod =
                authService.authenticationMethod(context);
        if (authenticationMethod == DashboardAuthService.AuthenticationMethod.NONE) {
            throw unavailable(UUID.randomUUID().toString(), operation, null);
        }
        return append(
                context,
                operation,
                ACTOR_DASHBOARD,
                authenticationMethod.name(),
                configKey,
                profile);
    }

    /**
     * 构造并同步追加不包含配置值、请求体或认证凭据的审计事件。
     *
     * @param context 当前 HTTP 请求。
     * @param operation 审计操作类型。
     * @param actorType 安全主体类型。
     * @param authMethod 认证方式。
     * @param configKey 敏感配置键。
     * @param profile 已解析的实际 Profile 名。
     * @return 审计事件标识。
     */
    private String append(
            Context context,
            String operation,
            String actorType,
            String authMethod,
            String configKey,
            String profile) {
        String eventId = UUID.randomUUID().toString();
        SensitiveConfigAuditEvent event = new SensitiveConfigAuditEvent();
        event.setEventId(eventId);
        event.setOperation(operation);
        event.setActorType(actorType);
        event.setAuthMethod(authMethod);
        event.setProfile(normalizeProfile(profile));
        event.setConfigKey(StrUtil.nullToEmpty(configKey).trim());
        event.setRemoteIp(context == null ? "" : StrUtil.nullToEmpty(context.remoteIp()).trim());
        event.setCreatedAt(System.currentTimeMillis());
        try {
            repository.append(event);
            return eventId;
        } catch (Exception error) {
            throw unavailable(eventId, operation, error);
        }
    }

    /**
     * 规范化审计 Profile 名，空值和 current 均归入当前默认 Profile。
     *
     * @param profile 已解析或请求指定的 Profile 名。
     * @return 适合审计对账的小写 Profile 名。
     */
    private String normalizeProfile(String profile) {
        String normalized = StrUtil.nullToEmpty(profile).trim().toLowerCase(Locale.ROOT);
        return normalized.length() == 0 || "current".equals(normalized) ? "default" : normalized;
    }

    /**
     * 记录固定故障摘要并构造不携带原始异常消息的审计不可用异常。
     *
     * @param eventId 审计事件标识。
     * @param operation 审计操作类型。
     * @param cause 审计存储异常。
     * @return 审计不可用异常。
     */
    private AuditUnavailableException unavailable(
            String eventId, String operation, Throwable cause) {
        log.error(
                "Sensitive configuration audit unavailable: requestId={}, operation={}, error={}",
                eventId,
                operation,
                cause == null ? "authentication" : cause.getClass().getName());
        return new AuditUnavailableException(eventId, cause);
    }

    /** 表示敏感配置操作因审计不可用而必须失败关闭。 */
    public static final class AuditUnavailableException extends RuntimeException {
        /** 对应失败审计尝试的事件标识。 */
        private final String requestId;

        /**
         * 创建审计不可用异常。
         *
         * @param requestId 审计事件标识。
         * @param cause 底层审计存储异常。
         */
        private AuditUnavailableException(String requestId, Throwable cause) {
            super("Sensitive configuration audit unavailable.", cause);
            this.requestId = requestId;
        }

        /**
         * @return 用于响应和日志对账的事件标识。
         */
        public String getRequestId() {
            return requestId;
        }
    }
}
