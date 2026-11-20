package com.minerva.api.library;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST API for submitting candidates and managing Minerva's curated media library.
 */
@RestController
@RequestMapping("/api/library")
public class MediaLibraryApiController {
    private final MediaLibraryService service;

    /**
     * Creates the API controller backed by the shared library workflow.
     *
     * @param service validates, curates, and persists media
     */
    public MediaLibraryApiController(MediaLibraryService service) {
        this.service = service;
    }

    /**
     * Considers one URL or uploaded file and persists it only if selected by Minerva.
     *
     * @param url optional public media or document URL
     * @param file optional PDF, HTML, or image file
     * @return the curation result; accepted items are returned with HTTP 201
     * @throws IOException if fetching, analysis, or storage fails
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaLibraryService.Submission> submit(
            @RequestParam(required = false) String url,
            @RequestParam(required = false) MultipartFile file
    ) throws IOException {
        boolean hasUrl = url != null && !url.isBlank();
        boolean hasFile = file != null && !file.isEmpty();
        if (hasUrl == hasFile) {
            throw new IllegalArgumentException("Submit exactly one public URL or file.");
        }
        MediaLibraryService.Submission submission = hasUrl
                ? service.submitUrl(url)
                : service.submitFile(file);
        if (submission.accepted()) {
            if (submission.item() == null) {
                throw new IllegalStateException("An accepted media candidate has no saved item.");
            }
            return ResponseEntity.created(URI.create("/api/library/" + submission.item().id()))
                    .body(submission);
        }
        return ResponseEntity.ok(submission);
    }

    /**
     * Lists accepted items, newest first.
     *
     * @return public item metadata
     * @throws IOException if saved metadata cannot be read
     */
    @GetMapping
    public List<MediaLibraryItem> list() throws IOException {
        return service.listItems();
    }

    /**
     * Retrieves one item's metadata and extracted text.
     *
     * @param id item UUID
     * @return saved item and text
     * @throws IOException if saved metadata cannot be read
     */
    @GetMapping("/{id}")
    public MediaLibraryDetails get(@PathVariable String id) throws IOException {
        return service.getDetails(id);
    }

    /**
     * Downloads the original content of one accepted item.
     *
     * @param id item UUID
     * @return content with its validated media type and safe display filename
     * @throws IOException if saved content cannot be read
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable String id) throws IOException {
        MediaLibraryItem item = service.getDetails(id).item();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(item.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(item.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(service.readContent(id));
    }

    /**
     * Removes one accepted item and its persisted bytes.
     *
     * @param id item UUID
     * @return HTTP 204 when removed
     * @throws IOException if saved data cannot be removed
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
        if (!service.deleteItem(id)) {
            throw new NoSuchElementException("The media library item was not found.");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Maps invalid submissions and identifiers to HTTP 400.
     *
     * @param exception validation failure
     * @return error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    /**
     * Maps missing library items to HTTP 404.
     *
     * @param exception missing item
     * @return error response
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    /**
     * Maps remote-download failures to HTTP 502 with an actionable message.
     *
     * @param exception download failure
     * @return error response
     */
    @ExceptionHandler(MediaLibraryService.RemoteFetchException.class)
    public ResponseEntity<Map<String, String>> downloadFailed(
            MediaLibraryService.RemoteFetchException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", exception.getMessage()));
    }
}
