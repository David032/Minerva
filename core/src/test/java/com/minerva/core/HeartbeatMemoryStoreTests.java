package com.minerva.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class HeartbeatMemoryStoreTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appendsAndReturnsMostRecentEntriesFirst() throws IOException {
        HeartbeatMemoryStore store = new HeartbeatMemoryStore(temporaryDirectory.resolve("heartbeat.json"));
        store.append(Instant.parse("2026-01-01T00:00:00Z"), "SUCCEEDED", 2, "First result");
        store.append(Instant.parse("2026-01-02T00:00:00Z"), "FAILED", 1, "IllegalStateException");

        var recent = store.loadRecent(2);

        assertEquals(2, recent.size());
        assertEquals("FAILED", recent.get(0).status());
        assertEquals("First result", recent.get(1).outcome());
    }

    @Test
    void boundsOutcomeTextAndRetainsOnlyTheLatestHundredEntries() throws IOException {
        HeartbeatMemoryStore store = new HeartbeatMemoryStore(temporaryDirectory.resolve("heartbeat.json"));
        IntStream.range(0, 101).forEach(index -> {
            try {
                store.append(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index),
                        "SUCCEEDED", 0, "x".repeat(600));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });

        var recent = store.loadRecent(100);

        assertEquals(100, recent.size());
        assertEquals("x".repeat(500) + "...", recent.get(0).outcome());
        assertEquals(Instant.parse("2026-01-01T00:01:40Z").toString(), recent.get(0).occurredAt());
    }

    @Test
    void rejectsInvalidLimitsAndEntries() throws IOException {
        HeartbeatMemoryStore store = new HeartbeatMemoryStore(temporaryDirectory.resolve("heartbeat.json"));

        assertThrows(IllegalArgumentException.class, () -> store.loadRecent(0));
        assertThrows(IllegalArgumentException.class,
                () -> store.append(Instant.now(), "UNKNOWN", 0, "Outcome"));
        assertTrue(store.loadRecent(10).isEmpty());
    }
}
