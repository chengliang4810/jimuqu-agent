package com.jimuqu.solon.claw.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard media cache endpoints. */
@Controller
public class DashboardMediaController {
    private final DashboardMediaService mediaService;

    public DashboardMediaController(DashboardMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Mapping(value = "/api/hermes/media", method = MethodType.GET)
    public Map<String, Object> list(Context context) throws Exception {
        return DashboardResponse.ok(
                mediaService.list(context.param("platform"), context.paramAsInt("limit", 50)));
    }

    @Mapping(value = "/api/hermes/media/index", method = MethodType.POST)
    public Map<String, Object> index(Context context) throws Exception {
        return DashboardResponse.ok(
                mediaService.indexLocal(
                        ONode.deserialize(
                                ONode.ofJson(context.body()).toJson(), LinkedHashMap.class)));
    }
}
