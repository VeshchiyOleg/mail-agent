package com.mailagent.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mailagent.agent.AgentLoop;
import com.mailagent.audit.AuditLog;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.MockLlmClient;
import com.mailagent.mail.MockMailChannel;
import com.mailagent.mail.Msg;
import com.mailagent.store.ReminderStore;
import com.mailagent.store.SeenStore;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.ToolRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertFalse;

/**
 * Verifies the PII rule from the spec (§3.7): message bodies must never
 * end up in structured logs or in the audit journal — only opaque ids and
 * tool names.
 */
public class MailAgentServiceLoggingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);
    private static final String SENSITIVE_BODY =
            "Напомни позвонить Ивану Петрову по номеру +7-900-123-45-67, детали проекта X секретны.";

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @Before
    public void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
    }

    @After
    public void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    public void neitherLogsNorAuditFileContainMessageBody() throws IOException {
        ReminderStore reminderStore = new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
        MockMailChannel channel = new MockMailChannel(Collections.singletonList(
                new Msg("msg-1", "ivan@example.com", "subj", SENSITIVE_BODY, Instant.parse("2026-08-13T08:55:00Z"))
        ));
        SeenStore seenStore = new SeenStore(tmp.getRoot().toPath().resolve("seen.jsonl"));
        Path auditFile = tmp.getRoot().toPath().resolve("audit.jsonl");
        AuditLog auditLog = new AuditLog(auditFile, FIXED_CLOCK);

        MockLlmClient llm = new MockLlmClient(Collections.singletonList(ChatResponse.text("Хорошо, напомню.")));
        ToolRegistry registry = new ToolRegistry().register(new CurrentDatetimeTool(FIXED_CLOCK));
        AgentLoop agentLoop = new AgentLoop(llm, registry, 6);

        MailAgentService service = new MailAgentService(channel, seenStore, agentLoop, auditLog);
        service.processUnread();

        for (ILoggingEvent event : appender.list) {
            String formatted = event.getFormattedMessage();
            assertFalse("log line leaked message body: " + formatted, formatted.contains(SENSITIVE_BODY));
            assertFalse("log line leaked a name from the body: " + formatted, formatted.contains("Ивану Петрову"));
            assertFalse("log line leaked a phone number from the body: " + formatted, formatted.contains("+7-900-123-45-67"));
        }

        String auditFileContent = new String(Files.readAllBytes(auditFile), StandardCharsets.UTF_8);
        assertFalse(auditFileContent.contains(SENSITIVE_BODY));
        assertFalse(auditFileContent.contains("Ивану Петрову"));
        assertFalse(auditFileContent.contains("+7-900-123-45-67"));
    }
}
