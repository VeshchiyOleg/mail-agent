package com.mailagent.tools;

public interface Tool {

    String name();

    String description();

    String parametersJsonSchema();

    /**
     * @throws ToolExecutionException on malformed/invalid arguments — never
     *         a raw runtime exception, so the caller (the tool-loop) can
     *         turn this into a structured error reply to the model instead
     *         of crashing.
     */
    String execute(String argumentsJson);
}
