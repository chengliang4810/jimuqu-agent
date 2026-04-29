package com.jimuqu.agent.support.constants;

/**
 * 运行时目录与默认值常量。
 */
public interface RuntimePathConstants {
    String RUNTIME_HOME = "runtime";
    String CONTEXT_DIR = "runtime/context";
    String SKILLS_DIR = "runtime/skills";
    String CACHE_DIR = "runtime/cache";
    String STATE_DB = "runtime/state.db";
    String CONFIG_FILE = "runtime/config.yml";
    String LOGS_DIR = "runtime/logs";

    String DEFAULT_PROVIDER_KEY = "default";
    String DEFAULT_LLM_PROVIDER = "openai-responses";
    String DEFAULT_LLM_API_URL = "https://subapi.jimuqu.com/v1/responses";
    String DEFAULT_LLM_MODEL = "gpt-5.4";
    String DEFAULT_REASONING_EFFORT = "medium";
    int DEFAULT_CONTEXT_WINDOW_TOKENS = 128000;

    int DEFAULT_SCHEDULER_TICK_SECONDS = 60;
    int DEFAULT_MAX_TOKENS = 4096;
    double DEFAULT_TEMPERATURE = 0.2D;

    int DEFAULT_HEARTBEAT_INTERVAL_MINUTES = 0;
}
