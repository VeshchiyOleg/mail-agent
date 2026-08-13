package com.mailagent.llm;

public final class ToolSpec {

    private final String name;
    private final String description;
    private final String parametersJsonSchema;

    public ToolSpec(String name, String description, String parametersJsonSchema) {
        this.name = name;
        this.description = description;
        this.parametersJsonSchema = parametersJsonSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParametersJsonSchema() {
        return parametersJsonSchema;
    }
}
