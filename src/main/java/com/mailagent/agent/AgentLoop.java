package com.mailagent.agent;

import com.mailagent.llm.ChatMessage;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.LlmClient;
import com.mailagent.llm.ToolCall;
import com.mailagent.llm.ToolSpec;
import com.mailagent.tools.Tool;
import com.mailagent.tools.ToolExecutionException;
import com.mailagent.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Drives the LLM tool-calling loop: ask the model, execute whatever tools
 * it requests, feed results back, repeat until it returns a final answer
 * or {@code maxSteps} is exhausted. A malformed/unknown tool_call is never
 * allowed to crash the loop — it's turned into an error tool-result
 * message so the model can recover.
 */
public class AgentLoop {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;

    public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, int maxSteps) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
    }

    public String run(List<ChatMessage> initialMessages) {
        return run(initialMessages, toolName -> {
        });
    }

    /**
     * @param onToolCall notified with the tool's name right before each
     *                   execution — lets callers audit-log tool usage
     *                   without AgentLoop needing to know about AuditLog.
     */
    public String run(List<ChatMessage> initialMessages, Consumer<String> onToolCall) {
        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        List<ToolSpec> specs = toolRegistry.specs();

        for (int step = 0; step < maxSteps; step++) {
            ChatResponse response = llmClient.chat(messages, specs);

            if (!response.hasToolCalls()) {
                return response.getContent();
            }

            messages.add(ChatMessage.assistantToolCalls(response.getToolCalls()));
            for (ToolCall call : response.getToolCalls()) {
                onToolCall.accept(call.getName());
                String result = executeSafely(call);
                messages.add(ChatMessage.tool(call.getId(), result));
            }
        }

        throw new AgentLoopException("Достигнут лимит шагов (maxSteps=" + maxSteps + ") без финального ответа модели");
    }

    private String executeSafely(ToolCall call) {
        Optional<Tool> tool = toolRegistry.find(call.getName());
        if (!tool.isPresent()) {
            return errorJson("Unknown tool: " + call.getName());
        }
        try {
            return tool.get().execute(call.getArgumentsJson());
        } catch (ToolExecutionException e) {
            return errorJson(e.getMessage());
        } catch (RuntimeException e) {
            return errorJson("Unexpected tool error in '" + call.getName() + "': " + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        String escaped = message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"error\":\"" + escaped + "\"}";
    }
}
