package com.minerva.api.library;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
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

    /**
     * Creates a store in the configured Minerva data directory.
     *
     * @param fileManager resolves the persistent library directory
     */
    @Autowired
    public MediaLibraryStore(FileManager fileManager) {
        this(Path.of(fileManager.getDirectoryPath(Directory.LIBRARY)));
    }

    /**
     * Creates a store in an explicit directory, primarily for isolated tests.
     *
     * @param directory persistent library directory
     */
    MediaLibraryStore(Path directory) {
        this.directory = directory;
    }

    /**
     * Atomically persists an accepted item and its metadata.
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
        if (Files.notExists(directory)) {
            return List.of();
        }
        List<MediaLibraryItem> items = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                items.add(readEntry(path).item());
            }
        }
        return items.stream().sorted(Comparator.comparing(MediaLibraryItem::storedAt).reversed()).toList();
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
        Path metadata = metadataPath(id);
        if (Files.notExists(metadata)) {
            return false;
        }
        StoredEntry entry = readEntry(metadata);
        Files.delete(contentPath(id, extensionFor(entry.item().contentType())));
        Files.delete(metadata);
        return true;
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
            if (!metadataPath.getFileName().toString().equals(entry.id() + ".json")) {
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
