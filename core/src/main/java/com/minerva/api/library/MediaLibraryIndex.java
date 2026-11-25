package com.minerva.api.library;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Rebuildable PostgreSQL projection of the public metadata in the library files.
 */
@Component
public class MediaLibraryIndex {
    private static final String COLUMNS =
            "id, title, source, content_type, file_name, stored_seconds, stored_nanos, curation_reason";
    private static final String INSERT =
            "INSERT INTO media_library_index (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    /**
     * Creates the projection backed by the application's database.
     *
     * @param jdbc database access
     * @param transactionManager transaction manager for atomic rebuilds
     */
    public MediaLibraryIndex(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Creates the projection table and ordering index when they do not yet exist. */
    @PostConstruct
    void initialise() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS media_library_index (
                    id varchar(36) PRIMARY KEY,
                    title text NOT NULL,
                    source text,
                    content_type text NOT NULL,
                    file_name text NOT NULL,
                    stored_seconds bigint NOT NULL,
                    stored_nanos integer NOT NULL,
                    curation_reason text
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_media_library_order
                ON media_library_index (stored_seconds DESC, stored_nanos DESC, id DESC)
                """);
    }

    /**
     * Replaces the projection in a single transaction using the validated file entries.
     *
     * @param items iterator over authoritative metadata
     */
    void rebuild(Iterator<MediaLibraryItem> items) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM media_library_index");
            List<MediaLibraryItem> batch = new ArrayList<>(250);
            while (items.hasNext()) {
                batch.add(items.next());
                if (batch.size() == 250) {
                    insertBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                insertBatch(batch);
            }
        });
    }

    private void insertBatch(List<MediaLibraryItem> items) {
        jdbc.batchUpdate(INSERT, items, 250, (statement, item) -> {
            statement.setString(1, item.id());
            statement.setString(2, item.title());
            statement.setString(3, item.source());
            statement.setString(4, item.contentType());
            statement.setString(5, item.fileName());
            statement.setLong(6, item.storedAt().getEpochSecond());
            statement.setInt(7, item.storedAt().getNano());
            statement.setString(8, item.curationReason());
        });
    }

    /** Adds a newly committed file entry to the projection. */
    void insert(MediaLibraryItem item) {
        jdbc.update(INSERT,
                item.id(), item.title(), item.source(), item.contentType(), item.fileName(),
                item.storedAt().getEpochSecond(), item.storedAt().getNano(), item.curationReason());
    }

    /** Removes a deleted file entry from the projection. */
    void delete(UUID id) {
        jdbc.update("DELETE FROM media_library_index WHERE id = ?", id.toString());
    }

    /** Returns the complete legacy listing without reading individual metadata files. */
    List<MediaLibraryItem> list() {
        return jdbc.query("SELECT " + COLUMNS + " FROM media_library_index"
                + " ORDER BY stored_seconds DESC, stored_nanos DESC, id DESC", MediaLibraryIndex::mapItem);
    }

    /**
     * Reads a bounded page using an opaque cursor.
     *
     * @param limit number of items, from 1 to 100
     * @param cursor cursor from the previous page, or {@code null} for the first page
     * @return the items and a cursor when more entries exist
     * @throws IllegalArgumentException if the limit or cursor is invalid
     */
    MediaLibraryPage page(int limit, String cursor) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("The page size must be between 1 and 100.");
        }
        String query = "SELECT " + COLUMNS + " FROM media_library_index";
        List<MediaLibraryItem> rows;
        String order = " ORDER BY stored_seconds DESC, stored_nanos DESC, id DESC LIMIT ?";
        if (cursor == null) {
            rows = jdbc.query(query + order, MediaLibraryIndex::mapItem, limit + 1);
        } else {
            Cursor position = Cursor.parse(cursor);
            rows = jdbc.query(query + " WHERE (stored_seconds, stored_nanos, id) < (?, ?, ?)" + order,
                    MediaLibraryIndex::mapItem, position.seconds(), position.nanos(), position.id(), limit + 1);
        }
        List<MediaLibraryItem> items = rows.subList(0, Math.min(limit, rows.size()));
        return new MediaLibraryPage(items, rows.size() > limit ? Cursor.of(items.getLast()).encode() : null);
    }

    private static MediaLibraryItem mapItem(java.sql.ResultSet result, int row) throws java.sql.SQLException {
        return new MediaLibraryItem(result.getString("id"), result.getString("title"), result.getString("source"),
                result.getString("content_type"), result.getString("file_name"),
                Instant.ofEpochSecond(result.getLong("stored_seconds"), result.getInt("stored_nanos")),
                result.getString("curation_reason"));
    }

    private record Cursor(long seconds, int nanos, String id) {
        static Cursor of(MediaLibraryItem item) {
            return new Cursor(item.storedAt().getEpochSecond(), item.storedAt().getNano(), item.id());
        }

        String encode() {
            String value = seconds + ":" + nanos + ":" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
        }

        static Cursor parse(String encoded) {
            try {
                if (encoded.length() > 128 || encoded.isBlank()) {
                    throw new IllegalArgumentException();
                }
                String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.US_ASCII);
                String[] parts = value.split(":", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException();
                }
                long seconds = Long.parseLong(parts[0]);
                int nanos = Integer.parseInt(parts[1]);
                UUID uuid = UUID.fromString(parts[2]);
                Instant.ofEpochSecond(seconds, nanos);
                if (nanos < 0 || nanos > 999_999_999 || !uuid.toString().equals(parts[2])) {
                    throw new IllegalArgumentException();
                }
                return new Cursor(seconds, nanos, parts[2]);
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                throw new IllegalArgumentException("The library page cursor is invalid.", e);
            }
        }
    }
}
