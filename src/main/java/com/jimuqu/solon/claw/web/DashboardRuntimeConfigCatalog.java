package com.jimuqu.solon.claw.web;

import com.jimuqu.solon.claw.support.ModelConfigKeySupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 保存 Dashboard 支持的工作区配置键及其展示元数据。 */
final class DashboardRuntimeConfigCatalog {
    /** 按 Dashboard 固定展示顺序保存的配置定义。 */
    private final List<ConfigItemDefinition> definitions;

    /** 按 Dashboard 固定展示顺序保存的配置键。 */
    private final List<String> keys;

    /** 创建不可变的工作区配置目录。 */
    DashboardRuntimeConfigCatalog() {
        this.definitions =
                Collections.unmodifiableList(
                        Arrays.asList(
                                item(
                                        "solonclaw.react.maxSteps",
                                        "主代理最大推理步数",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.retryMax",
                                        "主代理决策重试次数",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.retryDelayMs",
                                        "主代理决策重试延迟（毫秒）",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.delegateMaxSteps",
                                        "子代理最大推理步数",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.delegateRetryMax",
                                        "子代理决策重试次数",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.delegateRetryDelayMs",
                                        "子代理决策重试延迟（毫秒）",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.summarizationEnabled",
                                        "启用 ReAct 工作记忆摘要守卫",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.summarizationMaxMessages",
                                        "ReAct 摘要触发消息阈值",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.react.summarizationMaxTokens",
                                        "ReAct 摘要触发 token 阈值",
                                        "provider",
                                        false,
                                        true,
                                        "llm"),
                                item(
                                        "solonclaw.scheduler.wrapResponse",
                                        "默认包装 Cron 投递回复",
                                        "runtime",
                                        false,
                                        false,
                                        "cron"),
                                item(
                                        "solonclaw.task.busyPolicy",
                                        "运行中输入策略：queue / steer / interrupt / reject",
                                        "runtime",
                                        false,
                                        false,
                                        "agent"),
                                item(
                                        "solonclaw.task.toolOutputInlineLimit",
                                        "工具输出内联字节上限",
                                        "runtime",
                                        false,
                                        true,
                                        "agent"),
                                item(
                                        "solonclaw.task.toolOutputTurnBudget",
                                        "单轮工具输出累计预算字节",
                                        "runtime",
                                        false,
                                        true,
                                        "agent"),
                                item(
                                        "solonclaw.task.bootstrapPromptFileCharLimit",
                                        "静态上下文单文件字符上限",
                                        "runtime",
                                        false,
                                        true,
                                        "agent"),
                                item(
                                        "solonclaw.task.bootstrapPromptTotalCharBudget",
                                        "静态 bootstrap 提示词总字符预算",
                                        "runtime",
                                        false,
                                        true,
                                        "agent"),
                                item(
                                        "solonclaw.task.toolOutputMaxLines",
                                        "工具文件读取最大行数",
                                        "runtime",
                                        false,
                                        true,
                                        "agent"),
                                item(
                                        "solonclaw.task.toolOutputMaxLineLength",
                                        "工具输出单行最大长度",
                                        "runtime",
                                        false,
                                        true,
                                        "agent"),
                                item(
                                        "solonclaw.channels.feishu.enabled",
                                        "启用飞书渠道",
                                        "messaging",
                                        false,
                                        false,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.appId",
                                        "飞书应用 ID",
                                        "messaging",
                                        false,
                                        false,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.appSecret",
                                        "飞书应用密钥",
                                        "messaging",
                                        true,
                                        false,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.domain",
                                        "飞书/Lark 租户域",
                                        "messaging",
                                        false,
                                        false,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.groupAllowedUsers",
                                        "飞书群聊 allowlist",
                                        "messaging",
                                        false,
                                        true,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.requireMention",
                                        "飞书群聊是否必须提及机器人",
                                        "messaging",
                                        false,
                                        false,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.freeResponseChats",
                                        "飞书免提及响应群聊列表",
                                        "messaging",
                                        false,
                                        true,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.botOpenId",
                                        "飞书 bot Open ID",
                                        "messaging",
                                        false,
                                        true,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.feishu.botUserId",
                                        "飞书 bot User ID",
                                        "messaging",
                                        false,
                                        true,
                                        "feishu"),
                                item(
                                        "solonclaw.channels.dingtalk.enabled",
                                        "启用钉钉渠道",
                                        "messaging",
                                        false,
                                        false,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.dingtalk.clientId",
                                        "钉钉客户端 ID",
                                        "messaging",
                                        false,
                                        false,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.dingtalk.clientSecret",
                                        "钉钉客户端密钥",
                                        "messaging",
                                        true,
                                        false,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.dingtalk.robotCode",
                                        "钉钉机器人编码",
                                        "messaging",
                                        true,
                                        false,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.dingtalk.groupAllowedUsers",
                                        "钉钉群聊 allowlist",
                                        "messaging",
                                        false,
                                        true,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.dingtalk.requireMention",
                                        "钉钉群聊是否必须提及机器人",
                                        "messaging",
                                        false,
                                        false,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.dingtalk.freeResponseChats",
                                        "钉钉免提及响应群聊列表",
                                        "messaging",
                                        false,
                                        true,
                                        "dingtalk"),
                                item(
                                        "solonclaw.channels.wecom.enabled",
                                        "启用企微渠道",
                                        "messaging",
                                        false,
                                        false,
                                        "wecom"),
                                item(
                                        "solonclaw.channels.wecom.botId",
                                        "企微机器人 ID",
                                        "messaging",
                                        false,
                                        false,
                                        "wecom"),
                                item(
                                        "solonclaw.channels.wecom.secret",
                                        "企微机器人密钥",
                                        "messaging",
                                        true,
                                        false,
                                        "wecom"),
                                item(
                                        "solonclaw.channels.wecom.groupAllowedUsers",
                                        "企微群聊 allowlist",
                                        "messaging",
                                        false,
                                        true,
                                        "wecom"),
                                item(
                                        "solonclaw.channels.weixin.enabled",
                                        "启用微信渠道",
                                        "messaging",
                                        false,
                                        false,
                                        "weixin"),
                                item(
                                        "solonclaw.channels.weixin.token",
                                        "微信令牌",
                                        "messaging",
                                        true,
                                        false,
                                        "weixin"),
                                item(
                                        "solonclaw.channels.weixin.accountId",
                                        "微信 iLink accountId",
                                        "messaging",
                                        false,
                                        false,
                                        "weixin"),
                                item(
                                        "solonclaw.channels.weixin.groupAllowedUsers",
                                        "微信群聊 allowlist",
                                        "messaging",
                                        false,
                                        true,
                                        "weixin"),
                                item(
                                        "solonclaw.channels.qqbot.enabled",
                                        "启用 QQBot 渠道",
                                        "messaging",
                                        false,
                                        false,
                                        "qqbot"),
                                item(
                                        "solonclaw.channels.qqbot.appId",
                                        "QQBot 应用 ID",
                                        "messaging",
                                        false,
                                        false,
                                        "qqbot"),
                                item(
                                        "solonclaw.channels.qqbot.clientSecret",
                                        "QQBot 客户端密钥",
                                        "messaging",
                                        true,
                                        false,
                                        "qqbot"),
                                item(
                                        "solonclaw.channels.yuanbao.enabled",
                                        "启用腾讯元宝渠道",
                                        "messaging",
                                        false,
                                        false,
                                        "yuanbao"),
                                item(
                                        "solonclaw.channels.yuanbao.appId",
                                        "腾讯元宝应用 ID",
                                        "messaging",
                                        false,
                                        false,
                                        "yuanbao"),
                                item(
                                        "solonclaw.channels.yuanbao.appSecret",
                                        "腾讯元宝应用密钥",
                                        "messaging",
                                        true,
                                        false,
                                        "yuanbao"),
                                item(
                                        "solonclaw.gateway.injectionSecret",
                                        "HTTP gateway injection HMAC secret",
                                        "security",
                                        true,
                                        true,
                                        "gateway"),
                                item(
                                        "solonclaw.gateway.injectionMaxBodyBytes",
                                        "HTTP gateway injection max body bytes",
                                        "security",
                                        false,
                                        true,
                                        "gateway"),
                                item(
                                        "solonclaw.gateway.injectionReplayWindowSeconds",
                                        "HTTP gateway injection replay window seconds",
                                        "security",
                                        false,
                                        true,
                                        "gateway"),
                                item(
                                        "solonclaw.dashboard.accessToken",
                                        "Dashboard access token",
                                        "dashboard",
                                        true,
                                        false,
                                        "dashboard"),
                                item(
                                        "solonclaw.update.repo",
                                        "版本检查使用的 GitHub 仓库，格式 owner/repo",
                                        "runtime",
                                        false,
                                        true,
                                        "version"),
                                item(
                                        "solonclaw.update.releaseApiUrl",
                                        "自定义最新版本检查 API 地址，默认 GitHub releases/latest",
                                        "runtime",
                                        false,
                                        true,
                                        "version"),
                                item(
                                        "solonclaw.update.tagsApiUrl",
                                        "自定义 tags 检查 API 地址",
                                        "runtime",
                                        false,
                                        true,
                                        "version"),
                                item(
                                        "solonclaw.update.httpProxy",
                                        "版本检查 HTTP 代理地址，例如 http://proxy.example:7890",
                                        "runtime",
                                        false,
                                        true,
                                        "version"),
                                item(
                                        "solonclaw.integrations.github.token",
                                        "Skills Hub 使用的 GitHub 访问令牌",
                                        "tool",
                                        true,
                                        true,
                                        "skills_hub"),
                                item(
                                        "solonclaw.integrations.github.cliToken",
                                        "GitHub CLI 回退令牌",
                                        "tool",
                                        true,
                                        true,
                                        "skills_hub"),
                                item(
                                        "solonclaw.integrations.github.appId",
                                        "GitHub App ID",
                                        "tool",
                                        false,
                                        true,
                                        "skills_hub"),
                                item(
                                        "solonclaw.integrations.github.privateKeyPath",
                                        "GitHub App 私钥路径",
                                        "tool",
                                        false,
                                        true,
                                        "skills_hub"),
                                item(
                                        "solonclaw.integrations.github.installationId",
                                        "GitHub App 安装 ID",
                                        "tool",
                                        false,
                                        true,
                                        "skills_hub")));
        List<String> orderedKeys = new ArrayList<String>(definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            orderedKeys.add(definition.key);
        }
        this.keys = Collections.unmodifiableList(orderedKeys);
    }

    /**
     * 返回按 Dashboard 展示顺序排列的配置定义。
     *
     * @return 不可变配置定义列表。
     */
    List<ConfigItemDefinition> definitions() {
        return definitions;
    }

    /**
     * 返回按 Dashboard 展示顺序排列的配置键。
     *
     * @return 不可变配置键列表。
     */
    List<String> keys() {
        return keys;
    }

    /**
     * 返回受支持配置键的定义。
     *
     * @param key 配置键。
     * @return 对应配置定义。
     */
    ConfigItemDefinition require(String key) {
        for (ConfigItemDefinition definition : definitions) {
            if (definition.key.equals(key)) {
                return definition;
            }
        }
        if (ModelConfigKeySupport.isDedicatedKey(key)) {
            throw new IllegalArgumentException(ModelConfigKeySupport.DEDICATED_ENTRY_MESSAGE);
        }
        throw new IllegalStateException("Unsupported workspace config item: " + key);
    }

    /**
     * 创建单工具关联的配置定义。
     *
     * @param key 配置键。
     * @param description 展示说明。
     * @param category 配置分类。
     * @param password 是否为密钥。
     * @param advanced 是否为高级配置。
     * @param tool 关联工具。
     * @return 配置定义。
     */
    private static ConfigItemDefinition item(
            String key,
            String description,
            String category,
            boolean password,
            boolean advanced,
            String tool) {
        return new ConfigItemDefinition(
                key, description, category, password, advanced, null, Arrays.asList(tool));
    }

    /** 承载单个工作区配置项的展示与安全元数据。 */
    static final class ConfigItemDefinition {
        /** 配置键。 */
        final String key;

        /** 展示说明。 */
        final String description;

        /** 配置分类。 */
        final String category;

        /** 是否为密钥。 */
        final boolean password;

        /** 是否为高级配置。 */
        final boolean advanced;

        /** 相关说明地址；当前目录项未配置时为 null。 */
        final String url;

        /** 关联工具列表。 */
        final List<String> tools;

        /**
         * 创建配置定义。
         *
         * @param key 配置键。
         * @param description 展示说明。
         * @param category 配置分类。
         * @param password 是否为密钥。
         * @param advanced 是否为高级配置。
         * @param url 相关说明地址。
         * @param tools 关联工具列表。
         */
        private ConfigItemDefinition(
                String key,
                String description,
                String category,
                boolean password,
                boolean advanced,
                String url,
                List<String> tools) {
            this.key = key;
            this.description = description;
            this.category = category;
            this.password = password;
            this.advanced = advanced;
            this.url = url;
            this.tools = tools;
        }
    }
}
