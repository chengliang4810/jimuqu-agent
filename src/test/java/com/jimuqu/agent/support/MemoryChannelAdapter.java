package com.jimuqu.agent.support;

import com.jimuqu.agent.core.ChannelAdapter;
import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.PlatformType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryChannelAdapter implements ChannelAdapter {
    private final List<DeliveryRequest> requests = Collections.synchronizedList(new ArrayList<DeliveryRequest>());

    public PlatformType platform() {
        return PlatformType.MEMORY;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean connect() {
        return true;
    }

    public void disconnect() {
    }

    public boolean isConnected() {
        return true;
    }

    public String detail() {
        return "in-memory";
    }

    public void send(DeliveryRequest request) {
        requests.add(request);
    }

    public List<DeliveryRequest> getRequests() {
        return new ArrayList<DeliveryRequest>(requests);
    }

    public DeliveryRequest getLastRequest() {
        if (requests.isEmpty()) {
            return null;
        }
        return requests.get(requests.size() - 1);
    }
}
