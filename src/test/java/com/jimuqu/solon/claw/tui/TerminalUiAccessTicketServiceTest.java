package com.jimuqu.solon.claw.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** 验证终端 UI 短时一次性 WebSocket 票据的安全边界。 */
class TerminalUiAccessTicketServiceTest {
    /** 验证生产票据具有 256 位随机输入、Base64URL 格式和足够唯一性。 */
    @Test
    void shouldIssueUniqueBase64UrlTickets() {
        TerminalUiAccessTicketService service = new TerminalUiAccessTicketService();
        Set<String> tickets = new LinkedHashSet<String>();

        for (int i = 0; i < 128; i++) {
            tickets.add(service.issue());
        }

        assertThat(tickets).hasSize(128);
        assertThat(tickets)
                .allSatisfy(
                        ticket -> {
                            assertThat(ticket).hasSize(43);
                            assertThat(ticket).matches("[A-Za-z0-9_-]+");
                        });
    }

    /** 验证票据首次消费成功，随后重放和未知值都会失败。 */
    @Test
    void shouldConsumeTicketOnlyOnce() {
        TerminalUiAccessTicketService service = new TerminalUiAccessTicketService();
        String ticket = service.issue();

        assertThat(service.consume(ticket)).isTrue();
        assertThat(service.consume(ticket)).isFalse();
        assertThat(service.consume("unknown-ticket")).isFalse();
        assertThat(service.consume("")).isFalse();
    }

    /** 验证多个线程并发消费同一票据时只有一个调用能够成功。 */
    @Test
    void shouldConsumeTicketAtomicallyAcrossThreads() throws Exception {
        TerminalUiAccessTicketService service = new TerminalUiAccessTicketService();
        String ticket = service.issue();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(
                        executor.submit(
                                new Callable<Boolean>() {
                                    @Override
                                    public Boolean call() throws Exception {
                                        start.await();
                                        return Boolean.valueOf(service.consume(ticket));
                                    }
                                }));
            }
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get().booleanValue()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 验证达到容量上限时 fail closed，消费后才允许继续签发。 */
    @Test
    void shouldFailClosedAtCapacity() {
        TerminalUiAccessTicketService service = service(new AtomicLong(1_000L), 30_000L, 2);
        String first = service.issue();
        service.issue();

        assertThatThrownBy(service::issue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many outstanding");

        assertThat(service.consume(first)).isTrue();
        assertThat(service.issue()).isNotBlank();
    }

    /** 验证票据在 30 秒边界过期，过期记录会在下一次签发前释放容量。 */
    @Test
    void shouldExpireAndReclaimOutstandingTickets() {
        AtomicLong now = new AtomicLong(1_000L);
        TerminalUiAccessTicketService service = service(now, 30_000L, 1);
        String ticket = service.issue();

        now.set(31_000L);

        assertThat(service.consume(ticket)).isFalse();
        assertThat(service.issue()).isNotBlank();
    }

    /**
     * 创建使用可变测试时钟的票据服务。
     *
     * @param now 当前毫秒时间。
     * @param ttlMillis 票据有效期。
     * @param capacity 未消费票据容量。
     * @return 可稳定控制时间的票据服务。
     */
    private TerminalUiAccessTicketService service(AtomicLong now, long ttlMillis, int capacity) {
        return new TerminalUiAccessTicketService(
                new SecureRandom(),
                new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return now.get();
                    }
                },
                ttlMillis,
                capacity);
    }
}
