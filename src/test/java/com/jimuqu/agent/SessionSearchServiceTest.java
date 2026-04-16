package com.jimuqu.agent;

import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.model.SessionSearchEntry;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionSearchServiceTest {
    @Test
    void shouldListRecentSessionsAndExcludeCurrentSession() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        SessionRecord current = env.sessionRepository.bindNewSession("MEMORY:current-room:user");
        current.setTitle("current session");
        current.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser("current"),
                ChatMessage.ofAssistant("current reply")
        )));
        env.sessionRepository.save(current);

        SessionRecord previous = env.sessionRepository.bindNewSession("MEMORY:history-room:user");
        previous.setTitle("history session");
        previous.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser("older"),
                ChatMessage.ofAssistant("older reply")
        )));
        env.sessionRepository.save(previous);

        List<SessionSearchEntry> entries = env.sessionSearchService.search("MEMORY:current-room:user", "", 3);

        assertThat(entries).extracting(SessionSearchEntry::getSessionId).doesNotContain(current.getSessionId());
        assertThat(entries).extracting(SessionSearchEntry::getTitle).contains("history session");
    }

    @Test
    void shouldFoldDelegatedChildSessionsIntoParentAndAvoidPersistingSearchSummary() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        SessionRecord current = env.sessionRepository.bindNewSession("MEMORY:current-room:user");
        current.setTitle("current");
        current.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser("current"),
                ChatMessage.ofAssistant("current reply")
        )));
        env.sessionRepository.save(current);

        SessionRecord parent = env.sessionRepository.bindNewSession("MEMORY:history-room:user");
        parent.setTitle("parent session");
        parent.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser("setup context"),
                ChatMessage.ofAssistant("setup done")
        )));
        env.sessionRepository.save(parent);

        SessionRecord child = env.sessionRepository.cloneSession("MEMORY:history-room:user", parent.getSessionId(), "delegate");
        child.setNdjson(MessageSupport.toNdjson(Arrays.asList(
                ChatMessage.ofUser("investigate bug-123"),
                ChatMessage.ofAssistant("fixed bug-123 with file update")
        )));
        env.sessionRepository.save(child);

        String parentNdjson = env.sessionRepository.findById(parent.getSessionId()).getNdjson();
        String childNdjson = env.sessionRepository.findById(child.getSessionId()).getNdjson();

        List<SessionSearchEntry> entries = env.sessionSearchService.search("MEMORY:current-room:user", "bug-123", 3);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getSessionId()).isEqualTo(parent.getSessionId());
        assertThat(entries.get(0).getSummary()).isNotBlank();
        assertThat(env.sessionRepository.findById(parent.getSessionId()).getNdjson()).isEqualTo(parentNdjson);
        assertThat(env.sessionRepository.findById(child.getSessionId()).getNdjson()).isEqualTo(childNdjson);
    }
}
