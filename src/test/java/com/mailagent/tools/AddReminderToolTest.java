package com.mailagent.tools;

import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AddReminderToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ReminderStore store() throws IOException {
        return new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
    }

    @Test
    public void addsReminderAndReturnsConfirmation() throws IOException {
        ReminderStore store = store();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);
        AddReminderTool tool = new AddReminderTool(store, clock);

        String result = tool.execute("{\"text\":\"позвонить Ивану\",\"dueIso\":\"2026-08-14T10:00:00Z\"}");

        assertTrue(result.contains("позвонить Ивану"));
        assertTrue(result.contains("2026-08-14T10:00:00Z"));

        List<Reminder> all = store.findAll();
        assertEquals(1, all.size());
        assertEquals("позвонить Ивану", all.get(0).getText());
        assertEquals("2026-08-14T10:00:00Z", all.get(0).getDueIso());
        assertEquals("2026-08-13T09:00:00Z", all.get(0).getCreatedAtIso());
    }

    @Test(expected = ToolExecutionException.class)
    public void throwsOnMalformedJson() throws IOException {
        AddReminderTool tool = new AddReminderTool(store(), Clock.systemUTC());

        tool.execute("not json");
    }

    @Test(expected = ToolExecutionException.class)
    public void throwsWhenTextMissing() throws IOException {
        AddReminderTool tool = new AddReminderTool(store(), Clock.systemUTC());

        tool.execute("{\"dueIso\":\"2026-08-14T10:00:00Z\"}");
    }

    @Test(expected = ToolExecutionException.class)
    public void throwsWhenDueIsoMissing() throws IOException {
        AddReminderTool tool = new AddReminderTool(store(), Clock.systemUTC());

        tool.execute("{\"text\":\"позвонить Ивану\"}");
    }
}
