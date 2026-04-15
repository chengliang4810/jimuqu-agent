package com.jimuqu.agent.core;

import java.util.List;

public interface DeliveryService {
    void deliver(DeliveryRequest request) throws Exception;

    List<ChannelStatus> statuses();
}
