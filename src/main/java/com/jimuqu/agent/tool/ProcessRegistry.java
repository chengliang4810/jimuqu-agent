package com.jimuqu.agent.tool;

import com.jimuqu.agent.support.IdSupport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessRegistry {
    private final Map<String, Process> processes = Collections.synchronizedMap(new LinkedHashMap<String, Process>());

    public String add(Process process) {
        String id = IdSupport.newId();
        processes.put(id, process);
        return id;
    }

    public Map<String, Process> snapshot() {
        return new LinkedHashMap<String, Process>(processes);
    }

    public boolean stop(String id) {
        Process process = processes.remove(id);
        if (process == null) {
            return false;
        }

        process.destroy();
        return true;
    }
}
