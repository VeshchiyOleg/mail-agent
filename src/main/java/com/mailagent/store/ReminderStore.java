package com.mailagent.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * JSON-backed store for reminders. Whole file is rewritten atomically
 * (write to a sibling temp file, then move) on every mutation, so a crash
 * mid-write can't corrupt it.
 */
public class ReminderStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public ReminderStore(Path file) {
        this.file = file;
    }

    public synchronized void add(Reminder reminder) {
        List<Reminder> all = new ArrayList<>(loadAll());
        all.add(reminder);
        saveAll(all);
    }

    public synchronized List<Reminder> findAll() {
        return loadAll();
    }

    public synchronized List<Reminder> findByText(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Reminder> result = new ArrayList<>();
        for (Reminder r : loadAll()) {
            if (r.getText() != null && r.getText().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(r);
            }
        }
        return result;
    }

    private List<Reminder> loadAll() {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                return Collections.emptyList();
            }
            Reminder[] all = MAPPER.readValue(Files.newInputStream(file), Reminder[].class);
            return new ArrayList<>(java.util.Arrays.asList(all));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read reminder store: " + file, e);
        }
    }

    private void saveAll(List<Reminder> all) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            byte[] json = MAPPER.writeValueAsBytes(all);
            Files.write(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist reminder store: " + file, e);
        }
    }
}
