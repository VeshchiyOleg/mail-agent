package com.mailagent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Append-only audit journal with a SHA-256 hash-chain: each entry's hash
 * covers its own fields plus the previous entry's hash, so any edit to a
 * past line breaks {@link #verifyChain()} for everything after it. Callers
 * are responsible for keeping {@code details} free of PII/email bodies —
 * this class persists whatever it's given verbatim.
 */
public class AuditLog {

    public static final String GENESIS_HASH = "GENESIS";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final Clock clock;
    private long nextSeq;
    private String lastHash;

    public AuditLog(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
        List<AuditEntry> existing = readAll(file);
        if (existing.isEmpty()) {
            this.nextSeq = 0;
            this.lastHash = GENESIS_HASH;
        } else {
            AuditEntry last = existing.get(existing.size() - 1);
            this.nextSeq = last.getSeq() + 1;
            this.lastHash = last.getHash();
        }
    }

    public synchronized AuditEntry append(String event, Map<String, String> details) {
        long seq = nextSeq;
        String timestampIso = clock.instant().toString();
        String prevHash = lastHash;
        String hash = computeHash(seq, timestampIso, event, details, prevHash);

        AuditEntry entry = new AuditEntry(seq, timestampIso, event, details, prevHash, hash);
        writeEntry(entry);

        nextSeq = seq + 1;
        lastHash = hash;
        return entry;
    }

    public synchronized boolean verifyChain() {
        String expectedPrevHash = GENESIS_HASH;
        for (AuditEntry entry : readAll(file)) {
            if (!expectedPrevHash.equals(entry.getPrevHash())) {
                return false;
            }
            String expectedHash = computeHash(
                    entry.getSeq(), entry.getTimestampIso(), entry.getEvent(), entry.getDetails(), entry.getPrevHash());
            if (!expectedHash.equals(entry.getHash())) {
                return false;
            }
            expectedPrevHash = entry.getHash();
        }
        return true;
    }

    private static List<AuditEntry> readAll(Path file) {
        if (!Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<AuditEntry> entries = new ArrayList<>();
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                entries.add(MAPPER.readValue(line, AuditEntry.class));
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read audit log: " + file, e);
        }
    }

    private void writeEntry(AuditEntry entry) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = MAPPER.writeValueAsString(entry);
            Files.write(
                    file,
                    (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append audit entry: " + file, e);
        }
    }

    private static String computeHash(long seq, String timestampIso, String event, Map<String, String> details, String prevHash) {
        String canonical = seq + "|" + timestampIso + "|" + event + "|" + canonicalDetails(details) + "|" + prevHash;
        return sha256Hex(canonical);
    }

    private static String canonicalDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(details).entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        return sb.toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
