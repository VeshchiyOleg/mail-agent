package com.mailagent.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Reminder {

    private final String id;
    private final String text;
    private final String dueIso;
    private final String createdAtIso;

    @JsonCreator
    public Reminder(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("dueIso") String dueIso,
            @JsonProperty("createdAtIso") String createdAtIso) {
        this.id = id;
        this.text = text;
        this.dueIso = dueIso;
        this.createdAtIso = createdAtIso;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getDueIso() {
        return dueIso;
    }

    public String getCreatedAtIso() {
        return createdAtIso;
    }
}
