package com.jimuqu.solon.claw.context;

import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在全局设置表中持久化跨会话反思状态。 */
public class ReflectionStateStore {
    /** 跨会话反思状态降级日志，不输出持久化状态正文。 */
    private static final Logger log = LoggerFactory.getLogger(ReflectionStateStore.class);

    /** 跨会话反思状态键。 */
    public static final String STATE_KEY = "reflection.cross_session.state";

    /** 全局设置仓储。 */
    private final GlobalSettingRepository globalSettingRepository;

    /**
     * 创建反思状态仓储。
     *
     * @param globalSettingRepository 全局设置仓储。
     */
    public ReflectionStateStore(GlobalSettingRepository globalSettingRepository) {
        this.globalSettingRepository = globalSettingRepository;
    }

    /** 读取状态；缺失或损坏时返回默认状态。 */
    public ReflectionState load() {
        if (globalSettingRepository == null) {
            return new ReflectionState();
        }
        try {
            ReflectionState state =
                    ONode.deserialize(
                            globalSettingRepository.get(STATE_KEY), ReflectionState.class);
            return state == null ? new ReflectionState() : state;
        } catch (Exception e) {
            log.warn(
                    "Cross-session reflection state could not be parsed; using default diagnostics: errorType={}",
                    e.getClass().getSimpleName());
            return new ReflectionState();
        }
    }

    /**
     * 保存反思状态。
     *
     * @param state 要保存的状态。
     * @throws Exception 状态持久化失败时抛出异常。
     */
    public void save(ReflectionState state) throws Exception {
        if (globalSettingRepository != null && state != null) {
            globalSettingRepository.set(STATE_KEY, ONode.serialize(state));
        }
    }
}
