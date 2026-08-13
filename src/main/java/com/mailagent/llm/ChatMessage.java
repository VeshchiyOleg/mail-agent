package com.mailagent.llm;

import java.util.Collections;
import java.util.List;

public final class ChatMessage {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    private final Role role;
    private final String content;
    private final String toolCallId;
    private final List<ToolCall> toolCalls;

    private ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, Collections.emptyList());
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, Collections.emptyList());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, Collections.emptyList());
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, null, null, toolCalls);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, toolCallId, Collections.emptyList());
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
}
