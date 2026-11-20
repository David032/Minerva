package com.minerva.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.minerva.utils.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists a bounded local history of heartbeat outcomes independently of Journiv.
 */
@Component
public class HeartbeatMemoryStore {
    private static final Gson GSON = new Gson();
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<HeartbeatEntry>>() {}.getType();
    private static final int MAX_ENTRIES = 100;
    private final Path path;

    /**
     * Creates a store at the application's heartbeat memories path.
     *
     * @param fileManager resolves the local memories directory
     */
    @Autowired
    public HeartbeatMemoryStore(FileManager fileManager) {
        this(fileManager.getHeartbeatMemoriesPath());
    }

    /**
     * Creates a store at an explicit path, primarily for isolated tests.
     *
     * @param path file used to persist heartbeat history
     */
    public HeartbeatMemoryStore(Path path) {
        this.path = path;
    }

    /**
     * Appends a heartbeat result and retains only the latest 100 entries.
     *
     * @param occurredAt time the heartbeat completed
     * @param status {@code SUCCEEDED} or {@code FAILED}
     * @param actionCount number of tool actions started during the heartbeat
     * @param outcome bounded assistant result or failure type
     * @throws IllegalArgumentException if the timestamp, status, action count, or outcome is invalid
     * @throws IOException if the history cannot be read or written
     */
    public synchronized void append(Instant occurredAt, String status, int actionCount, String outcome)
            throws IOException {
        if (occurredAt == null || !("SUCCEEDED".equals(status) || "FAILED".equals(status))
                || actionCount < 0 || outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("Heartbeat history entry is invalid.");
        }
        String boundedOutcome = ActivityLogText.bounded(outcome);
        List<HeartbeatEntry> entries = new ArrayList<>(readEntries());
        entries.add(new HeartbeatEntry(occurredAt.toString(), status, actionCount, boundedOutcome));
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES, entries.size()));
        }
        write(entries);
    }

    /**
     * Loads the most recent heartbeat results, newest first.
     *
     * @param limit maximum number of entries to return, from 1 to 100
     * @return immutable recent heartbeat history
     * @throws IllegalArgumentException if the requested limit is invalid
     * @throws IOException if the history file cannot be read
     */
    public synchronized List<HeartbeatEntry> loadRecent(int limit) throws IOException {
        if (limit < 1 || limit > MAX_ENTRIES) {
            throw new IllegalArgumentException("Heartbeat history limit must be from 1 to 100.");
        }
        List<HeartbeatEntry> entries = readEntries();
        int start = Math.max(0, entries.size() - limit);
        List<HeartbeatEntry> recent = new ArrayList<>(entries.subList(start, entries.size()));
        java.util.Collections.reverse(recent);
        return List.copyOf(recent);
    }

    private List<HeartbeatEntry> readEntries() throws IOException {
        if (Files.notExists(path)) {
            return List.of();
        }
        try {
            List<HeartbeatEntry> entries = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), ENTRY_LIST_TYPE);
            if (entries == null) {
                throw new IOException("Heartbeat history file is empty or invalid.");
            }
            for (HeartbeatEntry entry : entries) {
                if (entry == null || entry.occurredAt() == null || entry.status() == null
                        || !("SUCCEEDED".equals(entry.status()) || "FAILED".equals(entry.status()))
                        || entry.outcome() == null || entry.outcome().length() > ActivityLogText.MAX_LENGTH + 3
                        || entry.actionCount() < 0) {
                    throw new IOException("Heartbeat history contains an invalid entry.");
                }
                try {
                    Instant.parse(entry.occurredAt());
                } catch (DateTimeParseException e) {
                    throw new IOException("Heartbeat history contains an invalid timestamp.", e);
                }
            }
            return entries;
        } catch (JsonParseException e) {
            throw new IOException("Could not read heartbeat history.", e);
        }
    }

    private void write(List<HeartbeatEntry> entries) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "heartbeat-memories-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(entries), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * A timestamped heartbeat outcome stored locally for the About page.
     *
     * @param occurredAt ISO-8601 completion timestamp
     * @param status {@code SUCCEEDED} or {@code FAILED}
     * @param actionCount number of tool actions started
     * @param outcome bounded assistant result or failure type
     */
    public record HeartbeatEntry(String occurredAt, String status, int actionCount, String outcome) {
    }
}
