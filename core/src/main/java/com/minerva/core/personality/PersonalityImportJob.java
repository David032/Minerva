package com.minerva.core.personality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent state and result summary for a background personality import.
 */
@Entity
@Table(name = "personality_import_jobs", indexes = {
        @Index(name = "idx_personality_import_status_created", columnList = "status, created_at")
})
public class PersonalityImportJob {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PersonalityImportKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PersonalityImportJobStatus status;

    @Column(nullable = false, length = 2_000)
    private String guidance;

    @Column(name = "upload_directory", nullable = false, length = 36)
    private String uploadDirectory;

    @Column(name = "file_manifest", nullable = false, columnDefinition = "text")
    private String fileManifest;

    @Column(name = "failures_json", nullable = false, columnDefinition = "text")
    private String failuresJson;

    @Column(name = "error_message", length = 2_000)
    private String errorMessage;

    @Column(name = "successful_file_count", nullable = false)
    private int successfulFileCount;

    @Column(name = "saved_tag_count", nullable = false)
    private int savedTagCount;

    @Column(name = "analysed_media_count", nullable = false)
    private int analysedMediaCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonalityImportJob() {
    }

    /**
     * Creates a queued import job.
     *
     * @param id stable job identifier
     * @param kind source type being imported
     * @param guidance user preference guidance
     * @param uploadDirectory generated upload-directory name
     * @param fileManifest JSON manifest of staged files
     * @param now creation time
     */
    public PersonalityImportJob(
            UUID id,
            PersonalityImportKind kind,
            String guidance,
            String uploadDirectory,
            String fileManifest,
            Instant now
    ) {
        this.id = id;
        this.kind = kind;
        this.status = PersonalityImportJobStatus.QUEUED;
        this.guidance = guidance;
        this.uploadDirectory = uploadDirectory;
        this.fileManifest = fileManifest;
        this.failuresJson = "[]";
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @return persistent job identifier */
    public UUID getId() {
        return id;
    }

    /** @return source type being imported */
    public PersonalityImportKind getKind() {
        return kind;
    }

    /** @return current lifecycle state */
    public PersonalityImportJobStatus getStatus() {
        return status;
    }

    /** @return preference guidance submitted with the files */
    public String getGuidance() {
        return guidance;
    }

    /** @return generated upload-directory name */
    public String getUploadDirectory() {
        return uploadDirectory;
    }

    /** @return JSON manifest of staged files */
    public String getFileManifest() {
        return fileManifest;
    }

    /** @return JSON-encoded per-file failures */
    public String getFailuresJson() {
        return failuresJson;
    }

    /** @return terminal job error, or {@code null} when no job-level error occurred */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** @return number of successfully processed files */
    public int getSuccessfulFileCount() {
        return successfulFileCount;
    }

    /** @return number of saved interest tags */
    public int getSavedTagCount() {
        return savedTagCount;
    }

    /** @return number of analysed media items */
    public int getAnalysedMediaCount() {
        return analysedMediaCount;
    }

    /** @return job creation time */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return time of the latest lifecycle update */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Marks a queued job as running.
     *
     * @param now transition time
     * @throws IllegalStateException if the job is not queued
     */
    public void start(Instant now) {
        if (status != PersonalityImportJobStatus.QUEUED) {
            throw new IllegalStateException("Only a queued personality import can start.");
        }
        status = PersonalityImportJobStatus.RUNNING;
        updatedAt = now;
    }

    /**
     * Restores an interrupted job to the queue after an application restart.
     *
     * @param now recovery time
     */
    public void requeue(Instant now) {
        if (status == PersonalityImportJobStatus.RUNNING) {
            status = PersonalityImportJobStatus.QUEUED;
            updatedAt = now;
        }
    }

    /**
     * Records the terminal aggregate result.
     *
     * @param status completed state
     * @param successfulFiles number of successfully processed files
     * @param savedTags number of saved interest tags
     * @param analysedMedia number of analysed media items
     * @param failures JSON-encoded per-file failures
     * @param error terminal job error, when no file could be processed
     * @param now transition time
     */
    public void complete(
            PersonalityImportJobStatus status,
            int successfulFiles,
            int savedTags,
            int analysedMedia,
            String failures,
            String error,
            Instant now
    ) {
        if (this.status != PersonalityImportJobStatus.RUNNING
                || status != PersonalityImportJobStatus.COMPLETED
                && status != PersonalityImportJobStatus.COMPLETED_WITH_ERRORS
                && status != PersonalityImportJobStatus.FAILED) {
            throw new IllegalStateException("Invalid personality import completion transition.");
        }
        this.status = status;
        this.successfulFileCount = successfulFiles;
        this.savedTagCount = savedTags;
        this.analysedMediaCount = analysedMedia;
        this.failuresJson = failures;
        this.errorMessage = error;
        this.updatedAt = now;
    }
}
