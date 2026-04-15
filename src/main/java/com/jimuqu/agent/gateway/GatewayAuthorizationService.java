package com.jimuqu.agent.gateway;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.ApprovedUserRecord;
import com.jimuqu.agent.core.ChannelStatus;
import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayPolicyRepository;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.HomeChannelRecord;
import com.jimuqu.agent.core.PairingRateLimitRecord;
import com.jimuqu.agent.core.PairingRequestRecord;
import com.jimuqu.agent.core.PlatformAdminRecord;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.storage.SqliteGatewayPolicyRepository;

import java.security.SecureRandom;
import java.util.List;

public class GatewayAuthorizationService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final long CODE_TTL_MILLIS = 60L * 60L * 1000L;
    private static final long RATE_LIMIT_MILLIS = 10L * 60L * 1000L;
    private static final long LOCKOUT_MILLIS = 60L * 60L * 1000L;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int MAX_PENDING_PER_PLATFORM = 3;

    private final GatewayPolicyRepository repository;
    private final AppConfig appConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public GatewayAuthorizationService(GatewayPolicyRepository repository, AppConfig appConfig) {
        this.repository = repository;
        this.appConfig = appConfig;
    }

    public GatewayReply preAuthorize(GatewayMessage message) throws Exception {
        if (message == null || message.getPlatform() == null) {
            return null;
        }

        PlatformType platform = message.getPlatform();
        PlatformAdminRecord admin = repository.getPlatformAdmin(platform);
        String text = message.getText() == null ? "" : message.getText().trim();

        if (admin == null) {
            if (!isDm(message)) {
                return null;
            }

            if ("/pairing claim-admin".equalsIgnoreCase(text)) {
                return claimAdmin(message);
            }

            PairingRequestRecord claim = repository.getAdminClaimRequest(platform);
            long now = System.currentTimeMillis();
            if (claim == null || claim.getExpiresAt() < now) {
                PairingRequestRecord record = new PairingRequestRecord();
                record.setPlatform(platform);
                record.setCode(SqliteGatewayPolicyRepository.ADMIN_CLAIM_CODE);
                record.setUserId(message.getUserId());
                record.setUserName(message.getUserName());
                record.setChatId(message.getChatId());
                record.setCreatedAt(now);
                record.setExpiresAt(now + CODE_TTL_MILLIS);
                repository.savePairingRequest(record);
                claim = record;
            }

            if (sameUser(claim.getUserId(), message.getUserId())) {
                return GatewayReply.ok(
                        "当前平台还没有管理员，你是当前认领人。\n" +
                                "请在这个私聊里发送 `/pairing claim-admin`，成为 " +
                                platform.name().toLowerCase() + " 平台的唯一管理员。"
                );
            }

            return GatewayReply.ok(
                    "当前平台正在等待首个私聊认领人完成管理员初始化。\n" +
                            "请让当前认领人发送 `/pairing claim-admin` 完成初始化。"
            );
        }

        if (isAuthorized(message)) {
            return null;
        }

        if (!isDm(message)) {
            return null;
        }

        if (!"pair".equals(getUnauthorizedDmBehavior(platform))) {
            return null;
        }

        return createPairingPrompt(message);
    }

    public boolean isAuthorized(GatewayMessage message) throws Exception {
        PlatformType platform = message.getPlatform();
        if (platform == null) {
            return false;
        }

        AppConfig.ChannelConfig channelConfig = channelConfig(platform);
        if (channelConfig != null && channelConfig.isAllowAllUsers()) {
            return true;
        }
        if (appConfig.getGateway().isAllowAllUsers()) {
            return true;
        }

        PlatformAdminRecord admin = repository.getPlatformAdmin(platform);
        if (admin != null && sameUser(admin.getUserId(), message.getUserId())) {
            return true;
        }

        ApprovedUserRecord approved = repository.getApprovedUser(platform, message.getUserId());
        if (approved != null) {
            return true;
        }

        if (channelConfig != null && contains(channelConfig.getAllowedUsers(), message.getUserId())) {
            return true;
        }

        return contains(appConfig.getGateway().getAllowedUsers(), message.getUserId());
    }

    public boolean isAdmin(GatewayMessage message) throws Exception {
        PlatformAdminRecord admin = repository.getPlatformAdmin(message.getPlatform());
        return admin != null && sameUser(admin.getUserId(), message.getUserId());
    }

    public GatewayReply claimAdmin(GatewayMessage message) throws Exception {
        if (!isDm(message)) {
            return GatewayReply.error("管理员认领必须在私聊中完成。");
        }
        PlatformAdminRecord existing = repository.getPlatformAdmin(message.getPlatform());
        if (existing != null) {
            return GatewayReply.error("当前平台已经有管理员。");
        }

        PairingRequestRecord claim = repository.getAdminClaimRequest(message.getPlatform());
        long now = System.currentTimeMillis();
        if (claim == null || claim.getExpiresAt() < now) {
            return GatewayReply.error("当前没有有效的管理员认领请求，请先在私聊里发送任意消息触发管理员初始化。");
        }
        if (!sameUser(claim.getUserId(), message.getUserId())) {
            return GatewayReply.error("只有首个认领人可以完成管理员初始化。");
        }

        PlatformAdminRecord admin = new PlatformAdminRecord();
        admin.setPlatform(message.getPlatform());
        admin.setUserId(message.getUserId());
        admin.setUserName(message.getUserName());
        admin.setChatId(message.getChatId());
        admin.setCreatedAt(now);

        if (!repository.createPlatformAdminIfAbsent(admin)) {
            return GatewayReply.error("当前平台已经有管理员。");
        }

        ApprovedUserRecord approvedUser = new ApprovedUserRecord();
        approvedUser.setPlatform(message.getPlatform());
        approvedUser.setUserId(message.getUserId());
        approvedUser.setUserName(message.getUserName());
        approvedUser.setApprovedAt(now);
        approvedUser.setApprovedBy("self-admin-claim");
        repository.saveApprovedUser(approvedUser);
        repository.deletePairingRequest(message.getPlatform(), SqliteGatewayPolicyRepository.ADMIN_CLAIM_CODE);

        return GatewayReply.ok("你现在已经是 " + message.getPlatform().name().toLowerCase() + " 平台的唯一管理员。");
    }

    public GatewayReply setHome(GatewayMessage message) throws Exception {
        if (!isAdmin(message)) {
            return GatewayReply.error("只有平台管理员可以执行 /sethome。");
        }

        HomeChannelRecord record = new HomeChannelRecord();
        record.setPlatform(message.getPlatform());
        record.setChatId(message.getChatId());
        record.setChatName(blankToDefault(message.getChatName(), message.getChatId()));
        record.setUpdatedAt(System.currentTimeMillis());
        repository.saveHomeChannel(record);
        return GatewayReply.ok("已将 Home Channel 设置为 " + record.getChatName() + "（" + record.getChatId() + "）。");
    }

    public GatewayReply pairingPending(GatewayMessage message, PlatformType targetPlatform) throws Exception {
        if (!isAdminForPlatform(message, targetPlatform)) {
            return GatewayReply.error("只有平台管理员可以查看待处理的 pairing 请求。");
        }
        if (!isDm(message)) {
            return GatewayReply.error("pairing 管理命令必须在私聊中执行。");
        }

        repository.deleteExpiredPairingRequests(targetPlatform, System.currentTimeMillis());
        List<PairingRequestRecord> records = repository.listPairingRequests(targetPlatform, false);
        if (records.isEmpty()) {
            return GatewayReply.ok(targetPlatform.name().toLowerCase() + " 平台当前没有待处理的 pairing 请求。");
        }
        StringBuilder buffer = new StringBuilder();
        for (PairingRequestRecord record : records) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(record.getCode())
                    .append(" -> ")
                    .append(blankToDefault(record.getUserName(), record.getUserId()))
                    .append(" [")
                    .append(record.getUserId())
                    .append("]");
        }
        return GatewayReply.ok(buffer.toString());
    }

    public GatewayReply pairingApprove(GatewayMessage message, PlatformType targetPlatform, String code) throws Exception {
        if (!isAdminForPlatform(message, targetPlatform)) {
            return GatewayReply.error("只有平台管理员可以批准 pairing code。");
        }
        if (!isDm(message)) {
            return GatewayReply.error("pairing 批准必须在私聊中执行。");
        }

        long now = System.currentTimeMillis();
        repository.deleteExpiredPairingRequests(targetPlatform, now);
        PairingRequestRecord request = repository.getPairingRequest(targetPlatform, code);
        if (request == null || request.getExpiresAt() < now || isAdminClaim(request)) {
            recordFailure(targetPlatform, message.getUserId(), now);
            return GatewayReply.error("pairing code 无效或已过期。");
        }

        ApprovedUserRecord approvedUser = new ApprovedUserRecord();
        approvedUser.setPlatform(targetPlatform);
        approvedUser.setUserId(request.getUserId());
        approvedUser.setUserName(request.getUserName());
        approvedUser.setApprovedAt(now);
        approvedUser.setApprovedBy(message.getUserId());
        repository.saveApprovedUser(approvedUser);
        repository.deletePairingRequest(targetPlatform, code);
        clearFailure(targetPlatform, message.getUserId(), now);
        return GatewayReply.ok("已批准 " + blankToDefault(request.getUserName(), request.getUserId()) + " 使用 " + targetPlatform.name().toLowerCase() + " 平台。");
    }

    public GatewayReply pairingRevoke(GatewayMessage message, PlatformType targetPlatform, String userId) throws Exception {
        if (!isAdminForPlatform(message, targetPlatform)) {
            return GatewayReply.error("只有平台管理员可以撤销已批准用户。");
        }
        PlatformAdminRecord admin = repository.getPlatformAdmin(targetPlatform);
        if (admin != null && sameUser(admin.getUserId(), userId)) {
            return GatewayReply.error("平台管理员不能被撤销。");
        }
        repository.revokeApprovedUser(targetPlatform, userId);
        return GatewayReply.ok("已撤销 " + userId + " 在 " + targetPlatform.name().toLowerCase() + " 平台的使用权限。");
    }

    public GatewayReply pairingApproved(GatewayMessage message, PlatformType targetPlatform) throws Exception {
        if (!isAdminForPlatform(message, targetPlatform)) {
            return GatewayReply.error("只有平台管理员可以查看已批准用户。");
        }
        List<ApprovedUserRecord> records = repository.listApprovedUsers(targetPlatform);
        if (records.isEmpty()) {
            return GatewayReply.ok(targetPlatform.name().toLowerCase() + " 平台当前没有已批准用户。");
        }
        StringBuilder buffer = new StringBuilder();
        for (ApprovedUserRecord record : records) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(blankToDefault(record.getUserName(), record.getUserId()))
                    .append(" [")
                    .append(record.getUserId())
                    .append("]");
        }
        return GatewayReply.ok(buffer.toString());
    }

    public String formatPlatformStatus(List<ChannelStatus> statuses) throws Exception {
        StringBuilder buffer = new StringBuilder();
        for (ChannelStatus status : statuses) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            PlatformAdminRecord admin = repository.getPlatformAdmin(status.getPlatform());
            HomeChannelRecord home = repository.getHomeChannel(status.getPlatform());
            int approved = repository.countApprovedUsers(status.getPlatform());
            buffer.append(status.getPlatform())
                    .append(" enabled=").append(status.isEnabled())
                    .append(" connected=").append(status.isConnected())
                    .append(" detail=").append(status.getDetail())
                    .append(" admin=").append(admin == null ? "none" : admin.getUserId())
                    .append(" home=").append(home == null ? "none" : home.getChatId())
                    .append(" pairing=").append(getUnauthorizedDmBehavior(status.getPlatform()))
                    .append(" approved=").append(approved);
        }
        return buffer.toString();
    }

    public HomeChannelRecord getHomeChannel(PlatformType platform) throws Exception {
        return repository.getHomeChannel(platform);
    }

    private GatewayReply createPairingPrompt(GatewayMessage message) throws Exception {
        PlatformType platform = message.getPlatform();
        long now = System.currentTimeMillis();
        PairingRateLimitRecord rateLimit = repository.getPairingRateLimit(platform, message.getUserId());
        if (rateLimit != null && rateLimit.getLockoutUntil() > now) {
            return GatewayReply.ok("pairing 失败次数过多，请稍后再试。");
        }
        if (rateLimit != null && rateLimit.getRequestedAt() > 0 && now - rateLimit.getRequestedAt() < RATE_LIMIT_MILLIS) {
            return null;
        }

        repository.deleteExpiredPairingRequests(platform, now);
        List<PairingRequestRecord> pending = repository.listPairingRequests(platform, false);
        if (pending.size() >= MAX_PENDING_PER_PLATFORM) {
            return GatewayReply.ok("当前待处理的 pairing 请求过多，请稍后再试。");
        }

        PairingRequestRecord existing = repository.getLatestUserPairingRequest(platform, message.getUserId());
        if (existing != null && existing.getExpiresAt() > now) {
            saveRequestRate(platform, message.getUserId(), now, rateLimit == null ? 0 : rateLimit.getFailedAttempts(), rateLimit == null ? 0L : rateLimit.getLockoutUntil());
            return pairingPrompt(platform, existing.getCode());
        }

        PairingRequestRecord request = new PairingRequestRecord();
        request.setPlatform(platform);
        request.setCode(generateCode());
        request.setUserId(message.getUserId());
        request.setUserName(message.getUserName());
        request.setChatId(message.getChatId());
        request.setCreatedAt(now);
        request.setExpiresAt(now + CODE_TTL_MILLIS);
        repository.savePairingRequest(request);
        saveRequestRate(platform, message.getUserId(), now, rateLimit == null ? 0 : rateLimit.getFailedAttempts(), rateLimit == null ? 0L : rateLimit.getLockoutUntil());
        return pairingPrompt(platform, request.getCode());
    }

    private GatewayReply pairingPrompt(PlatformType platform, String code) {
        return GatewayReply.ok(
                "当前还未识别你的身份。\n\n" +
                        "这是你的 pairing code：`" + code + "`\n\n" +
                        "请联系平台管理员在私聊中执行：\n" +
                        "`/pairing approve " + platform.name().toLowerCase() + " " + code + "`"
        );
    }

    private void recordFailure(PlatformType platform, String userId, long now) throws Exception {
        PairingRateLimitRecord record = repository.getPairingRateLimit(platform, userId);
        if (record == null) {
            record = new PairingRateLimitRecord();
            record.setPlatform(platform);
            record.setUserId(userId);
        }
        int attempts = record.getFailedAttempts() + 1;
        record.setRequestedAt(now);
        record.setFailedAttempts(attempts >= MAX_FAILED_ATTEMPTS ? 0 : attempts);
        record.setLockoutUntil(attempts >= MAX_FAILED_ATTEMPTS ? now + LOCKOUT_MILLIS : 0L);
        repository.savePairingRateLimit(record);
    }

    private void clearFailure(PlatformType platform, String userId, long now) throws Exception {
        saveRequestRate(platform, userId, now, 0, 0L);
    }

    private void saveRequestRate(PlatformType platform, String userId, long requestedAt, int failedAttempts, long lockoutUntil) throws Exception {
        PairingRateLimitRecord record = new PairingRateLimitRecord();
        record.setPlatform(platform);
        record.setUserId(userId);
        record.setRequestedAt(requestedAt);
        record.setFailedAttempts(failedAttempts);
        record.setLockoutUntil(lockoutUntil);
        repository.savePairingRateLimit(record);
    }

    private boolean isAdminForPlatform(GatewayMessage message, PlatformType platform) throws Exception {
        if (!isDm(message)) {
            return false;
        }
        if (message.getPlatform() != platform) {
            return false;
        }
        return isAdmin(message);
    }

    private AppConfig.ChannelConfig channelConfig(PlatformType platform) {
        if (platform == PlatformType.DINGTALK) {
            return appConfig.getChannels().getDingtalk();
        }
        if (platform == PlatformType.FEISHU) {
            return appConfig.getChannels().getFeishu();
        }
        if (platform == PlatformType.WECOM) {
            return appConfig.getChannels().getWecom();
        }
        if (platform == PlatformType.WEIXIN) {
            return appConfig.getChannels().getWeixin();
        }
        return null;
    }

    private boolean contains(List<String> values, String userId) {
        if (values == null || userId == null) {
            return false;
        }
        if (values.contains("*")) {
            return true;
        }
        return values.contains(userId);
    }

    private String getUnauthorizedDmBehavior(PlatformType platform) {
        AppConfig.ChannelConfig channelConfig = channelConfig(platform);
        return channelConfig == null ? "pair" : channelConfig.getUnauthorizedDmBehavior();
    }

    private boolean isDm(GatewayMessage message) {
        return "dm".equalsIgnoreCase(blankToDefault(message.getChatType(), "dm"));
    }

    private boolean sameUser(String left, String right) {
        return left != null && left.equals(right);
    }

    private boolean isAdminClaim(PairingRequestRecord record) {
        return SqliteGatewayPolicyRepository.ADMIN_CLAIM_CODE.equals(record.getCode());
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private String generateCode() {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(ALPHABET.length());
            buffer.append(ALPHABET.charAt(index));
        }
        return buffer.toString();
    }
}
