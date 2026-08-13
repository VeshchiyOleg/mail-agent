package com.mailagent.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MockLlmClient implements LlmClient {

    private final Deque<ChatResponse> script;
    private final List<List<ChatMessage>> callHistory = new ArrayList<>();

    public MockLlmClient(List<ChatResponse> script) {
        this.script = new ArrayDeque<>(script);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        callHistory.add(new ArrayList<>(messages));
        if (script.isEmpty()) {
            throw new IllegalStateException("MockLlmClient script exhausted after " + callHistory.size() + " call(s)");
        }
        return script.poll();
    }

    public List<List<ChatMessage>> callHistory() {
        return callHistory;
    }
}
