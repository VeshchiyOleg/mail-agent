package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class FindItemsToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReminderStore store() throws IOException {
        return new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
    }

    @Test
    public void returnsEmptyJsonArrayWhenNoReminders() throws IOException {
        FindItemsTool tool = new FindItemsTool(store());

        String result = tool.execute("{}");

        JsonNode parsed = MAPPER.readTree(result);
        assertEquals(0, parsed.size());
    }

    @Test
    public void returnsAllRemindersWhenQueryEmpty() throws IOException {
        ReminderStore store = store();
        store.add(new Reminder("r1", "позвонить Ивану", "2026-08-14T10:00:00Z", "2026-08-13T09:00:00Z"));
        store.add(new Reminder("r2", "купить молоко", "2026-08-15T10:00:00Z", "2026-08-13T09:00:00Z"));
        FindItemsTool tool = new FindItemsTool(store);

        String result = tool.execute("{}");

        JsonNode parsed = MAPPER.readTree(result);
        assertEquals(2, parsed.size());
    }

    @Test
    public void filtersByQuery() throws IOException {
        ReminderStore store = store();
        store.add(new Reminder("r1", "позвонить Ивану", "2026-08-14T10:00:00Z", "2026-08-13T09:00:00Z"));
        store.add(new Reminder("r2", "купить молоко", "2026-08-15T10:00:00Z", "2026-08-13T09:00:00Z"));
        FindItemsTool tool = new FindItemsTool(store);

        String result = tool.execute("{\"query\":\"ивану\"}");

        JsonNode parsed = MAPPER.readTree(result);
        assertEquals(1, parsed.size());
        assertEquals("позвонить Ивану", parsed.get(0).get("text").asText());
    }

    @Test(expected = ToolExecutionException.class)
    public void throwsOnMalformedJson() throws IOException {
        FindItemsTool tool = new FindItemsTool(store());

        tool.execute("not json");
    }
}
