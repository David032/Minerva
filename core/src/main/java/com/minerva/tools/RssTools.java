package com.minerva.tools;

import com.apptasticsoftware.rssreader.RssReader;
import com.minerva.utils.FileManager;
import com.minerva.utils.RssData;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class RssTools {

    private final FileManager fileManager;

    public RssTools() {
        this(FileManager.getInstance());
    }

    RssTools(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    @Tool(description = "Get the latest 10 headlines from a saved RSS feed, selected by its name")
    List<String> getHeadlines(String titleOfFeed) throws IOException {
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
