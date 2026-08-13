package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;

import java.time.Clock;
import java.util.UUID;

public class AddReminderTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReminderStore store;
    private final Clock clock;

    public AddReminderTool(ReminderStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "add_reminder";
    }

    @Override
    public String description() {
        return "Добавляет напоминание с текстом и сроком (dueIso, ISO-8601).";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\"},"
                + "\"dueIso\":{\"type\":\"string\"}"
                + "},\"required\":[\"text\",\"dueIso\"]}";
    }

    @Override
    public String execute(String argumentsJson) {
        JsonNode args = parse(argumentsJson);

        String text = textOf(args, "text");
        String dueIso = textOf(args, "dueIso");

        String id = UUID.randomUUID().toString();
        String createdAtIso = clock.instant().toString();
        store.add(new Reminder(id, text, dueIso, createdAtIso));

        return "Напоминание добавлено: \"" + text + "\" на " + dueIso + ".";
    }

    private static JsonNode parse(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("add_reminder: невалидный JSON аргументов", e);
        }
    }

    private static String textOf(JsonNode args, String field) {
        JsonNode node = args.get(field);
        if (node == null || node.asText("").trim().isEmpty()) {
            throw new ToolExecutionException("add_reminder: отсутствует обязательное поле '" + field + "'");
        }
        return node.asText();
    }
}
