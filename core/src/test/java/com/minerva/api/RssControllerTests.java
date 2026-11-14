package com.minerva.api;

import com.minerva.utils.FileManager;
import com.minerva.utils.RssData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class RssControllerTests {
    @Test
    void addsFeedThroughFileManager() throws IOException {
        var fileManager = mock(FileManager.class);
        var controller = new RssController(fileManager);

        controller.addRssFeed("BBC News", "https://feeds.bbci.co.uk/news/uk/rss.xml");

        verify(fileManager).AddToRssFeeds(argThat(feed ->
                feed.getTitle().equals("BBC News")
                        && feed.getLink().equals("https://feeds.bbci.co.uk/news/uk/rss.xml")));
    }

    @Test
    void returnsAllFeeds() throws IOException {
        var fileManager = mock(FileManager.class);
        var feeds = List.of(createFeed("The Guardian", "https://www.theguardian.com/uk/rss"));
        when(fileManager.GetRssFeeds()).thenReturn(feeds);
        var controller = new RssController(fileManager);

        assertEquals(feeds, controller.getRssFeeds());
        verify(fileManager).GetRssFeeds();
    }

    @Test
    void returnsFeedsMatchingName() throws IOException {
        var fileManager = mock(FileManager.class);
        var feeds = List.of(createFeed("BBC News", "https://feeds.bbci.co.uk/news/uk/rss.xml"));
        when(fileManager.GetRssFeedsByName("BBC")).thenReturn(feeds);
        var controller = new RssController(fileManager);

        assertEquals(feeds, controller.getRssFeedByName("BBC"));
        verify(fileManager).GetRssFeedsByName("BBC");
    }

    @Test
    void deletesFeedByUrl() throws IOException {
        var fileManager = mock(FileManager.class);
        var controller = new RssController(fileManager);
        String url = "https://www.theguardian.com/uk/rss";

        controller.DeleteRssFeedByUrl(url);

        verify(fileManager).DeleteRssFeedByUrl(url);
    }

    @Test
    void deletesFeedByName() throws IOException {
        var fileManager = mock(FileManager.class);
        var controller = new RssController(fileManager);

        controller.DeleteRssFeedByName("BBC News");

        verify(fileManager).DeleteRssFeedByName("BBC News");
    }

    private static RssData createFeed(String title, String url) {
        var feed = new RssData();
        feed.setTitle(title);
        feed.setLink(url);
        return feed;
    }
}
