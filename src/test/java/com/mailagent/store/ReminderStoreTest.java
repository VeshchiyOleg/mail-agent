package com.mailagent.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReminderStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path backingFile() throws IOException {
        return tmp.getRoot().toPath().resolve("reminders.json");
    }

    @Test
    public void newStoreHasNoReminders() throws IOException {
        ReminderStore store = new ReminderStore(backingFile());

        assertTrue(store.findAll().isEmpty());
    }

    @Test
    public void addedReminderIsReturnedByFindAll() throws IOException {
        ReminderStore store = new ReminderStore(backingFile());

        store.add(new Reminder("r1", "позвонить Ивану", "2026-08-14T10:00:00Z", "2026-08-13T09:00:00Z"));

        List<Reminder> all = store.findAll();
        assertEquals(1, all.size());
        assertEquals("позвонить Ивану", all.get(0).getText());
        assertEquals("2026-08-14T10:00:00Z", all.get(0).getDueIso());
    }

    @Test
    public void findByTextMatchesCaseInsensitiveSubstring() throws IOException {
        ReminderStore store = new ReminderStore(backingFile());
        store.add(new Reminder("r1", "позвонить Ивану", "2026-08-14T10:00:00Z", "2026-08-13T09:00:00Z"));
        store.add(new Reminder("r2", "купить молоко", "2026-08-15T10:00:00Z", "2026-08-13T09:00:00Z"));

        List<Reminder> found = store.findByText("ивану");

        assertEquals(1, found.size());
        assertEquals("r1", found.get(0).getId());
    }

    @Test
    public void reminderStateSurvivesRestart() throws IOException {
        Path file = backingFile();

        ReminderStore beforeRestart = new ReminderStore(file);
        beforeRestart.add(new Reminder("r1", "позвонить Ивану", "2026-08-14T10:00:00Z", "2026-08-13T09:00:00Z"));

        ReminderStore afterRestart = new ReminderStore(file);

        assertEquals(1, afterRestart.findAll().size());
        assertEquals("r1", afterRestart.findAll().get(0).getId());
    }
}
