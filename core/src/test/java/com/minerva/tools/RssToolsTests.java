package com.minerva.tools;

import com.minerva.utils.FileManager;
import com.minerva.utils.RssData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class RssToolsTests {
    @TempDir
    Path tempDirectory;

    @Test
    void returnsAtMostTenHeadlinesFromMatchingFeed() throws IOException {
        var feedFile = tempDirectory.resolve("feed.xml");
        Files.writeString(feedFile, createFeedXml(12));
        var feed = new RssData();
        feed.setTitle("BBC News");
        feed.setLink(feedFile.toUri().toString());
        var fileManager = mock(FileManager.class);
        when(fileManager.GetRssFeedsByName("BBC")).thenReturn(List.of(feed));
        var rssTools = new RssTools(fileManager);

        List<String> headlines = rssTools.getHeadlines("BBC");

        assertEquals(10, headlines.size());
        assertEquals("Headline 1", headlines.getFirst());
        assertEquals("Headline 10", headlines.getLast());
        verify(fileManager).GetRssFeedsByName("BBC");
    }

    @Test
    void returnsNoHeadlinesWhenFeedNameDoesNotMatch() throws IOException {
        var fileManager = mock(FileManager.class);
        when(fileManager.GetRssFeedsByName("Missing")).thenReturn(List.of());
        var rssTools = new RssTools(fileManager);

        assertEquals(List.of(), rssTools.getHeadlines("Missing"));
    }

    private static String createFeedXml(int itemCount) {
        var xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Test feed</title>
                    <link>https://example.com</link>
                    <description>Test feed</description>
                """);
        for (int i = 1; i <= itemCount; i++) {
            xml.append("<item><title>Headline ").append(i).append("</title></item>");
        }
        return xml.append("</channel></rss>").toString();
    }
}
