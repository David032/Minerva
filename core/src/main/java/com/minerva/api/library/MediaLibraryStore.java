package com.minerva.api.library;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Stores accepted media and metadata in Minerva's persistent data directory.
 */
@Component
public class MediaLibraryStore {
    private static final Gson GSON = new Gson();
    private final Path directory;
    private final MediaLibraryIndex index;
    private boolean indexDirty = true;

    /**
     * Creates a store in the configured Minerva data directory.
     *
     * @param fileManager resolves the persistent library directory
     * @param index rebuildable metadata projection
     */
    @Autowired
    public MediaLibraryStore(FileManager fileManager, MediaLibraryIndex index) {
        this(Path.of(fileManager.getDirectoryPath(Directory.LIBRARY)), index);
    }

    /**
     * Creates a store in an explicit directory, primarily for isolated tests.
     *
     * @param directory persistent library directory
     * @param index rebuildable metadata projection
     */
    MediaLibraryStore(Path directory, MediaLibraryIndex index) {
        this.directory = directory;
        this.index = index;
    }

    /**
     * Reconciles the database projection with the authoritative files at startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initialiseIndex() throws IOException {
        ensureIndexed();
    }

    /**
     * Persists an accepted item and its metadata, then updates the rebuildable index.
     *
     * @param title display title
     * @param source original URL, or {@code null} for a file upload
     * @param contentType validated content type
     * @param fileName submitted display filename
     * @param extractedText bounded text for later retrieval
     * @param reason curation reason
     * @param content validated media bytes
     * @return the saved item's public metadata
     * @throws IllegalArgumentException if the content type is unsupported or the content is empty
     * @throws IOException if the item cannot be persisted
     */
    public synchronized MediaLibraryItem save(
            String title,
            String source,
            String contentType,
            String fileName,
            String extractedText,
            String reason,
            byte[] content
    ) throws IOException {
        ensureIndexed();
        String extension = extensionFor(contentType);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The media content is empty.");
        }
        Files.createDirectories(directory);
        UUID id = UUID.randomUUID();
        MediaLibraryItem item = new MediaLibraryItem(
                id.toString(),
                normaliseTitle(title, fileName),
                source,
                contentType,
                safeFileName(fileName, extension),
                Instant.now(),
                reason);
        Path contentPath = contentPath(id, extension);
        Path metadataPath = metadataPath(id);
        Path temporaryContent = Files.createTempFile(directory, ".media-", ".tmp");
        Path temporaryMetadata = Files.createTempFile(directory, ".metadata-", ".tmp");
        boolean committed = false;
        try {
            Files.write(temporaryContent, content);
            Files.writeString(temporaryMetadata,
                    GSON.toJson(StoredEntry.from(item, extractedText)), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryContent, contentPath);
            moveIntoPlace(temporaryMetadata, metadataPath);
            committed = true;
            indexDirty = true;
            index.insert(item);
            indexDirty = false;
            return item;
        } finally {
            Files.deleteIfExists(temporaryContent);
            Files.deleteIfExists(temporaryMetadata);
            if (!committed) {
                Files.deleteIfExists(contentPath);
                Files.deleteIfExists(metadataPath);
            }
        }
    }

    /**
     * Returns all library items, newest first.
     *
     * @return immutable public metadata list
     * @throws IOException if saved metadata cannot be read
     */
    public synchronized List<MediaLibraryItem> list() throws IOException {
        ensureIndexed();
        return index.list();
    }

    /**
     * Returns a bounded page in newest-first order.
     *
     * @param limit maximum number of items, from 1 to 100
     * @param cursor cursor from the preceding page, or {@code null} for the first page
     * @return the items and next cursor
     * @throws IOException if the file index cannot be reconciled
     */
    public synchronized MediaLibraryPage page(int limit, String cursor) throws IOException {
        ensureIndexed();
        return index.page(limit, cursor);
    }

    /**
     * Loads one item and its extracted text.
     *
     * @param id item UUID
     * @return item details
     * @throws NoSuchElementException if the item does not exist
     * @throws IOException if the metadata cannot be read
     */
    public synchronized MediaLibraryDetails getDetails(UUID id) throws IOException {
        ensureIndexed();
        StoredEntry entry = readEntry(metadataPath(id));
        return new MediaLibraryDetails(entry.item(), entry.extractedText());
    }

    /**
     * Reads the original persisted media bytes.
     *
     * @param id item UUID
     * @return media bytes
     * @throws NoSuchElementException if the item does not exist
     * @throws IOException if the media bytes cannot be read
     */
    public synchronized byte[] readContent(UUID id) throws IOException {
        ensureIndexed();
        StoredEntry entry = readEntry(metadataPath(id));
        return Files.readAllBytes(contentPath(id, extensionFor(entry.item().contentType())));
    }

    /**
     * Removes one item and its media bytes.
     *
     * @param id item UUID
     * @return {@code true} if an item was removed
     * @throws IOException if metadata or media cannot be removed
     */
    public synchronized boolean delete(UUID id) throws IOException {
        ensureIndexed();
        Path metadata = metadataPath(id);
        if (Files.notExists(metadata)) {
            return false;
        }
        StoredEntry entry = readEntry(metadata);
        Path tombstone = directory.resolve(id + ".json.deleting");
        indexDirty = true;
        moveIntoPlace(metadata, tombstone);
        Files.delete(contentPath(id, extensionFor(entry.item().contentType())));
        Files.delete(tombstone);
        index.delete(id);
        indexDirty = false;
        return true;
    }

    private void ensureIndexed() throws IOException {
        if (!indexDirty) {
            return;
        }
        if (Files.notExists(directory)) {
            index.rebuild(List.<MediaLibraryItem>of().iterator());
            indexDirty = false;
            return;
        }
        recoverDeletes();
        try (var files = Files.list(directory)) {
            var items = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            StoredEntry entry = readEntry(path);
                            MediaLibraryItem item = entry.item();
                            if (!Files.isRegularFile(contentPath(UUID.fromString(item.id()),
                                    extensionFor(item.contentType())))) {
                                throw new IOException("Media library content is missing: " + path.getFileName());
                            }
                            return item;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            index.rebuild(items.iterator());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        indexDirty = false;
    }

    private void recoverDeletes() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path tombstone : files.filter(path -> path.getFileName().toString().endsWith(".json.deleting"))
                    .toList()) {
                String name = tombstone.getFileName().toString();
                Path metadata = directory.resolve(name.substring(0, name.length() - ".deleting".length()));
                if (Files.exists(metadata)) {
                    throw new IOException("Conflicting media library deletion files: " + name);
                }
                StoredEntry entry = readEntry(tombstone);
                Path content = contentPath(UUID.fromString(entry.id()), extensionFor(entry.contentType()));
                if (Files.isRegularFile(content)) {
                    moveIntoPlace(tombstone, metadata);
                } else if (Files.notExists(content)) {
                    Files.delete(tombstone);
                } else {
                    throw new IOException("Cannot inspect media library content for " + name);
                }
            }
        }
    }

    private StoredEntry readEntry(Path metadataPath) throws IOException {
        if (Files.notExists(metadataPath)) {
            throw new NoSuchElementException("The media library item was not found.");
        }
        try {
            StoredEntry entry = GSON.fromJson(
                    Files.readString(metadataPath, StandardCharsets.UTF_8), StoredEntry.class);
            if (entry == null || entry.id() == null || entry.title() == null
                    || entry.contentType() == null || entry.fileName() == null || entry.storedAt() == null) {
                throw new IOException("Media library metadata is invalid: " + metadataPath.getFileName());
            }
            String name = metadataPath.getFileName().toString();
            if (!name.equals(entry.id() + ".json") && !name.equals(entry.id() + ".json.deleting")) {
                throw new IOException("Media library metadata identifier does not match its filename.");
            }
            try {
                UUID.fromString(entry.id());
                extensionFor(entry.contentType());
                Instant.parse(entry.storedAt());
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new IOException("Media library metadata contains an invalid identifier or type.", e);
            }
            return entry;
        } catch (JsonParseException e) {
            throw new IOException("Could not read media library metadata: " + metadataPath.getFileName(), e);
        }
    }

    private Path metadataPath(UUID id) {
        return directory.resolve(id + ".json");
    }

    private Path contentPath(UUID id, String extension) {
        return directory.resolve(id + extension);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "text/html" -> ".html";
            case "text/plain" -> ".txt";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/ogg" -> ".ogv";
            default -> throw new IllegalArgumentException("This media type is not supported by the library.");
        };
    }

    private static String normaliseTitle(String title, String fileName) {
        String value = title == null || title.isBlank() ? fileName : title;
        String normalised = value == null ? "Media item" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (normalised.isEmpty()) {
            return "Media item";
        }
        return normalised.length() > 200 ? normalised.substring(0, 200) : normalised;
    }

    private static String safeFileName(String fileName, String extension) {
        String value = fileName == null ? "" : fileName.replace('\\', '/');
        int separator = value.lastIndexOf('/');
        String name = value.substring(separator + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "media" + extension;
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record StoredEntry(
            String id,
            String title,
            String source,
            String contentType,
            String fileName,
            String storedAt,
            String curationReason,
            String extractedText
    ) {
        private static StoredEntry from(MediaLibraryItem item, String extractedText) {
            return new StoredEntry(item.id(), item.title(), item.source(), item.contentType(),
                    item.fileName(), item.storedAt().toString(), item.curationReason(), extractedText);
        }

        private MediaLibraryItem item() {
            return new MediaLibraryItem(id, title, source, contentType, fileName,
                    Instant.parse(storedAt), curationReason);
        }
    }

}
