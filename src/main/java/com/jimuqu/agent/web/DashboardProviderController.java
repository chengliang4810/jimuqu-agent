package com.jimuqu.agent.web;

import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard provider 管理接口。
 */
@Controller
public class DashboardProviderController {
    private final DashboardProviderService providerService;

    public DashboardProviderController(DashboardProviderService providerService) {
        this.providerService = providerService;
    }

    @Mapping(value = "/api/providers", method = MethodType.GET)
    public Map<String, Object> providers() {
        return providerService.listProviders();
    }

    @Mapping(value = "/api/providers", method = MethodType.POST)
    public Map<String, Object> create(Context context) throws Exception {
        return providerService.createProvider(ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class));
    }

    @Mapping(value = "/api/providers/{providerKey}", method = MethodType.PUT)
    public Map<String, Object> update(String providerKey, Context context) throws Exception {
        return providerService.updateProvider(providerKey, ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class));
    }

    @Mapping(value = "/api/providers/{providerKey}", method = MethodType.DELETE)
    public Map<String, Object> delete(String providerKey) {
        return providerService.deleteProvider(providerKey);
    }

    @Mapping(value = "/api/model/default", method = MethodType.PUT)
    public Map<String, Object> updateDefault(Context context) throws Exception {
        Map<String, Object> body = ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class);
        return providerService.updateDefaultModel(
                body.get("providerKey") == null ? "" : String.valueOf(body.get("providerKey")),
                body.get("model") == null ? "" : String.valueOf(body.get("model"))
        );
    }

    @Mapping(value = "/api/model/fallbacks", method = MethodType.PUT)
    public Map<String, Object> updateFallbacks(Context context) throws Exception {
        Map<String, Object> body = ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class);
        Object items = body.get("fallbackProviders");
        return providerService.updateFallbackProviders(items instanceof List ? (List<Map<String, Object>>) items : null);
    }
}
