package com.jimuqu.agent.core;

import java.util.List;

public interface GatewayPolicyRepository {
    HomeChannelRecord getHomeChannel(PlatformType platform) throws Exception;

    void saveHomeChannel(HomeChannelRecord record) throws Exception;

    PlatformAdminRecord getPlatformAdmin(PlatformType platform) throws Exception;

    boolean createPlatformAdminIfAbsent(PlatformAdminRecord record) throws Exception;

    ApprovedUserRecord getApprovedUser(PlatformType platform, String userId) throws Exception;

    void saveApprovedUser(ApprovedUserRecord record) throws Exception;

    void revokeApprovedUser(PlatformType platform, String userId) throws Exception;

    List<ApprovedUserRecord> listApprovedUsers(PlatformType platform) throws Exception;

    int countApprovedUsers(PlatformType platform) throws Exception;

    PairingRequestRecord getPairingRequest(PlatformType platform, String code) throws Exception;

    PairingRequestRecord getAdminClaimRequest(PlatformType platform) throws Exception;

    PairingRequestRecord getLatestUserPairingRequest(PlatformType platform, String userId) throws Exception;

    void savePairingRequest(PairingRequestRecord record) throws Exception;

    void deletePairingRequest(PlatformType platform, String code) throws Exception;

    void deleteExpiredPairingRequests(PlatformType platform, long nowEpochMillis) throws Exception;

    List<PairingRequestRecord> listPairingRequests(PlatformType platform, boolean includeAdminClaim) throws Exception;

    PairingRateLimitRecord getPairingRateLimit(PlatformType platform, String userId) throws Exception;

    void savePairingRateLimit(PairingRateLimitRecord record) throws Exception;
}
