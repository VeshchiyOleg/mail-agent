package com.mailagent.tools;

import com.mailagent.llm.ToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolSpec> specs() {
        List<ToolSpec> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            result.add(new ToolSpec(tool.name(), tool.description(), tool.parametersJsonSchema()));
        }
        return result;
    }
}
