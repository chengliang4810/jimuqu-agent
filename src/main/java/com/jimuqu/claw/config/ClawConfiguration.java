package com.jimuqu.claw.config;

import com.jimuqu.claw.agent.job.JobExecutionService;
import com.jimuqu.claw.agent.job.JobSchedulerService;
import com.jimuqu.claw.agent.runtime.DefaultRuntimeService;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.store.DedupStore;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.agent.store.ProcessStore;
import com.jimuqu.claw.agent.store.RouteStore;
import com.jimuqu.claw.agent.store.RunStore;
import com.jimuqu.claw.agent.store.SessionStore;
import com.jimuqu.claw.agent.store.file.FileDedupStore;
import com.jimuqu.claw.agent.store.file.FileJobStore;
import com.jimuqu.claw.agent.store.file.FileProcessStore;
import com.jimuqu.claw.agent.store.file.FileRouteStore;
import com.jimuqu.claw.agent.store.file.FileRunStore;
import com.jimuqu.claw.agent.store.file.FileSessionStore;
import com.jimuqu.claw.agent.tool.DefaultToolRegistry;
import com.jimuqu.claw.agent.tool.ToolRegistry;
import com.jimuqu.claw.agent.workspace.DefaultWorkspacePromptService;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.agent.workspace.WorkspacePathGuard;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import com.jimuqu.claw.channel.ChannelAdapter;
import com.jimuqu.claw.channel.DingtalkChannelAdapter;
import com.jimuqu.claw.channel.FeishuChannelAdapter;
import com.jimuqu.claw.channel.WecomChannelAdapter;
import com.jimuqu.claw.channel.WeixinChannelAdapter;
import com.jimuqu.claw.provider.ModelConfigResolver;
import com.jimuqu.claw.provider.ProviderDialect;
import com.jimuqu.claw.provider.StandardProviderDialect;
import com.jimuqu.claw.skill.FileSkillCatalog;
import com.jimuqu.claw.skill.SkillCatalog;
import com.jimuqu.claw.skill.SkillManagerService;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.Props;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ClawConfiguration {
    @Bean
    public ClawProperties clawProperties() {
        Props props = Solon.cfg().getProp("jimuqu.claw");
        ClawProperties properties = new ClawProperties();
        if (props != null) {
            props.bindTo(properties);
        }
        return properties;
    }

    @Bean
    public WorkspaceLayout workspaceLayout(ClawProperties properties) {
        WorkspaceLayout layout = new WorkspaceLayout(properties.getWorkspace().getRoot());
        layout.initialize();
        return layout;
    }

    @Bean
    public WorkspacePathGuard workspacePathGuard(WorkspaceLayout workspaceLayout) {
        return new WorkspacePathGuard(workspaceLayout);
    }

    @Bean
    public SessionStore sessionStore(WorkspaceLayout workspaceLayout) {
        return new FileSessionStore(workspaceLayout);
    }

    @Bean
    public RunStore runStore(WorkspaceLayout workspaceLayout) {
        return new FileRunStore(workspaceLayout);
    }

    @Bean
    public RouteStore routeStore(WorkspaceLayout workspaceLayout) {
        return new FileRouteStore(workspaceLayout);
    }

    @Bean
    public DedupStore dedupStore(WorkspaceLayout workspaceLayout) {
        return new FileDedupStore(workspaceLayout);
    }

    @Bean
    public JobStore jobStore(WorkspaceLayout workspaceLayout) {
        return new FileJobStore(workspaceLayout);
    }

    @Bean
    public ProcessStore processStore(WorkspaceLayout workspaceLayout) {
        return new FileProcessStore(workspaceLayout);
    }

    @Bean
    public SkillCatalog skillCatalog(WorkspaceLayout workspaceLayout) {
        return new FileSkillCatalog(workspaceLayout);
    }

    @Bean
    public SkillManagerService skillManagerService(WorkspaceLayout workspaceLayout, SkillCatalog skillCatalog) {
        return new SkillManagerService(workspaceLayout, skillCatalog);
    }

    @Bean
    public WorkspacePromptService workspacePromptService(WorkspaceLayout workspaceLayout, ClawProperties properties) {
        return new DefaultWorkspacePromptService(workspaceLayout, properties);
    }

    @Bean
    public ProviderDialect openaiProviderDialect() {
        return new StandardProviderDialect("openai");
    }

    @Bean
    public ProviderDialect openaiResponsesProviderDialect() {
        return new StandardProviderDialect("openai-responses");
    }

    @Bean
    public ProviderDialect ollamaProviderDialect() {
        return new StandardProviderDialect("ollama");
    }

    @Bean
    public ProviderDialect geminiProviderDialect() {
        return new StandardProviderDialect("gemini");
    }

    @Bean
    public ProviderDialect anthropicProviderDialect() {
        return new StandardProviderDialect("anthropic");
    }

    @Bean
    public ModelConfigResolver modelConfigResolver(ClawProperties properties) {
        return new ModelConfigResolver(
                properties,
                Arrays.<ProviderDialect>asList(
                        openaiProviderDialect(),
                        openaiResponsesProviderDialect(),
                        ollamaProviderDialect(),
                        geminiProviderDialect(),
                        anthropicProviderDialect()));
    }

    @Bean
    public JobExecutionService jobExecutionService(JobStore jobStore, WorkspaceLayout workspaceLayout) {
        return new JobExecutionService(
                jobStore,
                workspaceLayout,
                () -> Solon.context().getBean(RuntimeService.class));
    }

    @Bean
    public JobSchedulerService jobSchedulerService(JobStore jobStore, JobExecutionService jobExecutionService, ClawProperties properties) {
        return new JobSchedulerService(jobStore, jobExecutionService, properties);
    }

    @Bean
    public ChannelAdapter weixinChannelAdapter(ClawProperties properties) {
        return new WeixinChannelAdapter(channelProperties(properties, "weixin"));
    }

    @Bean
    public ChannelAdapter wecomChannelAdapter(ClawProperties properties) {
        return new WecomChannelAdapter(channelProperties(properties, "wecom"));
    }

    @Bean
    public ChannelAdapter dingtalkChannelAdapter(ClawProperties properties) {
        return new DingtalkChannelAdapter(channelProperties(properties, "dingtalk"));
    }

    @Bean
    public ChannelAdapter feishuChannelAdapter(ClawProperties properties) {
        return new FeishuChannelAdapter(channelProperties(properties, "feishu"));
    }

    @Bean
    public ToolRegistry toolRegistry(
            WorkspaceLayout workspaceLayout,
            WorkspacePathGuard workspacePathGuard,
            SkillCatalog skillCatalog,
            SkillManagerService skillManagerService,
            JobStore jobStore,
            ProcessStore processStore,
            ClawProperties properties,
            List<ChannelAdapter> channelAdapters,
            JobExecutionService jobExecutionService) {
        return new DefaultToolRegistry(
                workspaceLayout,
                workspacePathGuard,
                skillCatalog,
                skillManagerService,
                jobStore,
                processStore,
                properties,
                channelAdapters,
                jobExecutionService,
                () -> Solon.context().getBean(RuntimeService.class));
    }

    @Bean
    public RuntimeService runtimeService(
            WorkspaceLayout workspaceLayout,
            ClawProperties properties,
            SessionStore sessionStore,
            RunStore runStore,
            RouteStore routeStore,
            DedupStore dedupStore,
            WorkspacePromptService workspacePromptService,
            ModelConfigResolver modelConfigResolver,
            ToolRegistry toolRegistry,
            List<ChannelAdapter> channelAdapters) {
        return new DefaultRuntimeService(
                workspaceLayout,
                properties,
                sessionStore,
                runStore,
                routeStore,
                dedupStore,
                workspacePromptService,
                modelConfigResolver,
                toolRegistry,
                channelAdapters);
    }

    private ClawProperties.ChannelProperties channelProperties(ClawProperties properties, String platform) {
        ClawProperties.ChannelProperties channelProperties = properties.getChannels().get(platform);
        return channelProperties == null ? new ClawProperties.ChannelProperties() : channelProperties;
    }
}
