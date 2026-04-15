package com.jimuqu.agent.bootstrap;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class HealthController {
    @Mapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("service", "jimuqu-agent");
        return result;
    }
}
