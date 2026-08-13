package com.mailagent.mail;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MockMailChannelTest {

    private Msg msg(String id) {
        return new Msg(id, "ivan@example.com", "subject-" + id, "body-" + id, Instant.parse("2026-08-13T10:00:00Z"));
    }

    @Test
    public void fetchUnreadReturnsSeededMessages() {
        List<Msg> seeded = Arrays.asList(msg("1"), msg("2"));
        MailChannel channel = new MockMailChannel(seeded);

        List<Msg> result = channel.fetchUnread();

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getId());
        assertEquals("2", result.get(1).getId());
    }

    @Test
    public void replyRecordsSentReplyAgainstOriginalMessage() {
        Msg original = msg("1");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(original));

        channel.reply(original, "Готово, напоминание добавлено на завтра.");

        assertEquals(1, channel.repliesSent().size());
        assertEquals(original, channel.repliesSent().get(0).to);
        assertEquals("Готово, напоминание добавлено на завтра.", channel.repliesSent().get(0).body);
    }

    @Test
    public void replyDoesNotRemoveMessageFromUnread() {
        Msg original = msg("1");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(original));

        channel.reply(original, "ok");

        assertTrue(channel.fetchUnread().contains(original));
    }
}
