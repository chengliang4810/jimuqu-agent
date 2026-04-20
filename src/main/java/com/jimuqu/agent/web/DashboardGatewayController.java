package com.jimuqu.agent.web;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.MethodType;

import java.util.Map;

/**
 * Dashboard 网关 doctor / setup 接口。
 */
@Controller
public class DashboardGatewayController {
    private final DashboardGatewayDoctorService doctorService;
    private final WeixinQrSetupService weixinQrSetupService;

    public DashboardGatewayController(DashboardGatewayDoctorService doctorService,
                                      WeixinQrSetupService weixinQrSetupService) {
        this.doctorService = doctorService;
        this.weixinQrSetupService = weixinQrSetupService;
    }

    @Mapping(value = "/api/gateway/doctor", method = MethodType.GET)
    public Map<String, Object> doctor() throws Exception {
        return doctorService.doctor();
    }

    @Mapping(value = "/api/gateway/setup/weixin/qr", method = MethodType.POST)
    public Map<String, Object> startWeixinQr() {
        return weixinQrSetupService.start();
    }

    @Mapping(value = "/api/gateway/setup/weixin/qr/{ticket}", method = MethodType.GET)
    public Map<String, Object> getWeixinQr(String ticket) {
        return weixinQrSetupService.get(ticket);
    }
}
