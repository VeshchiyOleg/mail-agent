package com.mailagent.llm;

import java.util.List;

public interface LlmClient {

    ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools);
}
