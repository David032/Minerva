package com.minerva.api.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class MediaLibraryStoreTests {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsMetadataTextAndContentAcrossStoreInstances() throws IOException {
        byte[] content = "A thoughtful article.".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var store = new MediaLibraryStore(tempDirectory);
        MediaLibraryItem saved = store.save(
                "A thoughtful article", "https://example.com/article", "text/plain",
                "article.txt", "Extracted article text", "Relevant to Minerva's interests", content);

        var restored = new MediaLibraryStore(tempDirectory);

        assertEquals(saved, restored.getDetails(UUID.fromString(saved.id())).item());
        assertEquals("Extracted article text",
                restored.getDetails(UUID.fromString(saved.id())).extractedText());
        assertArrayEquals(content, restored.readContent(UUID.fromString(saved.id())));
        assertEquals(saved, restored.list().getFirst());
    }

    @Test
    void sanitisesDisplayFilenamesAndRemovesSavedContent() throws IOException {
        var store = new MediaLibraryStore(tempDirectory);
        MediaLibraryItem saved = store.save(
                "Uploaded item", null, "application/pdf", "..\\secret.pdf",
                "Extracted text", "Relevant", new byte[]{1, 2, 3});

        assertEquals("secret.pdf", saved.fileName());
        assertTrue(Files.exists(tempDirectory.resolve(saved.id() + ".pdf")));

        assertTrue(store.delete(UUID.fromString(saved.id())));
        assertTrue(store.list().isEmpty());
        assertFalse(Files.exists(tempDirectory.resolve(saved.id() + ".pdf")));
        assertFalse(store.delete(UUID.fromString(saved.id())));
    }
}
