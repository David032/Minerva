package com.minerva.api.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class MediaLibraryIndexTests {
    @Test
    void cursorRetainsExactTimestampAndIdentifierForNextPage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var index = new MediaLibraryIndex(jdbc, mock(PlatformTransactionManager.class));
        var timestamp = Instant.parse("2026-01-01T00:00:00.123456789Z");
        var first = item("00000000-0000-0000-0000-000000000002", timestamp);
        var second = item("00000000-0000-0000-0000-000000000001", timestamp);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MediaLibraryItem>>any(),
                eq(2))).thenReturn(List.of(first, second));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MediaLibraryItem>>any(),
                eq(timestamp.getEpochSecond()), eq(timestamp.getNano()), eq(first.id()), eq(2)))
                .thenReturn(List.of(second));

        var page = index.page(1, null);
        assertEquals(List.of(first), page.items());
        assertNotNull(page.nextCursor());
        var next = index.page(1, page.nextCursor());
        assertEquals(List.of(second), next.items());
        assertNull(next.nextCursor());
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MediaLibraryItem>>any(),
                eq(timestamp.getEpochSecond()), eq(timestamp.getNano()), eq(first.id()), eq(2));
    }

    @Test
    void rejectsInvalidLimitsAndCursors() {
        var index = new MediaLibraryIndex(mock(JdbcTemplate.class), mock(PlatformTransactionManager.class));
        assertThrows(IllegalArgumentException.class, () -> index.page(0, null));
        assertThrows(IllegalArgumentException.class, () -> index.page(101, null));
        assertThrows(IllegalArgumentException.class, () -> index.page(1, ""));
        assertThrows(IllegalArgumentException.class, () -> index.page(1, "invalid!"));
    }

    private static MediaLibraryItem item(String id, Instant timestamp) {
        return new MediaLibraryItem(id, "Article", null, "text/plain", "article.txt", timestamp, "Relevant");
    }
}
