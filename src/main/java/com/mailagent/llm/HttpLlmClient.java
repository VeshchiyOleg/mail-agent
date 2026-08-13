package com.mailagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LlmClient over the Anthropic Messages API (POST {endpoint}/v1/messages,
 * x-api-key / anthropic-version headers) — confirmed against the proxy
 * endpoint provided for this assignment. System messages are pulled out
 * into the top-level "system" field (Anthropic doesn't accept a "system"
 * role inside the messages array); tool results are represented as a
 * user-role message with a tool_result content block, per that API's
 * tool-use convention.
 */
public class HttpLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    private final OkHttpClient httpClient;
    private final String endpoint;
    private final String model;
    private final String apiKey;

    public HttpLlmClient(String endpoint, String model, String apiKey, long timeoutMs) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        Request request = new Request.Builder()
                .url(endpoint + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .post(RequestBody.create(buildRequestBody(messages, tools), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseText = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new LlmClientException("LLM HTTP " + response.code() + ": " + responseText);
            }
            return parseResponse(responseText);
        } catch (IOException e) {
            throw new LlmClientException("LLM request failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS);

        StringBuilder system = new StringBuilder();
        ArrayNode messagesNode = MAPPER.createArrayNode();
        for (ChatMessage m : messages) {
            switch (m.getRole()) {
                case SYSTEM:
                    if (system.length() > 0) {
                        system.append('\n');
                    }
                    system.append(m.getContent());
                    break;
                case USER:
                    messagesNode.add(userTextMessage(m.getContent()));
                    break;
                case ASSISTANT:
                    messagesNode.add(assistantMessage(m));
                    break;
                case TOOL:
                    messagesNode.add(toolResultMessage(m));
                    break;
                default:
                    throw new LlmClientException("Unsupported message role: " + m.getRole());
            }
        }
        if (system.length() > 0) {
            root.put("system", system.toString());
        }
        root.set("messages", messagesNode);

        if (tools != null && !tools.isEmpty()) {
            root.set("tools", toolsNode(tools));
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new LlmClientException("Failed to serialize LLM request", e);
        }
    }

    private ArrayNode toolsNode(List<ToolSpec> tools) {
        ArrayNode toolsNode = MAPPER.createArrayNode();
        for (ToolSpec spec : tools) {
            ObjectNode toolNode = MAPPER.createObjectNode();
            toolNode.put("name", spec.getName());
            toolNode.put("description", spec.getDescription());
            toolNode.set("input_schema", parseJson(spec.getParametersJsonSchema()));
            toolsNode.add(toolNode);
        }
        return toolsNode;
    }

    private ObjectNode userTextMessage(String text) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", "user");
        node.put("content", text);
        return node;
    }

    private ObjectNode assistantMessage(ChatMessage m) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", "assistant");
        if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
            ArrayNode content = MAPPER.createArrayNode();
            for (ToolCall call : m.getToolCalls()) {
                ObjectNode block = MAPPER.createObjectNode();
                block.put("type", "tool_use");
                block.put("id", call.getId());
                block.put("name", call.getName());
                block.set("input", parseJson(call.getArgumentsJson()));
                content.add(block);
            }
            node.set("content", content);
        } else {
            node.put("content", m.getContent());
        }
        return node;
    }

    private ObjectNode toolResultMessage(ChatMessage m) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", "user");
        ArrayNode content = MAPPER.createArrayNode();
        ObjectNode block = MAPPER.createObjectNode();
        block.put("type", "tool_result");
        block.put("tool_use_id", m.getToolCallId());
        block.put("content", m.getContent());
        content.add(block);
        node.set("content", content);
        return node;
    }

    private JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json == null || json.isEmpty() ? "{}" : json);
        } catch (IOException e) {
            throw new LlmClientException("Invalid JSON: " + json, e);
        }
    }

    private ChatResponse parseResponse(String responseBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new LlmClientException("Invalid LLM response JSON", e);
        }

        JsonNode content = root.get("content");
        if (content == null || !content.isArray()) {
            throw new LlmClientException("LLM response missing 'content' array");
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("tool_use".equals(type)) {
                String id = block.path("id").asText();
                String name = block.path("name").asText();
                JsonNode input = block.get("input");
                toolCalls.add(new ToolCall(id, name, input == null ? "{}" : input.toString()));
            } else if ("text".equals(type)) {
                text.append(block.path("text").asText(""));
            }
        }

        if (!toolCalls.isEmpty()) {
            return ChatResponse.toolCalls(toolCalls);
        }
        return ChatResponse.text(text.toString());
    }
}
