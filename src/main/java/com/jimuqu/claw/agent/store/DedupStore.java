package com.jimuqu.claw.agent.store;

public interface DedupStore {
    boolean markIfAbsent(String dedupKey);

    boolean exists(String dedupKey);
}
