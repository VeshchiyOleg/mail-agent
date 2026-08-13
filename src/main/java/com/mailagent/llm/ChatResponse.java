package com.mailagent.llm;

import java.util.Collections;
import java.util.List;

public final class ChatResponse {

    private final String content;
    private final List<ToolCall> toolCalls;

    private ChatResponse(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public static ChatResponse text(String content) {
        return new ChatResponse(content, Collections.emptyList());
    }

    public static ChatResponse toolCalls(List<ToolCall> toolCalls) {
        return new ChatResponse(null, toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
}
