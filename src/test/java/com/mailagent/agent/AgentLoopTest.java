package com.mailagent.agent;

import com.mailagent.llm.ChatMessage;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.MockLlmClient;
import com.mailagent.llm.ToolCall;
import com.mailagent.store.ReminderStore;
import com.mailagent.tools.AddReminderTool;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.ToolRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentLoopTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-13T10:15:30Z"), ZoneOffset.UTC);

    private List<ChatMessage> userAsks(String text) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(text));
        return messages;
    }

    @Test
    public void happyPath_toolCallThenFinalAnswer() {
        ToolRegistry registry = new ToolRegistry().register(new CurrentDatetimeTool(FIXED_CLOCK));

        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall("call-1", "current_datetime", "{}"))),
                ChatResponse.text("Сегодня 2026-08-13.")
        ));

        AgentLoop loop = new AgentLoop(llm, registry, 6);

        String result = loop.run(userAsks("Какое сегодня число?"));

        assertEquals("Сегодня 2026-08-13.", result);
        assertEquals(2, llm.callHistory().size());

        List<ChatMessage> secondCallMessages = llm.callHistory().get(1);
        ChatMessage toolResult = secondCallMessages.get(secondCallMessages.size() - 1);
        assertEquals(ChatMessage.Role.TOOL, toolResult.getRole());
        assertEquals("2026-08-13T10:15:30Z", toolResult.getContent());
    }

    @Test
    public void unknownToolDoesNotCrashLoop_reportsErrorBackToModel() {
        ToolRegistry registry = new ToolRegistry().register(new CurrentDatetimeTool(FIXED_CLOCK));

        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall("call-1", "delete_universe", "{}"))),
                ChatResponse.text("Не могу выполнить это действие.")
        ));

        AgentLoop loop = new AgentLoop(llm, registry, 6);

        String result = loop.run(userAsks("удали всё"));

        assertEquals("Не могу выполнить это действие.", result);
        assertEquals(2, llm.callHistory().size());

        List<ChatMessage> secondCallMessages = llm.callHistory().get(1);
        ChatMessage toolResult = secondCallMessages.get(secondCallMessages.size() - 1);
        assertEquals(ChatMessage.Role.TOOL, toolResult.getRole());
        assertTrue(toolResult.getContent().contains("delete_universe"));
    }

    @Test
    public void malformedToolArgumentsDoNotCrashLoop_reportsErrorBackToModel() throws IOException {
        ReminderStore store = new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
        ToolRegistry registry = new ToolRegistry().register(new AddReminderTool(store, FIXED_CLOCK));

        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall("call-1", "add_reminder", "{}"))),
                ChatResponse.text("Не понял, что напомнить.")
        ));

        AgentLoop loop = new AgentLoop(llm, registry, 6);

        String result = loop.run(userAsks("напомни мне"));

        assertEquals("Не понял, что напомнить.", result);
        assertTrue(store.findAll().isEmpty());

        List<ChatMessage> secondCallMessages = llm.callHistory().get(1);
        ChatMessage toolResult = secondCallMessages.get(secondCallMessages.size() - 1);
        assertEquals(ChatMessage.Role.TOOL, toolResult.getRole());
        assertTrue(toolResult.getContent().contains("text"));
    }

    @Test(expected = AgentLoopException.class)
    public void stopsAtMaxStepsWithoutFinalAnswer() {
        ToolRegistry registry = new ToolRegistry().register(new CurrentDatetimeTool(FIXED_CLOCK));

        ChatResponse alwaysToolCall = ChatResponse.toolCalls(
                Collections.singletonList(new ToolCall("call-1", "current_datetime", "{}"))
        );
        MockLlmClient llm = new MockLlmClient(Arrays.asList(alwaysToolCall, alwaysToolCall, alwaysToolCall));

        AgentLoop loop = new AgentLoop(llm, registry, 3);

        try {
            loop.run(userAsks("зациклись"));
        } finally {
            assertEquals(3, llm.callHistory().size());
        }
    }

    @Test
    public void runWithOnToolCallInvokesCallbackWithToolNameForEachCall() {
        ToolRegistry registry = new ToolRegistry().register(new CurrentDatetimeTool(FIXED_CLOCK));

        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall("call-1", "current_datetime", "{}"))),
                ChatResponse.text("Сегодня 2026-08-13.")
        ));

        AgentLoop loop = new AgentLoop(llm, registry, 6);
        List<String> calledTools = new ArrayList<>();
        Consumer<String> onToolCall = calledTools::add;

        String result = loop.run(userAsks("Какое сегодня число?"), onToolCall);

        assertEquals("Сегодня 2026-08-13.", result);
        assertEquals(Collections.singletonList("current_datetime"), calledTools);
    }
}
