package com.minerva.tools;

import com.apptasticsoftware.rssreader.RssReader;
import com.minerva.utils.FileManager;
import com.minerva.utils.RssData;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Exposes saved RSS feeds and recent headlines to the conversational assistant.
 */
@Component
public class RssTools {

    private final FileManager fileManager;

    public RssTools() {
        this(FileManager.getInstance());
    }

    RssTools(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    /**
     * Lists the names and links of saved RSS feeds.
     *
     * @return saved feed summaries
     * @throws IOException if the feed file cannot be read
     */
    @Tool(description = "List the titles and URLs of all saved RSS feeds")
    public List<String> listFeeds() throws IOException {
        return fileManager.GetRssFeeds().stream()
                .map(feed -> (feed.getTitle() == null ? "Untitled" : feed.getTitle())
                        + ": " + feed.getLink())
                .toList();
    }

    /**
     * Returns the latest headlines from a named RSS feed.
     *
     * @param titleOfFeed saved feed name
     * @return up to ten recent headlines
     * @throws IOException if the feed file or feed URL cannot be read
     */
    @Tool(description = "Get the latest 10 headlines from a saved RSS feed, selected by its name")
    public List<String> getHeadlines(String titleOfFeed) throws IOException {
        List<RssData> matchingFeeds = fileManager.GetRssFeedsByName(titleOfFeed);
        if (matchingFeeds.isEmpty()) {
            return List.of();
        }

        RssData feed = matchingFeeds.stream()
                .filter(candidate -> candidate.getTitle() != null
                        && candidate.getTitle().equalsIgnoreCase(titleOfFeed))
                .findFirst()
                .orElse(matchingFeeds.getFirst());

        try (var items = new RssReader().read(feed.getLink())) {
            return items.map(item -> item.getTitle())
                    .flatMap(Optional::stream)
                    .filter(title -> !title.isBlank())
                    .limit(10)
                    .toList();
        }
    }
}
