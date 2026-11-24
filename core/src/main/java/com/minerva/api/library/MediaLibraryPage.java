package com.minerva.api.library;

import java.util.List;

/**
 * A bounded page of library items in descending storage order.
 *
 * @param items items in this page
 * @param nextCursor opaque cursor for the next page, or {@code null} at the end
 */
public record MediaLibraryPage(List<MediaLibraryItem> items, String nextCursor) {
    /**
     * Creates a page with an immutable item list.
     *
     * @param items item metadata in descending order
     * @param nextCursor cursor for the next page, or {@code null}
     */
    public MediaLibraryPage {
        items = List.copyOf(items);
    }
}
