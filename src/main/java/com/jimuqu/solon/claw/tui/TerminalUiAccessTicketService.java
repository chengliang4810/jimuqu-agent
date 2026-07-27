package com.jimuqu.solon.claw.tui;

import cn.hutool.core.util.StrUtil;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.noear.solon.annotation.Component;

/** 终端 UI WebSocket 短时一次性访问票据服务。 */
@Component
public class TerminalUiAccessTicketService {
    /** 每张票据包含的随机字节数，对应 256 位熵。 */
    static final int TICKET_BYTES = 32;

    /** 生产票据默认有效期为 30 秒。 */
    static final long DEFAULT_TTL_MILLIS = 30_000L;

    /** 生产环境最多保留的未消费票据数量。 */
    static final int DEFAULT_MAX_OUTSTANDING_TICKETS = 1_024;

    /** 单次生成票据时允许的最大随机碰撞重试次数。 */
    private static final int MAX_GENERATION_ATTEMPTS = 16;

    /** 用于生成不可预测票据的安全随机数。 */
    private final SecureRandom secureRandom;

    /** 当前时间提供器，测试可替换以稳定验证过期行为。 */
    private final LongSupplier currentTimeMillis;

    /** 每张票据的有效期。 */
    private final long ttlMillis;

    /** 未消费票据的容量上限。 */
    private final int maxOutstandingTickets;

    /** 按签发顺序保存票据及其绝对过期时间。 */
    private final Map<String, Long> tickets = new LinkedHashMap<String, Long>();

    /** 创建使用生产安全参数的一次性票据服务。 */
    public TerminalUiAccessTicketService() {
        this(
                new SecureRandom(),
                new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return System.currentTimeMillis();
                    }
                },
                DEFAULT_TTL_MILLIS,
                DEFAULT_MAX_OUTSTANDING_TICKETS);
    }

    /**
     * 创建可控制时钟和容量的票据服务，供同包测试验证边界。
     *
     * @param secureRandom 安全随机数来源。
     * @param currentTimeMillis 当前时间提供器。
     * @param ttlMillis 票据有效期，必须大于零。
     * @param maxOutstandingTickets 未消费票据容量，必须大于零。
     */
    TerminalUiAccessTicketService(
            SecureRandom secureRandom,
            LongSupplier currentTimeMillis,
            long ttlMillis,
            int maxOutstandingTickets) {
        if (secureRandom == null || currentTimeMillis == null) {
            throw new IllegalArgumentException("Ticket dependencies must not be null");
        }
        if (ttlMillis <= 0L || maxOutstandingTickets <= 0) {
            throw new IllegalArgumentException("Ticket limits must be positive");
        }
        this.secureRandom = secureRandom;
        this.currentTimeMillis = currentTimeMillis;
        this.ttlMillis = ttlMillis;
        this.maxOutstandingTickets = maxOutstandingTickets;
    }

    /**
     * 签发一张短时一次性票据。
     *
     * @return Base64URL 编码且不含填充的 256 位随机票据。
     * @throws IllegalStateException 未消费票据达到容量上限或随机源持续碰撞时抛出。
     */
    public synchronized String issue() {
        long now = currentTimeMillis.getAsLong();
        removeExpired(now);
        if (tickets.size() >= maxOutstandingTickets) {
            throw new IllegalStateException("Too many outstanding terminal UI access tickets");
        }
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[TICKET_BYTES];
            secureRandom.nextBytes(bytes);
            String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!tickets.containsKey(ticket)) {
                tickets.put(ticket, Long.valueOf(now + ttlMillis));
                return ticket;
            }
        }
        throw new IllegalStateException("Unable to generate a unique terminal UI access ticket");
    }

    /**
     * 原子消费票据，票据无论成功、过期或重放都不会再次生效。
     *
     * @param ticket WebSocket 握手携带的短时票据。
     * @return 票据存在且尚未过期时返回 true。
     */
    public synchronized boolean consume(String ticket) {
        if (StrUtil.isBlank(ticket)) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        Long expiresAt = tickets.remove(ticket);
        removeExpired(now);
        return expiresAt != null && expiresAt.longValue() > now;
    }

    /**
     * 移除已经过期的未消费票据，释放容量。
     *
     * @param now 当前毫秒时间。
     */
    private void removeExpired(long now) {
        Iterator<Map.Entry<String, Long>> iterator = tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().longValue() <= now) {
                iterator.remove();
            }
        }
    }
}
