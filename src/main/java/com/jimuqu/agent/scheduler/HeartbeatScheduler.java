package com.jimuqu.agent.scheduler;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.PersonaWorkspaceService;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.HomeChannelRecord;
import com.jimuqu.agent.core.repository.GatewayPolicyRepository;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.support.constants.ContextFileConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HEARTBEAT.md 固定间隔轮询调度器。
 */
@RequiredArgsConstructor
public class HeartbeatScheduler {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private static final String HEARTBEAT_USER = "__heartbeat__";
    private static final String DEFAULT_PROMPT = "请阅读 HEARTBEAT.md 并严格执行其中的检查清单。如果没有任何需要关注的内容，只回复 HEARTBEAT_OK。";

    private final AppConfig appConfig;
    private final GatewayPolicyRepository gatewayPolicyRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final DeliveryService deliveryService;
    private final PersonaWorkspaceService personaWorkspaceService;
    private ScheduledExecutorService executorService;

    public void start() {
        if (!appConfig.getAgent().getHeartbeat().isEnabled()) {
            return;
        }
        int intervalMinutes = Math.max(1, appConfig.getAgent().getHeartbeat().getIntervalMinutes());
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(this::tickSafe, 30, intervalMinutes * 60L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public void shutdown() {
        stop();
    }

    public void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("Heartbeat tick failed", e);
        }
    }

    public void tick() throws Exception {
        if (!appConfig.getAgent().getHeartbeat().isEnabled()) {
            log.debug("Heartbeat skipped: disabled");
            return;
        }
        if (!hasHeartbeatTasks()) {
            log.debug("Heartbeat skipped: HEARTBEAT.md has no runnable tasks");
            return;
        }

        log.info("Heartbeat tick started: intervalMinutes={}, deliveryMode={}",
                appConfig.getAgent().getHeartbeat().getIntervalMinutes(),
                appConfig.getAgent().getHeartbeat().getDeliveryMode());
        tryRunForPlatform(PlatformType.FEISHU, appConfig.getChannels().getFeishu().isEnabled());
        tryRunForPlatform(PlatformType.DINGTALK, appConfig.getChannels().getDingtalk().isEnabled());
        tryRunForPlatform(PlatformType.WECOM, appConfig.getChannels().getWecom().isEnabled());
        tryRunForPlatform(PlatformType.WEIXIN, appConfig.getChannels().getWeixin().isEnabled());
    }

    private void tryRunForPlatform(PlatformType platform, boolean enabled) throws Exception {
        if (!enabled) {
            log.debug("Heartbeat skipped for {}: channel disabled", platform);
            return;
        }
        HomeChannelRecord home = gatewayPolicyRepository.getHomeChannel(platform);
        if (home == null || StrUtil.isBlank(home.getChatId())) {
            log.debug("Heartbeat skipped for {}: home channel missing", platform);
            return;
        }
        runOnce(platform, home);
    }

    void runOnce(PlatformType platform, HomeChannelRecord home) throws Exception {
        log.info("Heartbeat executing: platform={}, chatId={}, chatName={}",
                platform,
                home.getChatId(),
                StrUtil.blankToDefault(home.getChatName(), home.getChatId()));
        GatewayMessage message = new GatewayMessage(platform, home.getChatId(), HEARTBEAT_USER, DEFAULT_PROMPT);
        message.setHeartbeat(true);
        message.setChatName(home.getChatName());
        message.setUserName(HEARTBEAT_USER);
        message.setSourceKeyOverride(platform.name() + ":" + home.getChatId() + ":" + HEARTBEAT_USER);

        GatewayReply reply = conversationOrchestrator.runScheduled(message);
        if (!shouldDeliver(reply)) {
            log.info("Heartbeat quiet: platform={}, chatId={}", platform, home.getChatId());
            return;
        }

        if (!"home".equalsIgnoreCase(appConfig.getAgent().getHeartbeat().getDeliveryMode())) {
            log.info("Heartbeat produced output but skipped delivery due to deliveryMode={}",
                    appConfig.getAgent().getHeartbeat().getDeliveryMode());
            return;
        }

        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(platform);
        request.setChatId(home.getChatId());
        request.setText(reply.getContent());
        deliveryService.deliver(request);
        log.info("Heartbeat delivered: platform={}, chatId={}, chars={}",
                platform,
                home.getChatId(),
                reply.getContent() == null ? 0 : reply.getContent().length());
    }

    private boolean shouldDeliver(GatewayReply reply) {
        if (reply == null || StrUtil.isBlank(reply.getContent())) {
            return false;
        }
        String quietToken = StrUtil.blankToDefault(appConfig.getAgent().getHeartbeat().getQuietToken(), "HEARTBEAT_OK");
        return !quietToken.equalsIgnoreCase(StrUtil.nullToEmpty(reply.getContent()).trim());
    }

    private boolean hasHeartbeatTasks() {
        String content = personaWorkspaceService.read(ContextFileConstants.KEY_HEARTBEAT);
        if (StrUtil.isBlank(content)) {
            return false;
        }
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String text = StrUtil.nullToEmpty(line).trim();
            if (text.length() == 0) {
                continue;
            }
            if (text.startsWith("#")) {
                continue;
            }
            return true;
        }
        return false;
    }
}
