package com.mailagent.audit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuditLogTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

    private Path backingFile() throws IOException {
        return tmp.getRoot().toPath().resolve("audit.jsonl");
    }

    @Test
    public void firstAppendedEntryHasSeqZeroAndGenesisPrevHash() throws IOException {
        AuditLog log = new AuditLog(backingFile(), FIXED_CLOCK);

        AuditEntry entry = log.append("agent_mail_seen", Collections.singletonMap("msgId", "m1"));

        assertEquals(0L, entry.getSeq());
        assertEquals(AuditLog.GENESIS_HASH, entry.getPrevHash());
    }

    @Test
    public void chainVerifiesAfterSeveralAppends() throws IOException {
        AuditLog log = new AuditLog(backingFile(), FIXED_CLOCK);

        log.append("agent_mail_seen", Collections.singletonMap("msgId", "m1"));
        log.append("agent_tool_call", Collections.singletonMap("tool", "add_reminder"));
        log.append("agent_mail_replied", Collections.singletonMap("msgId", "m1"));

        assertTrue(log.verifyChain());
    }

    @Test
    public void tamperedEntryBreaksVerification() throws IOException {
        Path file = backingFile();
        AuditLog log = new AuditLog(file, FIXED_CLOCK);
        log.append("agent_mail_seen", Collections.singletonMap("msgId", "m1"));
        log.append("agent_tool_call", Collections.singletonMap("tool", "add_reminder"));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String tamperedFirstLine = lines.get(0).replace("agent_mail_seen", "agent_mail_DELETED");
        Files.write(file, (tamperedFirstLine + System.lineSeparator() + lines.get(1) + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8));

        AuditLog reopened = new AuditLog(file, FIXED_CLOCK);
        assertFalse(reopened.verifyChain());
    }

    @Test
    public void seqAndChainContinueCorrectlyAcrossRestart() throws IOException {
        Path file = backingFile();

        AuditLog beforeRestart = new AuditLog(file, FIXED_CLOCK);
        beforeRestart.append("agent_mail_seen", Collections.singletonMap("msgId", "m1"));
        AuditEntry lastBeforeRestart = beforeRestart.append("agent_mail_replied", Collections.singletonMap("msgId", "m1"));

        AuditLog afterRestart = new AuditLog(file, FIXED_CLOCK);
        AuditEntry firstAfterRestart = afterRestart.append("agent_mail_seen", Collections.singletonMap("msgId", "m2"));

        assertEquals(2L, firstAfterRestart.getSeq());
        assertEquals(lastBeforeRestart.getHash(), firstAfterRestart.getPrevHash());
        assertTrue(afterRestart.verifyChain());
    }
}
