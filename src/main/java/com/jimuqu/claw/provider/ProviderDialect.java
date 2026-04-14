package com.jimuqu.claw.provider;

import com.jimuqu.claw.provider.model.ResolvedModelConfig;
import org.noear.solon.ai.chat.ChatModel;

public interface ProviderDialect {
    String name();

    ChatModel buildChatModel(ResolvedModelConfig modelConfig);
}
