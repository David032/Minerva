package com.minerva.utils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Integration")
class RssFeedsIntegrationTests {
    private static final String GUARDIAN_URL = "https://www.theguardian.com/uk/rss";
    private static final String BBC_URL = "https://feeds.bbci.co.uk/news/uk/rss.xml";

    @TempDir
    Path tempDirectory;

    @Test
    void persistsFeedsAndDeletesThemByNameAndUrl() throws IOException {
        var manager = new FileManager(tempDirectory.resolve("MinervaOS"));
        manager.AddToRssFeeds(createFeed("The Guardian", GUARDIAN_URL));
        manager.AddToRssFeeds(createFeed("BBC News", BBC_URL));

        var reloadedManager = new FileManager(tempDirectory.resolve("MinervaOS"));
        var savedFeeds = reloadedManager.GetRssFeeds();
        assertEquals(2, savedFeeds.size());
        assertEquals(GUARDIAN_URL, savedFeeds.get(0).getLink());
        assertEquals(BBC_URL, savedFeeds.get(1).getLink());

        reloadedManager.DeleteRssFeedByName("The Guardian");
        assertEquals(1, reloadedManager.GetRssFeeds().size());
        assertEquals("BBC News", reloadedManager.GetRssFeeds().getFirst().getTitle());

        reloadedManager.DeleteRssFeedByUrl(BBC_URL);
        assertTrue(reloadedManager.GetRssFeeds().isEmpty());
    }

    private static RssData createFeed(String title, String url) {
        var feed = new RssData();
        feed.setTitle(title);
        feed.setLink(url);
        return feed;
    }
}
