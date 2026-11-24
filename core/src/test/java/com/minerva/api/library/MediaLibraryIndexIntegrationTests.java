package com.minerva.api.library;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@Tag("ContinuousIntegration")
class MediaLibraryIndexIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    private MediaLibraryIndex index;
    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        index = new MediaLibraryIndex(jdbc, new DataSourceTransactionManager(dataSource));
        index.initialise();
        jdbc.update("DELETE FROM media_library_index");
    }

    @Test
    void pagesAcrossTimestampTiesAndConcurrentChanges() {
        Instant instant = Instant.parse("2026-01-01T00:00:00.123456789Z");
        var first = item("00000000-0000-0000-0000-000000000003", instant);
        var second = item("00000000-0000-0000-0000-000000000002", instant);
        var third = item("00000000-0000-0000-0000-000000000001", instant);
        index.rebuild(List.of(third, first, second).iterator());

        var page = index.page(1, null);
        assertEquals(List.of(first), page.items());
        index.insert(item("00000000-0000-0000-0000-000000000004", instant));
        index.delete(java.util.UUID.fromString(first.id()));
        var next = index.page(1, page.nextCursor());
        assertEquals(List.of(second), next.items());
        var last = index.page(1, next.nextCursor());
        assertEquals(List.of(third), last.items());
        assertNull(last.nextCursor());
    }

    @Test
    void failedRebuildRollsBackPreviousProjection() {
        var existing = item("00000000-0000-0000-0000-000000000001", Instant.EPOCH);
        index.insert(existing);
        Iterator<MediaLibraryItem> broken = new Iterator<>() {
            public boolean hasNext() { throw new IllegalStateException("invalid metadata"); }
            public MediaLibraryItem next() { throw new AssertionError(); }
        };
        assertThrows(IllegalStateException.class, () -> index.rebuild(broken));
        assertEquals(List.of(existing), index.list());
    }

    @Test
    void reconcilesExistingFilesAndStaleDatabaseOnRestart() throws Exception {
        var old = new MediaLibraryStore(directory, index);
        var saved = old.save("Article", null, "text/plain", "article.txt", "Text", "Relevant", new byte[]{1});
        index.insert(item("00000000-0000-0000-0000-000000000001", Instant.EPOCH));

        var restored = new MediaLibraryStore(directory, index);
        assertEquals(List.of(saved), restored.page(10, null).items());
        assertEquals("Text", restored.getDetails(java.util.UUID.fromString(saved.id())).extractedText());
        Files.delete(directory.resolve(saved.id() + ".txt"));
        assertThrows(java.io.IOException.class, () -> new MediaLibraryStore(directory, index).initialiseIndex());
        assertEquals(List.of(saved), index.list());
    }

    private static MediaLibraryItem item(String id, Instant timestamp) {
        return new MediaLibraryItem(id, "Article", null, "text/plain", "article.txt", timestamp, "Relevant");
    }
}
