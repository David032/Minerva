package com.minerva.api.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class MediaLibraryStoreTests {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsMetadataTextAndContentAcrossStoreInstances() throws IOException {
        byte[] content = "A thoughtful article.".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var index = index();
        var store = new MediaLibraryStore(tempDirectory, index);
        MediaLibraryItem saved = store.save(
                "A thoughtful article", "https://example.com/article", "text/plain",
                "article.txt", "Extracted article text", "Relevant to Minerva's interests", content);

        var restored = new MediaLibraryStore(tempDirectory, index);

        assertEquals(saved, restored.getDetails(UUID.fromString(saved.id())).item());
        assertEquals("Extracted article text",
                restored.getDetails(UUID.fromString(saved.id())).extractedText());
        assertArrayEquals(content, restored.readContent(UUID.fromString(saved.id())));
        assertEquals(saved, restored.list().getFirst());
    }

    @Test
    void sanitisesDisplayFilenamesAndRemovesSavedContent() throws IOException {
        var store = new MediaLibraryStore(tempDirectory, index());
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

    @Test
    void reconcilesSavedFilesAfterProjectionFailure() throws IOException {
        var failingIndex = index();
        doAnswer(invocation -> { throw new IllegalStateException("database unavailable"); })
                .when(failingIndex).insert(any());
        var store = new MediaLibraryStore(tempDirectory, failingIndex);
        assertThrows(IllegalStateException.class, () -> store.save(
                "Article", null, "text/plain", "article.txt", "Text", "Relevant", new byte[]{1}));
        assertEquals(1, store.list().size());

        var restored = new MediaLibraryStore(tempDirectory, index());
        assertEquals("Article", restored.list().getFirst().title());
    }

    @Test
    void recoversInterruptedDeletionBeforeAndAfterContentRemoval() throws IOException {
        var store = new MediaLibraryStore(tempDirectory, index());
        var item = store.save("Article", null, "text/plain", "article.txt", "Text", "Relevant", new byte[]{1});
        Path metadata = tempDirectory.resolve(item.id() + ".json");
        Path tombstone = tempDirectory.resolve(item.id() + ".json.deleting");
        Files.move(metadata, tombstone);

        var restored = new MediaLibraryStore(tempDirectory, index());
        assertEquals(item, restored.list().getFirst());
        assertTrue(Files.exists(metadata));

        Files.move(metadata, tombstone);
        Files.delete(tempDirectory.resolve(item.id() + ".txt"));
        assertTrue(new MediaLibraryStore(tempDirectory, index()).list().isEmpty());
        assertFalse(Files.exists(tombstone));
    }

    @Test
    void reportsInvalidMetadataAndMissingContentDuringReconciliation() throws IOException {
        var store = new MediaLibraryStore(tempDirectory, index());
        var item = store.save("Article", null, "text/plain", "article.txt", "Text", "Relevant", new byte[]{1});
        Files.delete(tempDirectory.resolve(item.id() + ".txt"));
        assertThrows(IOException.class, () -> new MediaLibraryStore(tempDirectory, index()).list());
        Files.writeString(tempDirectory.resolve(item.id() + ".json"), "not json");
        assertThrows(IOException.class, () -> new MediaLibraryStore(tempDirectory, index()).list());
    }

    @Test
    void repeatedPagesDoNotRebuildOrReadMetadataFiles() throws IOException {
        var index = index();
        var store = new MediaLibraryStore(tempDirectory, index);
        var item = store.save("Article", null, "text/plain", "article.txt", "Text", "Relevant", new byte[]{1});
        when(index.page(10, null)).thenReturn(new MediaLibraryPage(List.of(item), null));
        Files.writeString(tempDirectory.resolve(item.id() + ".json"), "invalid");

        assertEquals(List.of(item), store.page(10, null).items());
        assertEquals(List.of(item), store.page(10, null).items());
        verify(index, times(1)).rebuild(any());
    }

    private static MediaLibraryIndex index() {
        MediaLibraryIndex index = mock(MediaLibraryIndex.class);
        List<MediaLibraryItem> entries = new ArrayList<>();
        doAnswer(invocation -> {
            Iterator<MediaLibraryItem> items = invocation.getArgument(0);
            List<MediaLibraryItem> replacement = new ArrayList<>();
            items.forEachRemaining(replacement::add);
            entries.clear();
            entries.addAll(replacement);
            return null;
        }).when(index).rebuild(any());
        doAnswer(invocation -> {
            entries.add(invocation.getArgument(0));
            return null;
        }).when(index).insert(any());
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            entries.removeIf(item -> item.id().equals(id.toString()));
            return null;
        }).when(index).delete(any());
        when(index.list()).thenAnswer(invocation -> entries.stream()
                .sorted(Comparator.comparing(MediaLibraryItem::storedAt).reversed()).toList());
        return index;
    }
}
