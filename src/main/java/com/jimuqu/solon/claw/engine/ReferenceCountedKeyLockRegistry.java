package com.jimuqu.solon.claw.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按键复用监视器并用租约引用计数控制生命周期。
 *
 * <p>等待进入监视器的调用方也持有租约，因此当前持有者释放后不会为同一键创建第二个监视器。
 */
final class ReferenceCountedKeyLockRegistry {

    /** 活跃键对应的共享监视器条目。 */
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<String, Entry>();

    /**
     * 获取指定键的监视器租约。
     *
     * @param key 非空锁键。
     * @return 必须关闭的监视器租约。
     */
    Lease acquire(String key) {
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("锁键不能为空");
        }
        AtomicReference<Entry> acquired = new AtomicReference<Entry>();
        entries.compute(
                key,
                (ignored, current) -> {
                    Entry entry = current == null ? new Entry() : current;
                    entry.references++;
                    acquired.set(entry);
                    return entry;
                });
        return new Lease(this, key, acquired.get());
    }

    /**
     * 返回当前仍有持有者或等待者的键数量。
     *
     * @return 活跃锁键数量。
     */
    int activeKeyCount() {
        return entries.size();
    }

    /**
     * 释放指定租约持有的引用，并在最后一个引用释放时移除条目。
     *
     * @param key 租约锁键。
     * @param entry 租约持有的条目。
     */
    private void release(String key, Entry entry) {
        AtomicReference<IllegalStateException> failure =
                new AtomicReference<IllegalStateException>();
        entries.compute(
                key,
                (ignored, current) -> {
                    if (current != entry || entry.references <= 0) {
                        failure.set(new IllegalStateException("锁租约与注册表状态不一致"));
                        return current;
                    }
                    entry.references--;
                    return entry.references == 0 ? null : entry;
                });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    /** 单个键对应的共享监视器和受注册表原子操作保护的引用计数。 */
    private static final class Entry {

        /** 同一键的所有调用方必须同步使用的稳定监视器。 */
        private final Object monitor = new Object();

        /** 已获取且尚未关闭的租约数量，包括正在等待监视器的调用方。 */
        private int references;
    }

    /** 持有一个共享监视器引用的幂等关闭租约。 */
    static final class Lease implements AutoCloseable {

        /** 创建此租约的注册表。 */
        private final ReferenceCountedKeyLockRegistry owner;

        /** 租约对应的锁键。 */
        private final String key;

        /** 租约持有的共享条目。 */
        private final Entry entry;

        /** 防止重复关闭导致引用计数被多次扣减。 */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * 创建共享监视器租约。
         *
         * @param owner 创建租约的注册表。
         * @param key 租约锁键。
         * @param entry 租约持有的条目。
         */
        private Lease(ReferenceCountedKeyLockRegistry owner, String key, Entry entry) {
            this.owner = owner;
            this.key = key;
            this.entry = entry;
        }

        /**
         * 返回租约持有的稳定监视器。
         *
         * @return 供 synchronized 使用的监视器。
         */
        Object monitor() {
            return entry.monitor;
        }

        /** 仅第一次关闭时释放注册表引用。 */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.release(key, entry);
            }
        }
    }
}
