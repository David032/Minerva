package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        assertThrows(IllegalArgumentException.class, () -> PersonalityZipArchive.open(
                new MockMultipartFile("zipFile", "bad.zip", "application/zip", new byte[] {1, 2, 3})));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(zip(new Entry("../outside.html", new byte[] {1}))));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(zip(new Entry("C:/outside.html", new byte[] {1}))));
        assertThrows(IllegalArgumentException.class, () -> PersonalityZipArchive.open(zip()));
    }

    @Test
    void rejectsTooManyFilesAndOversizedExpandedEntries() throws Exception {
        Entry[] files = java.util.stream.IntStream.range(0, 1_001)
                .mapToObj(index -> new Entry(index + ".html", new byte[] {1}))
                .toArray(Entry[]::new);
        var countError = assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(zip(files)));
        assertTrue(countError.getMessage().contains("1,000 files"));
        var sizeError = assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(zip(new Entry("large.html", new byte[20 * 1024 * 1024 + 1]))));
        assertTrue(sizeError.getMessage().contains("20 MB"));
    }

    @Test
    void acceptsFileAndEntryLimitsAndRejectsAnExtraEntry() throws Exception {
        Entry[] thousandFiles = java.util.stream.IntStream.range(0, 1_000)
                .mapToObj(index -> new Entry(index + ".html", new byte[] {1}))
                .toArray(Entry[]::new);
        try (var staged = PersonalityZipArchive.open(zip(thousandFiles))) {
            assertEquals(1_000, staged.files().size());
        }

        var entries = new java.util.ArrayList<Entry>();
        entries.add(new Entry("page.html", new byte[] {1}));
        for (int index = 0; index < 1_999; index++) {
            entries.add(new Entry("folder" + index + "/", new byte[0]));
        }
        try (var staged = PersonalityZipArchive.open(zip(entries.toArray(Entry[]::new)))) {
            assertEquals(1, staged.files().size());
        }
        entries.add(new Entry("extra/", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(zip(entries.toArray(Entry[]::new))));
    }

    @Test
    void processesSevenHundredHtmlPages() throws Exception {
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), any(), any(), eq("I like these"))).thenReturn("I like these.");
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, mock(PersonalityImageOpinionService.class),
                new PersonalityPromptStore(directory.resolve("personality.md")),
                new InterestMemoryStore(directory.resolve("memories.md")));
        Entry[] pages = java.util.stream.IntStream.range(0, 700)
                .mapToObj(index -> new Entry("pages/" + index + ".html", "<p>Example</p>".getBytes(StandardCharsets.UTF_8)))
                .toArray(Entry[]::new);

        var result = service.processZip(zip(pages), "I like these");

        assertEquals(700, result.successfulFileCount());
        assertTrue(result.failures().isEmpty());
        verify(analyzer, org.mockito.Mockito.times(700)).analyze(any(), any(), any(), eq("I like these"));
    }

    @Test
    void enforcesStreamingSizeLimitsAndCleansUpTemporaryFiles() throws Exception {
        var archive = zip(new Entry("one.html", new byte[4]), new Entry("two.html", new byte[4]));
        var exact = new PersonalityZipArchive.Limits(archive.getSize(), 4, 8, 2, 2);
        try (var staged = PersonalityZipArchive.open(archive, directory, exact)) {
            assertEquals(2, staged.files().size());
            assertEquals(4, staged.files().getFirst().getBytes().length);
            try (var children = Files.list(directory)) {
                assertEquals(1, children.count());
            }
        }
        try (var children = Files.list(directory)) {
            assertEquals(0, children.count());
        }
        var expandedLimit = new PersonalityZipArchive.Limits(archive.getSize(), 4, 7, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(archive, directory, expandedLimit));
        var entryLimit = new PersonalityZipArchive.Limits(archive.getSize(), 3, 8, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(archive, directory, entryLimit));
        var countLimit = new PersonalityZipArchive.Limits(archive.getSize(), 4, 8, 1, 2);
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(archive, directory, countLimit));

        var misleadingUpload = mock(org.springframework.web.multipart.MultipartFile.class);
        when(misleadingUpload.isEmpty()).thenReturn(false);
        when(misleadingUpload.getSize()).thenReturn(1L);
        when(misleadingUpload.getInputStream()).thenReturn(new ByteArrayInputStream(archive.getBytes()));
        var compressedLimit = new PersonalityZipArchive.Limits(archive.getSize() - 1, 4, 8, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> PersonalityZipArchive.open(misleadingUpload, directory, compressedLimit));
        try (var children = Files.list(directory)) {
            assertEquals(0, children.count());
        }
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
