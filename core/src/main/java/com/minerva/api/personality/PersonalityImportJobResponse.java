package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityImportJobStatus;
import com.minerva.core.personality.PersonalityImportKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public status and aggregate result for a personality import job.
 *
 * @param id stable job identifier used by the status endpoint
 * @param kind source type being imported
 * @param status current lifecycle state
 * @param createdAt job creation time
 * @param updatedAt time of the latest lifecycle update
 * @param successfulFileCount number of successfully processed files
 * @param savedTagCount number of saved interest tags
 * @param analysedMediaCount number of analysed media items
 * @param failures per-file failures
 * @param error job-level failure, when present
 */
public record PersonalityImportJobResponse(
        UUID id,
        PersonalityImportKind kind,
        PersonalityImportJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        int successfulFileCount,
        int savedTagCount,
        int analysedMediaCount,
        List<MediaPersonalityService.FileFailure> failures,
        String error
) {
    /**
     * Creates a response with an immutable failure list.
     *
     * @param id stable job identifier
     * @param kind source type
     * @param status lifecycle state
     * @param createdAt creation time
     * @param updatedAt latest update time
     * @param successfulFileCount successful files
     * @param savedTagCount saved interest tags
     * @param analysedMediaCount analysed media items
     * @param failures per-file failures
     * @param error job-level failure
     */
    public PersonalityImportJobResponse {
        failures = List.copyOf(failures);
    }
}
