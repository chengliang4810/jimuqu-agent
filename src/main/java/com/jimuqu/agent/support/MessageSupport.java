package com.jimuqu.agent.support;

import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MessageSupport {
    private MessageSupport() {
    }

    public static List<ChatMessage> loadMessages(String ndjson) throws IOException {
        if (ndjson == null || ndjson.trim().isEmpty()) {
            return new ArrayList<ChatMessage>();
        }

        return new ArrayList<ChatMessage>(ChatMessage.fromNdjson(ndjson));
    }

    public static String toNdjson(List<ChatMessage> messages) throws IOException {
        return ChatMessage.toNdjson(messages);
    }

    public static int countMessages(String ndjson) throws IOException {
        return loadMessages(ndjson).size();
    }

    public static String getLastUserMessage(String ndjson) throws IOException {
        List<ChatMessage> messages = loadMessages(ndjson);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatRole.USER) {
                return message.getContent();
            }
        }

        return null;
    }

    public static String removeLastTurn(String ndjson) throws IOException {
        List<ChatMessage> messages = loadMessages(ndjson);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatRole.SYSTEM) {
                continue;
            }

            messages.remove(i);
            if (message.getRole() == ChatRole.USER) {
                break;
            }
        }

        return toNdjson(messages);
    }
}
