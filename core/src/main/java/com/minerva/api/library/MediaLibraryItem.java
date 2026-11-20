package com.minerva.api.library;

import java.time.Instant;

/**
 * Public metadata for one media item accepted into Minerva's interest library.
 *
 * @param id stable library identifier
 * @param title display title
 * @param source original source URL, or {@code null} for a direct file upload
 * @param contentType validated media content type
 * @param fileName safe display filename
 * @param storedAt time the item was accepted
 * @param curationReason concise reason the item was accepted
 */
public record MediaLibraryItem(
        String id,
        String title,
        String source,
        String contentType,
        String fileName,
        Instant storedAt,
        String curationReason
) {
}
