package com.mailagent.llm;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MockLlmClientTest {

    @Test
    public void returnsScriptedResponsesInOrder_toolCallThenFinal() {
        ChatResponse toolCallTurn = ChatResponse.toolCalls(Collections.singletonList(
                new ToolCall("call-1", "current_datetime", "{}")
        ));
        ChatResponse finalTurn = ChatResponse.text("Сегодня 2026-08-13.");

        MockLlmClient client = new MockLlmClient(Arrays.asList(toolCallTurn, finalTurn));

        ChatResponse first = client.chat(
                Collections.singletonList(ChatMessage.user("Какое сегодня число?")),
                Collections.emptyList()
        );
        assertTrue(first.hasToolCalls());
        assertEquals("current_datetime", first.getToolCalls().get(0).getName());

        ChatResponse second = client.chat(
                Arrays.asList(
                        ChatMessage.user("Какое сегодня число?"),
                        ChatMessage.assistantToolCalls(first.getToolCalls()),
                        ChatMessage.tool("call-1", "2026-08-13T00:00:00Z")
                ),
                Collections.emptyList()
        );
        assertFalse(second.hasToolCalls());
        assertEquals("Сегодня 2026-08-13.", second.getContent());
    }

    @Test
    public void recordsCallHistoryForAssertions() {
        MockLlmClient client = new MockLlmClient(Collections.singletonList(ChatResponse.text("ok")));

        List<ChatMessage> messages = Collections.singletonList(ChatMessage.user("hi"));
        client.chat(messages, Collections.emptyList());

        assertEquals(1, client.callHistory().size());
        assertEquals(1, client.callHistory().get(0).size());
        assertEquals("hi", client.callHistory().get(0).get(0).getContent());
    }

    @Test(expected = IllegalStateException.class)
    public void throwsWhenScriptExhausted() {
        MockLlmClient client = new MockLlmClient(Collections.singletonList(ChatResponse.text("only one")));

        client.chat(Collections.emptyList(), Collections.emptyList());
        client.chat(Collections.emptyList(), Collections.emptyList());
    }
}
