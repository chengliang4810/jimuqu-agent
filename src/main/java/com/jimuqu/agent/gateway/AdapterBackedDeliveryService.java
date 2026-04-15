package com.jimuqu.agent.gateway;

import com.jimuqu.agent.core.ChannelAdapter;
import com.jimuqu.agent.core.ChannelStatus;
import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.DeliveryService;
import com.jimuqu.agent.core.GatewayPolicyRepository;
import com.jimuqu.agent.core.HomeChannelRecord;
import com.jimuqu.agent.core.PlatformType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdapterBackedDeliveryService implements DeliveryService {
    private final Map<PlatformType, ChannelAdapter> adapters;
    private final GatewayPolicyRepository gatewayPolicyRepository;

    public AdapterBackedDeliveryService(Map<PlatformType, ChannelAdapter> adapters, GatewayPolicyRepository gatewayPolicyRepository) {
        this.adapters = adapters;
        this.gatewayPolicyRepository = gatewayPolicyRepository;
    }

    public void deliver(DeliveryRequest request) throws Exception {
        if (request.getChatId() == null || request.getChatId().trim().isEmpty()) {
            HomeChannelRecord home = gatewayPolicyRepository.getHomeChannel(request.getPlatform());
            if (home == null) {
                throw new IllegalStateException("No home channel configured for platform: " + request.getPlatform());
            }
            request.setChatId(home.getChatId());
        }

        ChannelAdapter adapter = adapters.get(request.getPlatform());
        if (adapter == null) {
            throw new IllegalStateException("No adapter for platform: " + request.getPlatform());
        }

        adapter.send(request);
    }

    public List<ChannelStatus> statuses() {
        List<ChannelStatus> items = new ArrayList<ChannelStatus>();
        for (ChannelAdapter adapter : adapters.values()) {
            items.add(new ChannelStatus(adapter.platform(), adapter.isEnabled(), adapter.isConnected(), adapter.detail()));
        }
        return items;
    }
}
