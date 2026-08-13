package com.mailagent.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeenStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path backingFile() throws IOException {
        return tmp.newFile("seen.jsonl").toPath();
    }

    @Test
    public void unknownIdIsNotSeen() throws IOException {
        SeenStore store = new SeenStore(backingFile());

        assertFalse(store.isSeen("msg-1"));
    }

    @Test
    public void markedIdIsSeen() throws IOException {
        SeenStore store = new SeenStore(backingFile());

        store.markSeen("msg-1");

        assertTrue(store.isSeen("msg-1"));
    }

    @Test
    public void markSeenIsIdempotentAndDoesNotThrow() throws IOException {
        SeenStore store = new SeenStore(backingFile());

        store.markSeen("msg-1");
        store.markSeen("msg-1");

        assertTrue(store.isSeen("msg-1"));
    }

    @Test
    public void seenStateSurvivesRestart() throws IOException {
        Path file = backingFile();

        SeenStore beforeRestart = new SeenStore(file);
        beforeRestart.markSeen("msg-1");

        SeenStore afterRestart = new SeenStore(file);

        assertTrue(afterRestart.isSeen("msg-1"));
        assertFalse(afterRestart.isSeen("msg-2"));
    }
}
