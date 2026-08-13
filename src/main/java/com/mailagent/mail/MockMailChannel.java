package com.mailagent.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MockMailChannel implements MailChannel {

    private final List<Msg> unread;
    private final List<Reply> repliesSent = new ArrayList<>();

    public MockMailChannel(List<Msg> unread) {
        this.unread = new ArrayList<>(unread);
    }

    public MockMailChannel() {
        this(Collections.emptyList());
    }

    @Override
    public List<Msg> fetchUnread() {
        return Collections.unmodifiableList(unread);
    }

    @Override
    public void reply(Msg msg, String body) {
        repliesSent.add(new Reply(msg, body));
    }

    public List<Reply> repliesSent() {
        return Collections.unmodifiableList(repliesSent);
    }

    public static final class Reply {
        public final Msg to;
        public final String body;

        Reply(Msg to, String body) {
            this.to = to;
            this.body = body;
        }
    }
}
