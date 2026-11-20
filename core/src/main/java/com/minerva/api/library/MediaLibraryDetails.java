package com.minerva.api.library;

/**
 * A library item and the text extracted for Minerva to review.
 *
 * @param item public item metadata
 * @param extractedText bounded plain-text content, when available
 */
public record MediaLibraryDetails(MediaLibraryItem item, String extractedText) {
}
