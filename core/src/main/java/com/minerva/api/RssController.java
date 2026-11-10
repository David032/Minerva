package com.minerva.api;

import com.minerva.utils.FileManager;
import com.minerva.utils.RssData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

@Controller
@ResponseBody
@RequestMapping("/rss")
public class RssController {
    private final FileManager fileManager;

    @Autowired
    RssController(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    @PostMapping()
    void addRssFeed(@RequestParam String title, @RequestParam String url) throws IOException {
        var feedToAdd = new RssData();
        feedToAdd.setTitle(title);
        feedToAdd.setLink(url);
        fileManager.AddToRssFeeds(feedToAdd);
    }

    @GetMapping(params = "!name")
    List<RssData> getRssFeeds() throws IOException {
        return fileManager.GetRssFeeds();
    }

    @GetMapping(params = "name")
    List<RssData> getRssFeedByName(@RequestParam String name) throws IOException {
        return fileManager.GetRssFeedsByName(name);
    }

    @DeleteMapping(params = "url")
    void DeleteRssFeedByUrl(@RequestParam String url) throws IOException {
        fileManager.DeleteRssFeedByUrl(url);
    }

    @DeleteMapping(params = "name")
    void DeleteRssFeedByName(@RequestParam String name) throws IOException {
        fileManager.DeleteRssFeedByName(name);
    }
}
