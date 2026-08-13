package com.mailagent.service;

import com.mailagent.agent.AgentLoop;
import com.mailagent.audit.AuditLog;
import com.mailagent.llm.ChatMessage;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.LlmClient;
import com.mailagent.llm.MockLlmClient;
import com.mailagent.llm.ToolCall;
import com.mailagent.llm.ToolSpec;
import com.mailagent.mail.MockMailChannel;
import com.mailagent.mail.Msg;
import com.mailagent.store.ReminderStore;
import com.mailagent.store.SeenStore;
import com.mailagent.tools.AddReminderTool;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.FindItemsTool;
import com.mailagent.tools.ToolRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MailAgentServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

    private ToolRegistry registryFor(ReminderStore reminderStore) {
        return new ToolRegistry()
                .register(new CurrentDatetimeTool(FIXED_CLOCK))
                .register(new AddReminderTool(reminderStore, FIXED_CLOCK))
                .register(new FindItemsTool(reminderStore));
    }

    private ReminderStore reminderStore() throws IOException {
        return new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
    }

    private SeenStore seenStore() throws IOException {
        return new SeenStore(tmp.getRoot().toPath().resolve("seen.jsonl"));
    }

    private AuditLog auditLog() throws IOException {
        return new AuditLog(tmp.getRoot().toPath().resolve("audit.jsonl"), FIXED_CLOCK);
    }

    private Msg msg(String id, String body) {
        return new Msg(id, "ivan@example.com", "subject-" + id, body, Instant.parse("2026-08-13T08:55:00Z"));
    }

    @Test
    public void goldenCase_addReminder() throws IOException {
        ReminderStore reminderStore = reminderStore();
        MockMailChannel channel = new MockMailChannel(
                Collections.singletonList(msg("msg-1", "Напомни завтра в 10 позвонить Ивану")));
        SeenStore seenStore = seenStore();
        AuditLog auditLog = auditLog();

        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(
                        new ToolCall("call-1", "add_reminder", "{\"text\":\"позвонить Ивану\",\"dueIso\":\"2026-08-14T10:00:00Z\"}"))),
                ChatResponse.text("Напоминание добавлено: позвонить Ивану, срок 2026-08-14T10:00:00Z.")
        ));
        AgentLoop agentLoop = new AgentLoop(llm, registryFor(reminderStore), 6);

        MailAgentService service = new MailAgentService(channel, seenStore, agentLoop, auditLog);
        service.processUnread();

        assertEquals(1, channel.repliesSent().size());
        assertTrue(channel.repliesSent().get(0).body.contains("Ивану"));
        assertEquals(1, reminderStore.findAll().size());
        assertTrue(seenStore.isSeen("msg-1"));
        assertTrue(auditLog.verifyChain());
    }

    @Test
    public void goldenCase_currentDatetime() throws IOException {
        ReminderStore reminderStore = reminderStore();
        MockMailChannel channel = new MockMailChannel(
                Collections.singletonList(msg("msg-1", "Какое сегодня число?")));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall("call-1", "current_datetime", "{}"))),
                ChatResponse.text("Сегодня 2026-08-13.")
        ));
        AgentLoop agentLoop = new AgentLoop(llm, registryFor(reminderStore), 6);
        MailAgentService service = new MailAgentService(channel, seenStore(), agentLoop, auditLog());

        service.processUnread();

        assertEquals(1, channel.repliesSent().size());
        assertEquals("Сегодня 2026-08-13.", channel.repliesSent().get(0).body);
    }

    @Test
    public void goldenCase_garbageEmail_noToolCallsNoExtraActions() throws IOException {
        ReminderStore reminderStore = reminderStore();
        MockMailChannel channel = new MockMailChannel(
                Collections.singletonList(msg("msg-1", "asdkjaskjdqwe??")));
        MockLlmClient llm = new MockLlmClient(Collections.singletonList(
                ChatResponse.text("Извините, не понял ваш запрос.")
        ));
        AgentLoop agentLoop = new AgentLoop(llm, registryFor(reminderStore), 6);
        MailAgentService service = new MailAgentService(channel, seenStore(), agentLoop, auditLog());

        service.processUnread();

        assertEquals(1, channel.repliesSent().size());
        assertEquals("Извините, не понял ваш запрос.", channel.repliesSent().get(0).body);
        assertTrue(reminderStore.findAll().isEmpty());
        assertEquals(1, llm.callHistory().size());
    }

    @Test
    public void secondProcessUnreadDoesNotReprocessAlreadySeenMessage() throws IOException {
        ReminderStore reminderStore = reminderStore();
        MockMailChannel channel = new MockMailChannel(
                Collections.singletonList(msg("msg-1", "Какое сегодня число?")));
        // Script has exactly enough responses for ONE pass; a second LLM
        // round for the same message would exhaust it and throw.
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall("call-1", "current_datetime", "{}"))),
                ChatResponse.text("Сегодня 2026-08-13.")
        ));
        AgentLoop agentLoop = new AgentLoop(llm, registryFor(reminderStore), 6);
        MailAgentService service = new MailAgentService(channel, seenStore(), agentLoop, auditLog());

        service.processUnread();
        service.processUnread();

        assertEquals(1, channel.repliesSent().size());
        assertEquals(2, llm.callHistory().size());
    }

    @Test
    public void llmFailureProducesGracefulFallback_noStacktraceLeaked() throws IOException {
        ReminderStore reminderStore = reminderStore();
        MockMailChannel channel = new MockMailChannel(
                Collections.singletonList(msg("msg-1", "Какое сегодня число?")));
        SeenStore seenStore = seenStore();
        AuditLog auditLog = auditLog();

        LlmClient failingLlm = (messages, tools) -> {
            throw new RuntimeException("connect timed out");
        };
        AgentLoop agentLoop = new AgentLoop(failingLlm, registryFor(reminderStore), 6);
        MailAgentService service = new MailAgentService(channel, seenStore, agentLoop, auditLog);

        service.processUnread();

        assertEquals(1, channel.repliesSent().size());
        String replyBody = channel.repliesSent().get(0).body;
        assertEquals(MailAgentService.FALLBACK_REPLY, replyBody);
        assertFalse(replyBody.contains("RuntimeException"));
        assertFalse(replyBody.contains("connect timed out"));
        assertTrue(seenStore.isSeen("msg-1"));
        assertTrue(auditLog.verifyChain());
    }
}
