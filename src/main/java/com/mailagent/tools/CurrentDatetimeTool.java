package com.mailagent.tools;

import java.time.Clock;

public class CurrentDatetimeTool implements Tool {

    private final Clock clock;

    public CurrentDatetimeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "current_datetime";
    }

    @Override
    public String description() {
        return "Возвращает текущую дату и время в формате ISO-8601 (UTC).";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(String argumentsJson) {
        return clock.instant().toString();
    }
}
