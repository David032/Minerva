package com.minerva.utils;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class FileManagerTests {
    @TempDir
    Path tempDirectory;

    @Test
    void createsExpectedDirectoriesUnderConfiguredRoot() {
        var manager = new FileManager(tempDirectory.resolve("MinervaOS"));

        assertTrue(Files.isDirectory(Path.of(manager.getDirectoryPath(Directory.UPLOADS))));
        assertTrue(Files.isDirectory(Path.of(manager.getDirectoryPath(Directory.SKILLS))));
        assertTrue(Files.isDirectory(Path.of(manager.getDirectoryPath(Directory.MEMORIES))));
        assertTrue(Files.isDirectory(Path.of(manager.getDirectoryPath(Directory.LOGS))));
    }

    @Test
    void writesReadsAndDeletesUploadedFile() throws IOException {
        var manager = new FileManager(tempDirectory.resolve("MinervaOS"));
        var contents = new byte[] {1, 2, 3, 4};
        var upload = new MockMultipartFile("upload", "sample.bin", "application/octet-stream", contents);

        Path savedPath = manager.UploadFile(upload.getOriginalFilename(), upload);

        assertArrayEquals(contents, Files.readAllBytes(savedPath));
        assertEquals(savedPath.toFile(), manager.GetFile(savedPath));

        manager.DeleteFile(upload.getOriginalFilename());

        assertFalse(Files.exists(savedPath));
    }

    @Test
    void propagatesUploadFailures() {
        var manager = new FileManager(tempDirectory.resolve("MinervaOS"));
        var upload = new MockMultipartFile("upload", "sample.bin", "application/octet-stream", new byte[] {1});

        assertThrows(IOException.class, () -> manager.UploadFile("missing/sample.bin", upload));
    }

    @Test
    void addsRssFeedsToMemoriesFile() throws IOException {
        var manager = new FileManager(tempDirectory.resolve("MinervaOS"));
        var firstFeed = new RssData();
        firstFeed.setTitle("First feed");
        firstFeed.setLink("https://example.com/first");
        firstFeed.setDescription("First description");
        var secondFeed = new RssData();
        secondFeed.setTitle("Second feed");
        secondFeed.setLink("https://example.com/second");
        secondFeed.setDescription("Second description");

        manager.AddToRssFeeds(firstFeed);
        manager.AddToRssFeeds(secondFeed);

        Path feedsFile = Path.of(manager.getDirectoryPath(Directory.MEMORIES)).resolve("feeds.json");
        var feeds = JsonParser.parseString(Files.readString(feedsFile)).getAsJsonArray();
        assertEquals(2, feeds.size());
        assertEquals("First feed", feeds.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("https://example.com/second", feeds.get(1).getAsJsonObject().get("link").getAsString());
    }

    @Test
    void getsAndDeletesRssFeedsByNameOrUrl() throws IOException {
        var manager = new FileManager(tempDirectory.resolve("MinervaOS"));
        var firstFeed = new RssData();
        firstFeed.setTitle("First feed");
        firstFeed.setLink("https://example.com/first");
        var secondFeed = new RssData();
        secondFeed.setTitle("Second feed");
        secondFeed.setLink("https://example.com/second");
        manager.AddToRssFeeds(firstFeed);
        manager.AddToRssFeeds(secondFeed);

        assertEquals(2, manager.GetRssFeeds().size());
        assertEquals(1, manager.GetRssFeedsByName("first feed").size());
        assertEquals(2, manager.GetRssFeedsByName("feed").size());
        assertTrue(manager.GetRssFeedsByName("missing").isEmpty());

        manager.DeleteRssFeedByName("First feed");
        assertEquals(1, manager.GetRssFeeds().size());
        assertEquals("Second feed", manager.GetRssFeeds().get(0).getTitle());

        manager.DeleteRssFeedByUrl("https://example.com/second");
        assertTrue(manager.GetRssFeeds().isEmpty());
    }
}
