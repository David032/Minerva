package com.minerva.api;

import com.apptasticsoftware.rssreader.RssReader;
import com.minerva.utils.FileManager;
import com.minerva.utils.RssData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

@Controller
@ResponseBody
public class RssController {
    private final FileManager fileManager;

    @Autowired
    RssController(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    @PostMapping
    void addRssFeed(String title, String url) throws IOException {
        var feedToAdd = new RssData();
        feedToAdd.setTitle(title);
        feedToAdd.setLink(url);
        fileManager.AddToRssFeeds(feedToAdd);
    }



}
