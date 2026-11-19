package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class PersonalityZipTests {
    @TempDir
    Path directory;

    @Test
    void processesMixedFilesWithSharedGuidanceAndReportsFailures() throws Exception {
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        var opinion = mock(PersonalityImageOpinionService.class);
        var promptStore = new PersonalityPromptStore(directory.resolve("personality.md"));
        URI imageUrl = URI.create("https://example.test/image.png");
        var remoteImage = new RemoteMediaFetcher.MediaAsset(imageUrl, "image/png", new byte[] {1});
        when(fetcher.fetch(List.of(imageUrl))).thenReturn(List.of(remoteImage));
        when(opinion.analyze(any(), eq("I like this"))).thenReturn("A garden.");
        when(analyzer.analyze(any(), any(), any(), eq("I like this"))).thenReturn("I like gardens.");
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, opinion, promptStore,
                new InterestMemoryStore(directory.resolve("memories.md")));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        var archive = zip(
                new Entry("folder/page.html", "<p>Garden</p><img src=\"https://example.test/image.png\">"
                        .getBytes(StandardCharsets.UTF_8)),
                new Entry("folder/photo.png", png),
                new Entry("folder/broken.png", new byte[] {1}),
                new Entry("folder/notes.txt", new byte[] {1}));

        var result = service.processZip(archive, "I like this");

        assertEquals(2, result.successfulFileCount());
        assertEquals(2, result.analyzedMediaCount());
        assertEquals(List.of("folder/broken.png", "folder/notes.txt"),
                result.failures().stream().map(MediaPersonalityService.FileFailure::filename).toList());
        assertEquals("I like gardens.", promptStore.load());
        verify(fetcher).fetch(List.of(imageUrl));
        verify(opinion).analyze(any(), eq("I like this"));
        verify(analyzer).analyze(any(), eq(List.of(remoteImage)), eq(""), eq("I like this"));
        verify(analyzer).analyze(any(), eq(List.of()), eq("I like gardens."), eq("I like this"));
    }

    @Test
    void rejectsMalformedUnsafeAndEmptyArchives() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PersonalityZipArchive.read(
                new MockMultipartFile("zipFile", "bad.zip", "application/zip", new byte[] {1, 2, 3})));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.read(zip(new Entry("../outside.html", new byte[] {1}))));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.read(zip(new Entry("C:/outside.html", new byte[] {1}))));
        assertThrows(IllegalArgumentException.class, () -> PersonalityZipArchive.read(zip()));
    }

    @Test
    void rejectsTooManyFilesAndOversizedExpandedEntries() throws Exception {
        Entry[] files = java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> new Entry(index + ".html", new byte[] {1}))
                .toArray(Entry[]::new);
        var countError = assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.read(zip(files)));
        assertTrue(countError.getMessage().contains("50 files"));
        var sizeError = assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.read(zip(new Entry("large.html", new byte[20 * 1024 * 1024 + 1]))));
        assertTrue(sizeError.getMessage().contains("20 MB"));
    }

    @Test
    void validatesAllPathsBeforeSavingAnyPreferences() throws Exception {
        var promptStore = new PersonalityPromptStore(directory.resolve("personality.md"));
        promptStore.save("Existing preferences.");
        var analyzer = mock(PersonalityAnalyzer.class);
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), mock(RemoteMediaFetcher.class),
                new MediaPreprocessor(video -> List.of()), analyzer, mock(PersonalityImageOpinionService.class),
                promptStore, new InterestMemoryStore(directory.resolve("memories.md")));
        var archive = zip(new Entry("good.html", "<p>Example</p>".getBytes(StandardCharsets.UTF_8)),
                new Entry("../bad.html", new byte[] {1}));

        assertThrows(IllegalArgumentException.class, () -> service.processZip(archive, "I like this"));
        assertEquals("Existing preferences.", promptStore.load());
        org.mockito.Mockito.verifyNoInteractions(analyzer);
    }

    private static MockMultipartFile zip(Entry... entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("zipFile", "personality.zip", "application/zip", bytes.toByteArray());
    }

    private record Entry(String name, byte[] content) {
    }
}
