package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityImportKind;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * HTTP API for submitting and checking background personality imports.
 */
@RestController
@RequestMapping("/api/personality/imports")
public class PersonalityImportJobController {
    private final PersonalityImportJobService service;

    /**
     * Creates the controller backed by durable import jobs.
     *
     * @param service persists and processes submitted imports
     */
    public PersonalityImportJobController(PersonalityImportJobService service) {
        this.service = service;
    }

    /**
     * Queues one batch or ZIP import without waiting for model analysis.
     *
     * @param htmlFiles optional HTML batch
     * @param imageFiles optional image batch
     * @param zipFile optional ZIP archive
     * @param guidance optional preference guidance
     * @return HTTP 202 and the queued job status
     * @throws IOException if upload data cannot be staged
     */
    @PostMapping
    public ResponseEntity<PersonalityImportJobResponse> submit(
            @RequestParam(required = false) List<MultipartFile> htmlFiles,
            @RequestParam(required = false) List<MultipartFile> imageFiles,
            @RequestParam(required = false) MultipartFile zipFile,
            @RequestParam(defaultValue = "") String guidance
    ) throws IOException {
        boolean hasHtml = hasFiles(htmlFiles);
        boolean hasImages = hasFiles(imageFiles);
        boolean hasZip = zipFile != null && !zipFile.isEmpty();
        int sourceCount = (hasHtml ? 1 : 0) + (hasImages ? 1 : 0) + (hasZip ? 1 : 0);
        if (sourceCount != 1) {
            throw new IllegalArgumentException("Submit exactly one HTML batch, image batch, or ZIP archive.");
        }
        PersonalityImportKind kind = hasHtml ? PersonalityImportKind.HTML_BATCH
                : hasImages ? PersonalityImportKind.IMAGE_BATCH
                : PersonalityImportKind.ZIP;
        List<MultipartFile> files = hasHtml ? htmlFiles : hasImages ? imageFiles : List.of(zipFile);
        PersonalityImportJobResponse response = service.submit(kind, files, guidance);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/personality/imports/" + response.id())
                .body(response);
    }

    /**
     * Retrieves a job's lifecycle status, aggregate counts, and per-file failures.
     *
     * @param id job identifier
     * @return persisted job status and result
     */
    @GetMapping("/{id}")
    public PersonalityImportJobResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Maps invalid import submissions to HTTP 400.
     *
     * @param exception validation failure
     * @return error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    /**
     * Maps unknown job identifiers to HTTP 404.
     *
     * @param exception missing job
     * @return error response
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    private static boolean hasFiles(List<MultipartFile> files) {
        return files != null && files.stream().anyMatch(file -> file != null && !file.isEmpty());
    }
}
