package com.mailagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpLlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;

    @Before
    public void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void stopServer() throws IOException {
        server.shutdown();
    }

    private HttpLlmClient client() {
        return new HttpLlmClient(server.url("/api/llm").toString(), "claude-sonnet-5", "test-key", 5000);
    }

    @Test
    public void parsesFinalTextResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"type\":\"message\",\"role\":\"assistant\",\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"Сегодня 2026-08-13.\"}],\"stop_reason\":\"end_turn\"}"));

        ChatResponse response = client().chat(
                Collections.singletonList(ChatMessage.user("Какое сегодня число?")),
                Collections.emptyList()
        );

        assertFalse(response.hasToolCalls());
        assertEquals("Сегодня 2026-08-13.", response.getContent());
    }

    @Test
    public void parsesToolUseResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"type\":\"message\",\"role\":\"assistant\",\"content\":"
                        + "[{\"type\":\"tool_use\",\"id\":\"call-1\",\"name\":\"current_datetime\",\"input\":{}}],"
                        + "\"stop_reason\":\"tool_use\"}"));

        ChatResponse response = client().chat(
                Collections.singletonList(ChatMessage.user("Какое сегодня число?")),
                Collections.singletonList(new ToolSpec("current_datetime", "desc", "{\"type\":\"object\",\"properties\":{}}"))
        );

        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("current_datetime", response.getToolCalls().get(0).getName());
        assertEquals("call-1", response.getToolCalls().get(0).getId());
    }

    @Test
    public void sendsAuthHeadersAndAnthropicVersion() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        client().chat(Collections.singletonList(ChatMessage.user("hi")), Collections.emptyList());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/api/llm/v1/messages", recorded.getPath());
        assertEquals("test-key", recorded.getHeader("x-api-key"));
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"));
    }

    @Test
    public void extractsSystemMessageIntoTopLevelFieldNotIntoMessagesArray() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        client().chat(
                Arrays.asList(ChatMessage.system("Ты — ассистент."), ChatMessage.user("hi")),
                Collections.emptyList()
        );

        JsonNode body = MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("Ты — ассистент.", body.get("system").asText());
        assertEquals(1, body.get("messages").size());
        assertEquals("user", body.get("messages").get(0).get("role").asText());
    }

    @Test
    public void mapsToolResultMessageToUserRoleToolResultBlock() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.user("Какое сегодня число?"),
                ChatMessage.assistantToolCalls(Collections.singletonList(new ToolCall("call-1", "current_datetime", "{}"))),
                ChatMessage.tool("call-1", "2026-08-13T10:15:30Z")
        );

        client().chat(messages, Collections.emptyList());

        JsonNode body = MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        JsonNode lastMessage = body.get("messages").get(body.get("messages").size() - 1);
        assertEquals("user", lastMessage.get("role").asText());
        JsonNode toolResultBlock = lastMessage.get("content").get(0);
        assertEquals("tool_result", toolResultBlock.get("type").asText());
        assertEquals("call-1", toolResultBlock.get("tool_use_id").asText());
        assertEquals("2026-08-13T10:15:30Z", toolResultBlock.get("content").asText());
    }

    @Test(expected = LlmClientException.class)
    public void throwsOnNon2xxStatus() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"unavailable\"}}"));

        client().chat(Collections.singletonList(ChatMessage.user("hi")), Collections.emptyList());
    }
}
