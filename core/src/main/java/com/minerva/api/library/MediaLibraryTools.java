package com.minerva.api.library;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Exposes Minerva's curated media library to the assistant as tools.
 */
@Component
public class MediaLibraryTools {
    private final MediaLibraryService service;

    /**
     * Creates tools backed by the shared library workflow.
     *
     * @param service validates, curates, and manages library items
     */
    public MediaLibraryTools(MediaLibraryService service) {
        this.service = service;
    }

    /**
     * Downloads and considers one public document, webpage, image, or video URL.
     *
     * @param url public media URL to consider
     * @return whether the item was selected and, if so, its saved metadata
     * @throws IOException if fetching, analysis, or persistence fails
     */
    @Tool(description = "Consider a public PDF, webpage, image, or video URL for Minerva's interest library. The item is stored only if the curation step accepts it.")
    public MediaLibraryService.Submission considerMediaUrl(String url) throws IOException {
        return service.submitUrl(url);
    }

    /**
     * Lists items already accepted into Minerva's library.
     *
     * @return saved item metadata
     * @throws IOException if saved metadata cannot be read
     */
    @Tool(description = "List media items Minerva has already selected for its interest library.")
    public List<MediaLibraryItem> listLibraryItems() throws IOException {
        return service.listItems();
    }

    /**
     * Retrieves saved metadata and extracted text for one library item.
     *
     * @param id item UUID from the library list
     * @return saved metadata and extracted text
     * @throws IOException if saved metadata cannot be read
     */
    @Tool(description = "Retrieve the metadata and extracted text for one item in Minerva's interest library.")
    public MediaLibraryDetails retrieveLibraryItem(String id) throws IOException {
        return service.getDetails(id);
    }

    /**
     * Removes one item from Minerva's library.
     *
     * @param id item UUID from the library list
     * @return confirmation that the item was removed
     * @throws IOException if saved data cannot be removed
     */
    @Tool(description = "Remove an item from Minerva's interest library by its item UUID.")
    public String removeLibraryItem(String id) throws IOException {
        if (!service.deleteItem(id)) {
            return "The media library item was not found.";
        }
        return "The media library item was removed.";
    }
}
