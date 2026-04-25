package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.config.AppConfig;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/**
 * Root application configuration. Feature beans live in dedicated configuration classes.
 */
@Configuration
public class JimuquAgentConfiguration {
    @Bean
    public AppConfig appConfig() {
        return AppConfig.load(Solon.cfg());
    }
}
