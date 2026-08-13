package com.mailagent.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks which message ids have already been processed, backed by an
 * append-only file so the state survives a process restart. The id must be
 * a stable message identifier (Outlook EntryID / Message-ID) — never
 * subject/body.
 */
public class SeenStore {

    private final Path file;
    private final Set<String> seenIds;

    public SeenStore(Path file) {
        this.file = file;
        this.seenIds = Collections.synchronizedSet(new LinkedHashSet<>(loadExisting(file)));
    }

    public boolean isSeen(String id) {
        return seenIds.contains(id);
    }

    public void markSeen(String id) {
        if (!seenIds.add(id)) {
            return;
        }
        append(id);
    }

    private static List<String> loadExisting(Path file) {
        if (!Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read seen-store: " + file, e);
        }
    }

    private void append(String id) {
        try {
            Files.write(
                    file,
                    (id + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist seen id to store: " + file, e);
        }
    }
}
