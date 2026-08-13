package com.mailagent.mail;

import java.time.Instant;
import java.util.Objects;

public final class Msg {

    private final String id;
    private final String from;
    private final String subject;
    private final String body;
    private final Instant receivedAt;

    public Msg(String id, String from, String subject, String body, Instant receivedAt) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.receivedAt = receivedAt;
    }

    public String getId() {
        return id;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Msg)) return false;
        Msg msg = (Msg) o;
        return Objects.equals(id, msg.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
