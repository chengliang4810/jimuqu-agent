package com.jimuqu.agent.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.llm.LlmProviderSupport;
import com.jimuqu.agent.support.LlmProviderService;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard provider 配置管理服务。
 */
public class DashboardProviderService {
    private final AppConfig appConfig;
    private final com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService;
    private final LlmProviderService llmProviderService;

    public DashboardProviderService(AppConfig appConfig,
                                    com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService,
                                    LlmProviderService llmProviderService) {
        this.appConfig = appConfig;
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        this.llmProviderService = llmProviderService;
    }

    public Map<String, Object> listProviders() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, AppConfig.ProviderConfig> entry : appConfig.getProviders().entrySet()) {
            items.add(toProviderMap(entry.getKey(), entry.getValue()));
        }
        result.put("providers", items);
        result.put("defaultProviderKey", appConfig.getModel().getProviderKey());
        result.put("defaultModel", appConfig.getModel().getDefault());
        result.put("fallbackProviders", cloneFallbackProviders(appConfig.getFallbackProviders()));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createProvider(Map<String, Object> data) {
        String providerKey = readString(data, "providerKey");
        ensureProviderKey(providerKey);
        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> providers = getOrCreateMap(root, "providers");
        if (providers.containsKey(providerKey)) {
            throw new IllegalArgumentException("Provider 已存在：" + providerKey);
        }
        providers.put(providerKey, toProviderNode(data, null));

        Map<String, Object> model = getOrCreateMap(root, "model");
        if (StrUtil.isBlank(readString(model, "providerKey"))) {
            model.put("providerKey", providerKey);
        }

        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateProvider(String providerKey, Map<String, Object> data) {
        ensureProviderKey(providerKey);
        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> providers = getOrCreateMap(root, "providers");
        Object existing = providers.get(providerKey);
        if (!(existing instanceof Map)) {
            throw new IllegalArgumentException("Provider 不存在：" + providerKey);
        }
        providers.put(providerKey, toProviderNode(data, (Map<String, Object>) existing));
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> deleteProvider(String providerKey) {
        ensureProviderKey(providerKey);
        if (StrUtil.equals(providerKey, appConfig.getModel().getProviderKey())) {
            throw new IllegalArgumentException("当前默认 provider 不能删除。");
        }
        for (AppConfig.FallbackProviderConfig fallback : appConfig.getFallbackProviders()) {
            if (fallback != null && StrUtil.equals(providerKey, fallback.getProvider())) {
                throw new IllegalArgumentException("该 provider 正在 fallbackProviders 中使用，不能删除。");
            }
        }

        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> providers = getOrCreateMap(root, "providers");
        providers.remove(providerKey);
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> updateDefaultModel(String providerKey, String model) {
        String nextProviderKey = StrUtil.isNotBlank(providerKey)
                ? providerKey.trim()
                : appConfig.getModel().getProviderKey();
        if (!llmProviderService.hasProvider(nextProviderKey)) {
            throw new IllegalArgumentException("未找到 provider：" + nextProviderKey);
        }

        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> modelNode = getOrCreateMap(root, "model");
        modelNode.put("providerKey", nextProviderKey);
        modelNode.put("default", StrUtil.nullToEmpty(model).trim());
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> updateFallbackProviders(List<Map<String, Object>> items) {
        List<Object> next = new ArrayList<Object>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                String provider = readString(item, "provider");
                if (!llmProviderService.hasProvider(provider)) {
                    throw new IllegalArgumentException("fallbackProviders 引用了不存在的 provider：" + provider);
                }
                Map<String, Object> node = new LinkedHashMap<String, Object>();
                node.put("provider", provider);
                String model = readString(item, "model");
                if (StrUtil.isNotBlank(model)) {
                    node.put("model", model);
                }
                next.add(node);
            }
        }

        Map<String, Object> root = loadRootForMutation();
        root.put("fallbackProviders", next);
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private Map<String, Object> toProviderMap(String providerKey, AppConfig.ProviderConfig provider) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("providerKey", providerKey);
        item.put("name", StrUtil.blankToDefault(provider.getName(), providerKey));
        item.put("baseUrl", StrUtil.nullToEmpty(provider.getBaseUrl()));
        item.put("defaultModel", StrUtil.nullToEmpty(provider.getDefaultModel()));
        item.put("dialect", StrUtil.nullToEmpty(provider.getDialect()));
        item.put("hasApiKey", StrUtil.isNotBlank(provider.getApiKey()));
        item.put("isDefault", StrUtil.equals(providerKey, appConfig.getModel().getProviderKey()));
        return item;
    }

    private Map<String, Object> toProviderNode(AppConfig.ProviderConfig provider) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", StrUtil.nullToEmpty(provider.getName()).trim());
        result.put("baseUrl", StrUtil.nullToEmpty(provider.getBaseUrl()).trim());
        result.put("apiKey", StrUtil.nullToEmpty(provider.getApiKey()).trim());
        result.put("defaultModel", StrUtil.nullToEmpty(provider.getDefaultModel()).trim());
        result.put("dialect", StrUtil.nullToEmpty(provider.getDialect()).trim());
        return result;
    }

    private List<Map<String, Object>> cloneFallbackProviders(List<AppConfig.FallbackProviderConfig> source) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (source == null) {
            return result;
        }
        for (AppConfig.FallbackProviderConfig item : source) {
            if (item == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("provider", StrUtil.nullToEmpty(item.getProvider()));
            row.put("model", StrUtil.nullToEmpty(item.getModel()));
            result.add(row);
        }
        return result;
    }

    private void ensureProviderKey(String providerKey) {
        if (StrUtil.isBlank(providerKey)) {
            throw new IllegalArgumentException("providerKey 不能为空。");
        }
    }

    private Map<String, Object> toProviderNode(Map<String, Object> source, Map<String, Object> base) {
        String name = readString(source, "name");
        String baseUrl = readString(source, "baseUrl");
        String apiKey = source.containsKey("apiKey") ? readString(source, "apiKey") : readString(base, "apiKey");
        String defaultModel = readString(source, "defaultModel");
        String dialect = LlmProviderSupport.normalizeDialect(readString(source, "dialect"));

        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalArgumentException("baseUrl 不能为空。");
        }
        if (StrUtil.isBlank(dialect) || !LlmProviderSupport.isSupportedDialect(dialect)) {
            throw new IllegalArgumentException("不支持的 dialect：" + dialect);
        }
        if (StrUtil.isBlank(defaultModel) && StrUtil.isBlank(appConfig.getModel().getDefault())) {
            throw new IllegalArgumentException("defaultModel 不能为空。");
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", StrUtil.nullToEmpty(name).trim());
        result.put("baseUrl", StrUtil.nullToEmpty(baseUrl).trim());
        result.put("apiKey", StrUtil.nullToEmpty(apiKey).trim());
        result.put("defaultModel", StrUtil.nullToEmpty(defaultModel).trim());
        result.put("dialect", dialect);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRootForMutation() {
        File configFile = new File(appConfig.getRuntime().getConfigFile());
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        if (configFile.exists()) {
            Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
            if (parsed instanceof Map) {
                root.putAll(sanitizeMap((Map<?, ?>) parsed));
            }
        }

        if (!(root.get("providers") instanceof Map)) {
            Map<String, Object> providers = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, AppConfig.ProviderConfig> entry : appConfig.getProviders().entrySet()) {
                providers.put(entry.getKey(), toProviderNode(entry.getValue()));
            }
            root.put("providers", providers);
        }
        if (!(root.get("model") instanceof Map)) {
            Map<String, Object> model = new LinkedHashMap<String, Object>();
            model.put("providerKey", appConfig.getModel().getProviderKey());
            model.put("default", appConfig.getModel().getDefault());
            root.put("model", model);
        }
        if (!(root.get("fallbackProviders") instanceof List)) {
            root.put("fallbackProviders", new ArrayList<Object>(cloneFallbackProviders(appConfig.getFallbackProviders())));
        }
        return root;
    }

    private void write(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);

        File configFile = new File(appConfig.getRuntime().getConfigFile());
        FileUtil.mkParentDirs(configFile);
        FileUtil.writeUtf8String(new Yaml(options).dump(root), configFile);
        gatewayRuntimeRefreshService.refreshConfigOnly();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Map<String, Object> root, String key) {
        Object current = root.get(key);
        if (current instanceof Map) {
            return (Map<String, Object>) current;
        }
        Map<String, Object> created = new LinkedHashMap<String, Object>();
        root.put(key, created);
        return created;
    }

    private String readString(Map<String, Object> source, String key) {
        if (source == null || key == null) {
            return "";
        }
        Object value = source.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.put(key, sanitizeMap((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.put(key, sanitizeList((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<Object> sanitizeList(List<?> raw) {
        List<Object> result = new ArrayList<Object>();
        for (Object item : raw) {
            if (item instanceof Map) {
                result.add(sanitizeMap((Map<?, ?>) item));
            } else if (item instanceof List) {
                result.add(sanitizeList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
