package com.jimuqu.solon.claw.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一维护 .curator_state 的进程内读改写，避免用量统计和整理任务互相覆盖。
 *
 * <p>状态文件同时保存整理器运行信息和单技能统计，因此所有写入必须通过同一把按规范路径分配的锁完成。
 */
public class CuratorStateStore {
    /** 整理器状态降级日志，不输出状态正文或完整本地路径。 */
    private static final Logger log = LoggerFactory.getLogger(CuratorStateStore.class);

    /** 按状态文件规范路径共享的进程内锁。 */
    private static final ConcurrentHashMap<String, Object> STATE_LOCKS =
            new ConcurrentHashMap<String, Object>();

    /** 状态文件路径。 */
    private final File stateFile;

    /** 对应状态文件的共享锁。 */
    private final Object stateLock;

    /**
     * 创建状态存取服务。
     *
     * @param stateFile 技能整理状态文件。
     */
    public CuratorStateStore(File stateFile) {
        if (stateFile == null) {
            throw new IllegalArgumentException("curator state file is required");
        }
        this.stateFile = stateFile;
        this.stateLock = STATE_LOCKS.computeIfAbsent(lockKey(stateFile), ignored -> new Object());
    }

    /**
     * 读取当前完整状态快照。
     *
     * @return 可供调用方安全修改的状态副本。
     */
    public Map<String, Object> read() {
        synchronized (stateLock) {
            return loadState(false);
        }
    }

    /**
     * 在共享锁内执行完整读改写，并通过原子替换提交状态。
     *
     * @param mutation 状态变更函数。
     * @param <T> 调用方结果类型。
     * @return 变更函数返回值。
     */
    public <T> T update(StateMutation<T> mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("curator state mutation is required");
        }
        synchronized (stateLock) {
            Map<String, Object> state = loadState(true);
            T result = mutation.apply(state);
            writeAtomically(state);
            return result;
        }
    }

    /** 状态变更回调，调用期间持有对应状态文件的共享锁。 */
    public interface StateMutation<T> {
        /**
         * 修改完整状态内容。
         *
         * @param state 当前状态。
         * @return 调用方需要的结果。
         */
        T apply(Map<String, Object> state);
    }

    /**
     * 读取状态文件；只读调用可降级为空视图，写调用必须拒绝覆盖损坏状态。
     *
     * @param failOnCorruption 是否在读改写前发现损坏时拒绝继续。
     * @return 当前完整状态。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadState(boolean failOnCorruption) {
        if (!stateFile.isFile()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Object parsed = ONode.deserialize(FileUtil.readUtf8String(stateFile), Object.class);
            if (parsed instanceof Map) {
                return new LinkedHashMap<String, Object>((Map<String, Object>) parsed);
            }
        } catch (Exception e) {
            if (failOnCorruption) {
                throw invalidState(e);
            }
            log.warn(
                    "Curator state could not be parsed; returning an empty read-only view: file={}, errorType={}",
                    stateFile.getName(),
                    e.getClass().getSimpleName());
            return new LinkedHashMap<String, Object>();
        }
        if (failOnCorruption) {
            throw invalidState(null);
        }
        log.warn(
                "Curator state is not a JSON object; returning an empty read-only view: file={}",
                stateFile.getName());
        return new LinkedHashMap<String, Object>();
    }

    /**
     * 构造拒绝覆盖损坏整理器状态的异常。
     *
     * @param cause 解析失败原因。
     * @return 可直接抛出的状态异常。
     */
    private IllegalStateException invalidState(Exception cause) {
        String message = "Curator state is invalid; refusing to overwrite " + stateFile.getName();
        return cause == null
                ? new IllegalStateException(message)
                : new IllegalStateException(message, cause);
    }

    /** 将完整状态写入同目录临时文件后原子替换，避免进程中断留下半份 JSON。 */
    private void writeAtomically(Map<String, Object> state) {
        Path target = stateFile.toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        try {
            if (parent == null) {
                throw new IOException("curator state parent is required");
            }
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, ".curator-state-", ".tmp");
            boolean moved = false;
            try {
                Files.write(
                        temp,
                        StrUtil.nullToEmpty(ONode.serialize(state))
                                .getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(
                            temp,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                moved = true;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save curator state", e);
        }
    }

    /** 生成跨服务实例稳定一致的锁键。 */
    private static String lockKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
