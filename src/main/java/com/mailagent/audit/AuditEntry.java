package com.mailagent.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public final class AuditEntry {

    private final long seq;
    private final String timestampIso;
    private final String event;
    private final Map<String, String> details;
    private final String prevHash;
    private final String hash;

    @JsonCreator
    public AuditEntry(
            @JsonProperty("seq") long seq,
            @JsonProperty("timestampIso") String timestampIso,
            @JsonProperty("event") String event,
            @JsonProperty("details") Map<String, String> details,
            @JsonProperty("prevHash") String prevHash,
            @JsonProperty("hash") String hash) {
        this.seq = seq;
        this.timestampIso = timestampIso;
        this.event = event;
        this.details = details;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public long getSeq() {
        return seq;
    }

    public String getTimestampIso() {
        return timestampIso;
    }

    public String getEvent() {
        return event;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}
