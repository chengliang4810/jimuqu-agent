package com.jimuqu.solon.claw.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证引用计数键锁在持有者、等待者和回收边界上的行为。 */
public class ReferenceCountedKeyLockRegistryTest {

    /** 等待者尚未退出时，后来者必须继续复用原监视器。 */
    @Test
    void shouldKeepMonitorWhileAnyLeaseRemains() {
        ReferenceCountedKeyLockRegistry registry = new ReferenceCountedKeyLockRegistry();
        ReferenceCountedKeyLockRegistry.Lease owner = registry.acquire("source");
        ReferenceCountedKeyLockRegistry.Lease waiting = registry.acquire("source");
        Object monitor = owner.monitor();

        owner.close();
        ReferenceCountedKeyLockRegistry.Lease later = registry.acquire("source");

        assertThat(waiting.monitor()).isSameAs(monitor);
        assertThat(later.monitor()).isSameAs(monitor);
        assertThat(registry.activeKeyCount()).isEqualTo(1);
        waiting.close();
        later.close();
        assertThat(registry.activeKeyCount()).isZero();
    }

    /** 嵌套获取和重复关闭不得提前删除同一键的条目。 */
    @Test
    void shouldSupportNestedAcquireAndIdempotentClose() {
        ReferenceCountedKeyLockRegistry registry = new ReferenceCountedKeyLockRegistry();
        ReferenceCountedKeyLockRegistry.Lease outer = registry.acquire("nested");
        ReferenceCountedKeyLockRegistry.Lease inner = registry.acquire("nested");

        assertThat(inner.monitor()).isSameAs(outer.monitor());
        inner.close();
        inner.close();

        assertThat(registry.activeKeyCount()).isEqualTo(1);
        outer.close();
        assertThat(registry.activeKeyCount()).isZero();
    }

    /** 大量唯一来源依次完成后，注册表不得保留历史键。 */
    @Test
    void shouldRemoveCompletedUniqueKeys() {
        ReferenceCountedKeyLockRegistry registry = new ReferenceCountedKeyLockRegistry();

        for (int index = 0; index < 1_000; index++) {
            try (ReferenceCountedKeyLockRegistry.Lease ignored =
                    registry.acquire("source-" + index)) {
                assertThat(registry.activeKeyCount()).isEqualTo(1);
            }
        }

        assertThat(registry.activeKeyCount()).isZero();
    }

    /** 不同来源键必须使用不同监视器，允许彼此并发执行。 */
    @Test
    void shouldUseIndependentMonitorsForDifferentKeys() {
        ReferenceCountedKeyLockRegistry registry = new ReferenceCountedKeyLockRegistry();
        try (ReferenceCountedKeyLockRegistry.Lease first = registry.acquire("first");
                ReferenceCountedKeyLockRegistry.Lease second = registry.acquire("second")) {
            assertThat(first.monitor()).isNotSameAs(second.monitor());
            assertThat(registry.activeKeyCount()).isEqualTo(2);
        }
        assertThat(registry.activeKeyCount()).isZero();
    }
}
