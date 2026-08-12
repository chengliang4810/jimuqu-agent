package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ToolResultEnvelope;
import com.jimuqu.solon.claw.profile.ProfileCredentialService;
import com.jimuqu.solon.claw.profile.ProfileRuntimeScope;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** 提供 Profile .env 与进程环境变量双来源凭据管理工具。 */
public final class CredentialTools {
    /** 应用配置，用于默认 Profile 未安装线程作用域时定位工作区。 */
    private final AppConfig appConfig;

    /** 创建凭据管理工具。 */
    public CredentialTools(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /** 设置、删除、列出或探测凭据，任何动作都不会返回凭据值。 */
    @ToolMapping(
            name = "credential_manage",
            description =
                    "Manage credentials through the current Profile .env with process environment fallback. Actions: set, remove, list, probe. Values are never readable.")
    public String credentialManage(
            @Param(name = "action", description = "set、remove、list 或 probe") String action,
            @Param(name = "name", description = "环境变量名；list 时可省略", required = false) String name,
            @Param(name = "value", description = "仅 set 动作需要的凭据值", required = false) String value) {
        try {
            ProfileCredentialService service = new ProfileCredentialService(resolveHome());
            String normalized = StrUtil.nullToEmpty(action).trim().toLowerCase(Locale.ROOT);
            if ("set".equals(normalized)) {
                service.set(name, value);
                return ToolResultEnvelope.ok("凭据已保存到当前 Profile .env")
                        .data("name", safe(name))
                        .data("source", "profile_env")
                        .data("present", Boolean.TRUE)
                        .preview("set " + safe(name) + "=***")
                        .toJson();
            }
            if ("remove".equals(normalized)) {
                boolean removed = service.remove(name);
                return ToolResultEnvelope.ok(
                                removed ? "已从当前 Profile .env 删除凭据" : "当前 Profile .env 中没有该凭据")
                        .data("name", safe(name))
                        .data("removed", Boolean.valueOf(removed))
                        .preview("remove " + safe(name))
                        .toJson();
            }
            if ("probe".equals(normalized)) {
                ProfileCredentialService.CredentialView view = service.probe(name);
                return ToolResultEnvelope.ok("已探测凭据来源")
                        .data("name", safe(view.getName()))
                        .data("source", view.getSource())
                        .data("present", Boolean.valueOf(view.isPresent()))
                        .preview(safe(view.getName()) + ": " + view.getSource())
                        .toJson();
            }
            if ("list".equals(normalized)) {
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                for (ProfileCredentialService.CredentialView view : service.list()) {
                    Map<String, Object> item = new java.util.LinkedHashMap<String, Object>();
                    item.put("name", safe(view.getName()));
                    item.put("source", view.getSource());
                    item.put("present", Boolean.valueOf(view.isPresent()));
                    items.add(item);
                }
                return ToolResultEnvelope.ok("已列出凭据名称与来源，不包含值")
                        .data("credentials", items)
                        .data("count", Integer.valueOf(items.size()))
                        .preview("credentials: " + items.size())
                        .toJson();
            }
            throw new IllegalArgumentException(
                    "Credential action must be set, remove, list, or probe.");
        } catch (Exception e) {
            return ToolResultEnvelope.error(
                            SecretRedactor.redact(
                                    e.getMessage() == null
                                            ? e.getClass().getSimpleName()
                                            : e.getMessage(),
                                    1000))
                    .toJson();
        }
    }

    /** 定位当前 Profile 工作区。 */
    private java.nio.file.Path resolveHome() {
        ProfileRuntimeScope.Context current = ProfileRuntimeScope.current();
        if (current != null && current.getHome() != null) {
            return current.getHome();
        }
        return Paths.get(appConfig.getRuntime().getHome()).toAbsolutePath().normalize();
    }

    /** 生成不含秘密值的安全文本。 */
    private String safe(String value) {
        return SecretRedactor.redact(StrUtil.nullToEmpty(value), 200);
    }
}
