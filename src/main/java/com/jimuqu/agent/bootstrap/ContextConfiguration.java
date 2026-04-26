package com.jimuqu.agent.bootstrap;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.agent.AgentProfileRepository;
import com.jimuqu.agent.agent.AgentProfileService;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.AsyncSkillLearningService;
import com.jimuqu.agent.context.BuiltinMemoryProvider;
import com.jimuqu.agent.context.DefaultMemoryManager;
import com.jimuqu.agent.context.FileMemoryService;
import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.context.PersonaWorkspaceService;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.service.MemoryManager;
import com.jimuqu.agent.core.service.MemoryProvider;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.service.SessionSearchService;
import com.jimuqu.agent.core.service.SkillGuardService;
import com.jimuqu.agent.core.service.SkillHubService;
import com.jimuqu.agent.core.service.SkillImportService;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.engine.DefaultContextCompressionService;
import com.jimuqu.agent.engine.DefaultSessionSearchService;
import com.jimuqu.agent.project.repository.ProjectRepository;
import com.jimuqu.agent.project.service.ProjectService;
import com.jimuqu.agent.skillhub.service.DefaultSkillGuardService;
import com.jimuqu.agent.skillhub.service.DefaultSkillHubService;
import com.jimuqu.agent.skillhub.service.DefaultSkillImportService;
import com.jimuqu.agent.skillhub.source.GitHubSkillSource;
import com.jimuqu.agent.skillhub.support.DefaultSkillHubHttpClient;
import com.jimuqu.agent.skillhub.support.GitHubAuth;
import com.jimuqu.agent.skillhub.support.SkillHubHttpClient;
import com.jimuqu.agent.skillhub.support.SkillHubStateStore;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import java.io.File;

/**
 * context bean configuration.
 */
@Configuration
public class ContextConfiguration {
    @Bean
    public LocalSkillService localSkillService(AppConfig appConfig,
                                               SqlitePreferenceStore preferenceStore,
                                               SkillImportService skillImportService,
                                               SkillHubStateStore skillHubStateStore) {
        return new LocalSkillService(appConfig, preferenceStore, skillImportService, skillHubStateStore);
    }

    @Bean
    public PersonaWorkspaceService personaWorkspaceService(AppConfig appConfig) {
        return new PersonaWorkspaceService(appConfig);
    }

    @Bean
    public AgentProfileService agentProfileService(AgentProfileRepository agentProfileRepository) {
        return new AgentProfileService(agentProfileRepository);
    }

    @Bean(destroyMethod = "shutdown")
    public ProjectService projectService(AppConfig appConfig,
                                          ProjectRepository projectRepository,
                                          AgentProfileService agentProfileService,
                                          GlobalSettingRepository globalSettingRepository) {
        return new ProjectService(appConfig, projectRepository, agentProfileService, globalSettingRepository);
    }

    @Bean
    public FileContextService fileContextService(AppConfig appConfig,
                                                 LocalSkillService localSkillService,
                                                 MemoryManager memoryManager,
                                                 GlobalSettingRepository globalSettingRepository,
                                                 PersonaWorkspaceService personaWorkspaceService) {
        return new FileContextService(appConfig, localSkillService, memoryManager, globalSettingRepository, personaWorkspaceService);
    }

    @Bean
    public MemoryService memoryService(AppConfig appConfig) {
        return new FileMemoryService(appConfig);
    }

    @Bean
    public SkillHubStateStore skillHubStateStore(AppConfig appConfig) {
        return new SkillHubStateStore(FileUtil.file(appConfig.getRuntime().getSkillsDir()));
    }

    @Bean
    public SkillHubHttpClient skillHubHttpClient() {
        return new DefaultSkillHubHttpClient();
    }

    @Bean
    public GitHubAuth gitHubAuth(SkillHubHttpClient skillHubHttpClient) {
        return new GitHubAuth(skillHubHttpClient);
    }

    @Bean
    public SkillGuardService skillGuardService() {
        return new DefaultSkillGuardService();
    }

    @Bean
    public SkillImportService skillImportService(AppConfig appConfig,
                                                 SkillGuardService skillGuardService,
                                                 SkillHubStateStore skillHubStateStore) {
        return new DefaultSkillImportService(
                FileUtil.file(appConfig.getRuntime().getSkillsDir()),
                skillGuardService,
                skillHubStateStore
        );
    }

    @Bean
    public GitHubSkillSource gitHubSkillSource(GitHubAuth gitHubAuth,
                                               SkillHubHttpClient skillHubHttpClient,
                                               SkillHubStateStore skillHubStateStore) {
        return new GitHubSkillSource(gitHubAuth, skillHubHttpClient, skillHubStateStore);
    }

    @Bean
    public SkillHubService skillHubService(AppConfig appConfig,
                                           SkillImportService skillImportService,
                                           SkillGuardService skillGuardService,
                                           SkillHubStateStore skillHubStateStore,
                                           SkillHubHttpClient skillHubHttpClient,
                                           GitHubAuth gitHubAuth,
                                           GitHubSkillSource gitHubSkillSource) {
        return new DefaultSkillHubService(
                new File(System.getProperty("user.dir")),
                FileUtil.file(appConfig.getRuntime().getSkillsDir()),
                skillImportService,
                skillGuardService,
                skillHubStateStore,
                skillHubHttpClient,
                gitHubAuth,
                gitHubSkillSource
        );
    }

    @Bean
    public MemoryProvider builtinMemoryProvider(MemoryService memoryService) {
        return new BuiltinMemoryProvider(memoryService);
    }

    @Bean
    public MemoryManager memoryManager(MemoryProvider builtinMemoryProvider) {
        java.util.List<MemoryProvider> providers = new java.util.ArrayList<MemoryProvider>();
        providers.add(builtinMemoryProvider);
        return new DefaultMemoryManager(providers);
    }

    @Bean
    public ContextCompressionService contextCompressionService(AppConfig appConfig) {
        return new DefaultContextCompressionService(appConfig);
    }

    @Bean
    public SessionSearchService sessionSearchService(SessionRepository sessionRepository,
                                                     LlmGateway llmGateway) {
        return new DefaultSessionSearchService(sessionRepository, llmGateway);
    }

    @Bean(destroyMethod = "shutdown")
    public SkillLearningService skillLearningService(AppConfig appConfig,
                                                     SessionRepository sessionRepository,
                                                     MemoryService memoryService,
                                                     LocalSkillService localSkillService,
                                                     CheckpointService checkpointService) {
        return new AsyncSkillLearningService(appConfig, sessionRepository, memoryService, localSkillService, checkpointService);
    }
}
