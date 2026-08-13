package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;

import java.util.List;

public class FindItemsTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReminderStore store;

    public FindItemsTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "find_items";
    }

    @Override
    public String description() {
        return "Ищет напоминания по подстроке в тексте (query); пустой query — вернуть все.";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}";
    }

    @Override
    public String execute(String argumentsJson) {
        JsonNode args = parse(argumentsJson);
        JsonNode queryNode = args.get("query");
        String query = queryNode == null ? null : queryNode.asText(null);

        List<Reminder> found = (query == null || query.trim().isEmpty())
                ? store.findAll()
                : store.findByText(query);

        try {
            return MAPPER.writeValueAsString(found);
        } catch (Exception e) {
            throw new ToolExecutionException("find_items: не удалось сериализовать результат", e);
        }
    }

    private static JsonNode parse(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("find_items: невалидный JSON аргументов", e);
        }
    }
}
