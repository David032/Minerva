package com.minerva.api.library;

import com.minerva.api.personality.MediaDocumentExtractor;
import com.minerva.api.personality.MediaPreprocessor;
import com.minerva.api.personality.RemoteArticleFetcher;
import com.minerva.api.personality.RemoteDocumentFetcher;
import com.minerva.api.personality.RemoteMediaFetcher;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Validates, curates, and persists media candidates for Minerva's interest library.
 */
@Service
public class MediaLibraryService {
    private static final int MAX_DOCUMENT_BYTES = 20 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_URL_LENGTH = 2_048;
    private static final int MAX_EXTRACTED_TEXT_CHARS = 40_000;
    private final MediaDocumentExtractor extractor;
    private final RemoteArticleFetcher articleFetcher;
    private final RemoteDocumentFetcher documentFetcher;
    private final RemoteMediaFetcher mediaFetcher;
    private final MediaPreprocessor mediaPreprocessor;
    private final MediaLibraryCurator curator;
    private final MediaLibraryStore store;

    /**
     * Creates the library workflow from the shared document and media processing components.
     *
     * @param extractor extracts text from supported documents
     * @param articleFetcher fetches public HTML articles
     * @param documentFetcher fetches public PDF documents
     * @param mediaFetcher fetches public images and videos
     * @param mediaPreprocessor prepares images and videos for model analysis
     * @param curator evaluates candidates against Minerva's interests
     * @param store persists only accepted candidates
     */
    public MediaLibraryService(
            MediaDocumentExtractor extractor,
            RemoteArticleFetcher articleFetcher,
            RemoteDocumentFetcher documentFetcher,
            RemoteMediaFetcher mediaFetcher,
            MediaPreprocessor mediaPreprocessor,
            MediaLibraryCurator curator,
            MediaLibraryStore store
    ) {
        this.extractor = extractor;
        this.articleFetcher = articleFetcher;
        this.documentFetcher = documentFetcher;
        this.mediaFetcher = mediaFetcher;
        this.mediaPreprocessor = mediaPreprocessor;
        this.curator = curator;
        this.store = store;
    }

    /**
     * Considers and, if selected by Minerva, stores an uploaded PDF, HTML document, or image.
     *
     * @param file uploaded candidate
     * @return the curation decision and saved metadata, if accepted
     * @throws IllegalArgumentException if the upload is empty, unsupported, or oversized
     * @throws IOException if extraction, analysis, or persistence fails
     */
    public Submission submitFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty PDF, HTML, or image file.");
        }
        String fileName = normaliseFileName(file.getOriginalFilename());
        int sizeLimit = uploadLimit(fileName);
        if (file.getSize() > sizeLimit) {
            throw new IllegalArgumentException(sizeLimit == MAX_IMAGE_BYTES
                    ? "Uploaded images must be 5 MB or smaller."
                    : "Uploaded documents must be 20 MB or smaller.");
        }
        byte[] content = file.getBytes();
        if (content.length == 0) {
            throw new IllegalArgumentException("Choose a non-empty PDF, HTML, or image file.");
        }
        String contentType = detectUploadType(fileName, content);
        if (content.length > sizeLimit) {
            throw new IllegalArgumentException(contentType.startsWith("image/")
                    ? "Uploaded images must be 5 MB or smaller."
                    : "Uploaded documents must be 20 MB or smaller.");
        }

        String text = "";
        List<RemoteMediaFetcher.MediaAsset> media = List.of();
        if (contentType.equals("application/pdf") || contentType.equals("text/html")) {
            MediaDocumentExtractor.Extraction extraction = extractor.extract(fileName, content);
            text = documentText(extraction);
            List<RemoteMediaFetcher.MediaAsset> fetched = fetchRemote(
                    () -> mediaFetcher.fetch(extraction.mediaUrls()));
            media = mediaPreprocessor.prepare(fetched);
        } else {
            RemoteMediaFetcher.MediaAsset image = new RemoteMediaFetcher.MediaAsset(
                    URI.create("https://upload.invalid/" + UUID.randomUUID()),
                    contentType,
                    content);
            media = mediaPreprocessor.prepare(List.of(image));
        }
        return consider(fileName, null, contentType, fileName, text, media, content);
    }

    /**
     * Fetches and considers a public PDF, HTML article, image, or video URL.
     *
     * @param url public media or document URL
     * @return the curation decision and saved metadata, if accepted
     * @throws IllegalArgumentException if the URL or returned media is unsupported
     * @throws IOException if fetching, analysis, or persistence fails
     */
    public Submission submitUrl(String url) throws IOException {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Enter a public media URL of 2,048 characters or fewer.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Enter a valid media URL.", e);
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".pdf")) {
            RemoteDocumentFetcher.RemoteDocument document = fetchRemote(() -> documentFetcher.fetch(url));
            MediaDocumentExtractor.Extraction extraction = extractor.extract(document.fileName(), document.content());
            return consider(
                    document.fileName(),
                    document.source().toString(),
                    "application/pdf",
                    document.fileName(),
                    documentText(extraction),
                    List.of(),
                    document.content());
        }
        if (isMediaPath(path)) {
            RemoteMediaFetcher.MediaAsset asset = fetchRemote(() -> mediaFetcher.fetch(List.of(uri))).getFirst();
            List<RemoteMediaFetcher.MediaAsset> prepared = mediaPreprocessor.prepare(List.of(asset));
            String title = fileNameFromPath(path, uri.getHost());
            return consider(title, uri.toString(), asset.contentType(), title, "", prepared, asset.content());
        }

        RemoteArticleFetcher.Article article = fetchRemote(() -> articleFetcher.fetch(url));
        String title = article.title().isBlank() ? fileNameFromPath(path, uri.getHost()) : article.title();
        String snapshot = "Title: " + title + "\nSource: " + article.source() + "\n\n" + article.text();
        byte[] content = snapshot.getBytes(StandardCharsets.UTF_8);
        return consider(title, article.source().toString(), "text/plain", "article.txt", snapshot, List.of(), content);
    }

    /**
     * Lists accepted items, newest first.
     *
     * @return saved library metadata
     * @throws IOException if persisted metadata cannot be read
     */
    public List<MediaLibraryItem> listItems() throws IOException {
        return store.list();
    }

    /**
     * Lists accepted items in bounded newest-first pages.
     *
     * @param limit page size, from 1 to 100
     * @param cursor previous page cursor, or {@code null} for the first page
     * @return the requested page
     * @throws IOException if the library index cannot be reconciled
     */
    public MediaLibraryPage listPage(int limit, String cursor) throws IOException {
        return store.page(limit, cursor);
    }

    /**
     * Retrieves an accepted item and its extracted text.
     *
     * @param id item UUID string
     * @return the saved item and text
     * @throws IllegalArgumentException if the identifier is not a UUID
     * @throws NoSuchElementException if the item does not exist
     * @throws IOException if saved metadata cannot be read
     */
    public MediaLibraryDetails getDetails(String id) throws IOException {
        return store.getDetails(parseId(id));
    }

    /**
     * Retrieves the original saved media bytes.
     *
     * @param id item UUID string
     * @return original media bytes
     * @throws IllegalArgumentException if the identifier is not a UUID
     * @throws NoSuchElementException if the item does not exist
     * @throws IOException if saved media cannot be read
     */
    public byte[] readContent(String id) throws IOException {
        return store.readContent(parseId(id));
    }

    /**
     * Removes an accepted item and its content.
     *
     * @param id item UUID string
     * @return {@code true} if an item was removed
     * @throws IllegalArgumentException if the identifier is not a UUID
     * @throws IOException if the item cannot be removed
     */
    public boolean deleteItem(String id) throws IOException {
        return store.delete(parseId(id));
    }

    private Submission consider(
            String title,
            String source,
            String contentType,
            String fileName,
            String text,
            List<RemoteMediaFetcher.MediaAsset> media,
            byte[] content
    ) throws IOException {
        MediaLibraryCurator.Decision decision = curator.decide(title, source, contentType, text, media);
        if (!decision.accepted()) {
            return new Submission(false, decision.reason(), null);
        }
        MediaLibraryItem item = store.save(
                title, source, contentType, fileName,
                text == null || text.isBlank() ? decision.summary() : decision.summary() + "\n\n" + text,
                decision.reason(), content);
        return new Submission(true, decision.reason(), item);
    }

    private static <T> T fetchRemote(RemoteOperation<T> operation) throws IOException {
        try {
            return operation.fetch();
        } catch (IOException e) {
            throw new RemoteFetchException("Could not download the media candidate: " + e.getMessage(), e);
        }
    }

    private static String documentText(MediaDocumentExtractor.Extraction extraction) {
        String tags = String.join(", ", extraction.tags());
        String altText = String.join("\n", extraction.imageAltTexts());
        String text = (extraction.text() == null ? "" : extraction.text())
                + (tags.isBlank() ? "" : "\nInterest tags: " + tags)
                + (altText.isBlank() ? "" : "\nImage descriptions: " + altText);
        return text.length() > MAX_EXTRACTED_TEXT_CHARS
                ? text.substring(0, MAX_EXTRACTED_TEXT_CHARS)
                : text;
    }

    private static String normaliseFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String normalised = fileName.replace('\\', '/');
        normalised = normalised.substring(normalised.lastIndexOf('/') + 1).trim();
        return normalised.length() > 255 ? normalised.substring(normalised.length() - 255) : normalised;
    }

    private static String detectUploadType(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("The uploaded file must have a supported filename extension.");
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            if (!startsWith(content, "%PDF-")) {
                throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
            }
            return "application/pdf";
        }
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "text/html";
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            requireSignature(content, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "JPEG");
            return "image/jpeg";
        }
        if (lowerName.endsWith(".png")) {
            requireSignature(content, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "PNG");
            return "image/png";
        }
        if (lowerName.endsWith(".gif")) {
            if (!startsWith(content, "GIF87a") && !startsWith(content, "GIF89a")) {
                throw new IllegalArgumentException("The uploaded file is not a valid GIF image.");
            }
            return "image/gif";
        }
        if (lowerName.endsWith(".webp")) {
            if (content.length < 12 || !startsWith(content, "RIFF")
                    || content[8] != 'W' || content[9] != 'E' || content[10] != 'B' || content[11] != 'P') {
                throw new IllegalArgumentException("The uploaded file is not a valid WebP image.");
            }
            return "image/webp";
        }
        throw new IllegalArgumentException("Only PDF, HTML, JPEG, PNG, GIF, and WebP files are supported.");
    }

    private static int uploadLimit(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("The uploaded file must have a supported filename extension.");
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png")
                || lowerName.endsWith(".gif") || lowerName.endsWith(".webp")) {
            return MAX_IMAGE_BYTES;
        }
        if (lowerName.endsWith(".pdf") || lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return MAX_DOCUMENT_BYTES;
        }
        throw new IllegalArgumentException("Only PDF, HTML, JPEG, PNG, GIF, and WebP files are supported.");
    }

    private static void requireSignature(byte[] content, byte[] signature, String type) {
        if (content.length < signature.length) {
            throw new IllegalArgumentException("The uploaded file is not a valid " + type + " image.");
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                throw new IllegalArgumentException("The uploaded file is not a valid " + type + " image.");
            }
        }
    }

    private static boolean startsWith(byte[] content, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (content.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (content[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMediaPath(String path) {
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png")
                || path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".mp4")
                || path.endsWith(".webm") || path.endsWith(".ogv") || path.endsWith(".ogg");
    }

    private static String fileNameFromPath(String path, String fallback) {
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return fallback == null || fallback.isBlank() ? "media" : fallback;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("The media library item identifier is invalid.", e);
        }
    }

    @FunctionalInterface
    private interface RemoteOperation<T> {
        T fetch() throws IOException;
    }

    /**
     * Indicates that a public media URL could not be retrieved.
     */
    public static final class RemoteFetchException extends IOException {
        /**
         * Creates an explicit remote-download failure.
         *
         * @param message safe download failure message
         * @param cause original network failure
         */
        public RemoteFetchException(String message, IOException cause) {
            super(message, cause);
        }
    }

    /**
     * A curation result; rejected candidates have no stored item.
     *
     * @param accepted whether the candidate was saved
     * @param reason curation decision reason
     * @param item saved metadata, or {@code null} when rejected
     */
    public record Submission(boolean accepted, String reason, MediaLibraryItem item) {
    }
}
