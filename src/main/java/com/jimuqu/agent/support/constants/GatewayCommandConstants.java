package com.jimuqu.agent.support.constants;

/**
 * 对话内命令与常见动作常量。
 */
public interface GatewayCommandConstants {
    /**
     * 命令前缀。
     */
    String COMMAND_PREFIX = "/";

    /**
     * 通用动作名。
     */
    String ACTION_LIST = "list";
    String ACTION_ENABLE = "enable";
    String ACTION_DISABLE = "disable";
    String ACTION_INSPECT = "inspect";
    String ACTION_RELOAD = "reload";
    String ACTION_CREATE = "create";
    String ACTION_PAUSE = "pause";
    String ACTION_RESUME = "resume";
    String ACTION_DELETE = "delete";
    String ACTION_RUN = "run";
    String ACTION_APPROVE = "approve";
    String ACTION_REVOKE = "revoke";
    String ACTION_PENDING = "pending";
    String ACTION_APPROVED = "approved";
    String ACTION_CLAIM_ADMIN = "claim-admin";
    String ACTION_CLEAR = "clear";
    String ACTION_SEARCH = "search";
    String ACTION_BROWSE = "browse";
    String ACTION_INSTALL = "install";
    String ACTION_CHECK = "check";
    String ACTION_UPDATE = "update";
    String ACTION_AUDIT = "audit";
    String ACTION_UNINSTALL = "uninstall";
    String ACTION_TAP = "tap";
    String ACTION_ADD = "add";
    String ACTION_REMOVE = "remove";

    /**
     * 一级命令。
     */
    String COMMAND_NEW = "new";
    String COMMAND_RESET = "reset";
    String COMMAND_RETRY = "retry";
    String COMMAND_UNDO = "undo";
    String COMMAND_BRANCH = "branch";
    String COMMAND_RESUME = "resume";
    String COMMAND_STATUS = "status";
    String COMMAND_STOP = "stop";
    String COMMAND_PERSONALITY = "personality";
    String COMMAND_MODEL = "model";
    String COMMAND_TOOLS = "tools";
    String COMMAND_SKILLS = "skills";
    String COMMAND_CRON = "cron";
    String COMMAND_PLATFORMS = "platforms";
    String COMMAND_SETHOME = "sethome";
    String COMMAND_PAIRING = "pairing";
    String COMMAND_COMPRESS = "compress";
    String COMMAND_ROLLBACK = "rollback";
    String COMMAND_HELP = "help";

    /**
     * 完整 slash 命令文本。
     */
    String SLASH_NEW = COMMAND_PREFIX + COMMAND_NEW;
    String SLASH_RESET = COMMAND_PREFIX + COMMAND_RESET;
    String SLASH_RETRY = COMMAND_PREFIX + COMMAND_RETRY;
    String SLASH_UNDO = COMMAND_PREFIX + COMMAND_UNDO;
    String SLASH_BRANCH = COMMAND_PREFIX + COMMAND_BRANCH;
    String SLASH_RESUME = COMMAND_PREFIX + COMMAND_RESUME;
    String SLASH_STATUS = COMMAND_PREFIX + COMMAND_STATUS;
    String SLASH_STOP = COMMAND_PREFIX + COMMAND_STOP;
    String SLASH_PERSONALITY = COMMAND_PREFIX + COMMAND_PERSONALITY;
    String SLASH_MODEL = COMMAND_PREFIX + COMMAND_MODEL;
    String SLASH_TOOLS = COMMAND_PREFIX + COMMAND_TOOLS;
    String SLASH_SKILLS = COMMAND_PREFIX + COMMAND_SKILLS;
    String SLASH_CRON = COMMAND_PREFIX + COMMAND_CRON;
    String SLASH_PLATFORMS = COMMAND_PREFIX + COMMAND_PLATFORMS;
    String SLASH_SETHOME = COMMAND_PREFIX + COMMAND_SETHOME;
    String SLASH_PAIRING = COMMAND_PREFIX + COMMAND_PAIRING;
    String SLASH_COMPRESS = COMMAND_PREFIX + COMMAND_COMPRESS;
    String SLASH_ROLLBACK = COMMAND_PREFIX + COMMAND_ROLLBACK;
    String SLASH_HELP = COMMAND_PREFIX + COMMAND_HELP;
}
