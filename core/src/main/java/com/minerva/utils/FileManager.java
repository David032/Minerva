package com.minerva.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileManager {
    final Logger logger = LoggerFactory.getLogger(FileManager.class);
    private static FileManager single_instance = null;
    private final Path minervaPath;
    private final Path uploadPath;
    private final Path skillsPath;
    private final Path memoriesPath;
    private final Path logsPath;

    private FileManager() {
        single_instance = this;
        String userHome = System.getProperty("user.home");
        minervaPath = Paths.get(userHome, "MinervaOS");
        uploadPath = minervaPath.resolve("uploads");
        skillsPath = minervaPath.resolve("skills");
        memoriesPath = minervaPath.resolve("memories");
        logsPath = minervaPath.resolve("logs");
        createDirectories(true);
    }

    FileManager(Path root) {
        minervaPath = root;
        uploadPath = root.resolve("uploads");
        skillsPath = root.resolve("skills");
        memoriesPath = root.resolve("memories");
        logsPath = root.resolve("logs");
        createDirectories(false);
    }

    /// <summary>
    /// Gets the singleton instance of the FileManager class.
    /// </summary>
    /// @return The singleton instance of the FileManager class.
    public static synchronized FileManager getInstance() {
        if (single_instance == null)
            single_instance = new FileManager();

        return single_instance;
    }

    /// <summary>
    /// Creates the necessary directories for the Minerva to function properly.
    /// </summary>
    public void CreateDirectories() {
        createDirectories(true);
    }

    private void createDirectories(boolean publishSystemProperties) {
        try {
            Files.createDirectories(minervaPath);
            Files.createDirectories(uploadPath);
            Files.createDirectories(skillsPath);
            Files.createDirectories(memoriesPath);
            Files.createDirectories(logsPath);
            logger.info("Created/verified MinervaOS Directory");
            logger.debug("Located at: {}", uploadPath);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        if (publishSystemProperties) {
            System.setProperty("app.dir", minervaPath.toAbsolutePath().toString());
            System.setProperty("app.dir.uploads", uploadPath.toAbsolutePath().toString());
            System.setProperty("app.dir.skills", skillsPath.toAbsolutePath().toString());
            System.setProperty("app.dir.memories", memoriesPath.toAbsolutePath().toString());
            System.setProperty("app.dir.logs", logsPath.toAbsolutePath().toString());
        }
    }

    public Path UploadFile(String fileName, MultipartFile content) throws IOException {
        return Files.write(uploadPath.resolve(fileName), content.getBytes());
    }

    /// <summary>
    /// Deletes a file from the uploads directory.
    /// </summary>
    /// @param fileName The name of the file to delete.
    public void DeleteFile(String fileName) {
        Path path = uploadPath.resolve(fileName);
        try {
            Files.delete(path);
        }  catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /// <summary>
    /// Gets the path to a specific directory.
    /// </summary>
    /// @param directory The directory to get the path for.
    /// @return The path to the specified directory.
    public String getDirectoryPath(Directory directory) {
        return switch (directory) {
            case UPLOADS -> uploadPath.toAbsolutePath().toString();
            case SKILLS -> skillsPath.toAbsolutePath().toString();
            case MEMORIES -> memoriesPath.toAbsolutePath().toString();
            case LOGS -> logsPath.toAbsolutePath().toString();
        };
    }

    /// <summary>
    /// Gets a file from the specified path.
    /// </summary>
    /// @param pathToFile The path to the file to get.
    /// @return The file at the specified path.
    public File GetFile(Path pathToFile) {
        return pathToFile.toFile();
    }

    public void AddToRssFeeds(RssData rssData) throws IOException {
        List<RssData> rssDataList = readRssFeeds();
        rssDataList.add(rssData);
        writeRssFeeds(rssDataList);
    }

    public List<RssData> GetRssFeeds() throws IOException {
        return readRssFeeds();
    }

    public List<RssData> GetRssFeedsByName(String name) throws IOException {
        if (name == null || name.isBlank()) {
            return new ArrayList<>();
        }

        String searchName = name.toLowerCase(Locale.ROOT);
        return readRssFeeds().stream()
                .filter(feed -> feed.getTitle() != null
                        && feed.getTitle().toLowerCase(Locale.ROOT).contains(searchName))
                .toList();
    }

    public void DeleteRssFeedByName(String name) throws IOException {
        deleteRssFeeds(feed -> name.equals(feed.getTitle()));
    }

    public void DeleteRssFeedByUrl(String url) throws IOException {
        deleteRssFeeds(feed -> url.equals(feed.getLink()));
    }

    private List<RssData> readRssFeeds() throws IOException {
        Path feedsFile = memoriesPath.resolve("feeds.json");
        if (Files.notExists(feedsFile)) {
            return new ArrayList<>();
        }

        try (var reader = Files.newBufferedReader(feedsFile)) {
            List<RssData> feeds = new Gson().fromJson(reader, new TypeToken<List<RssData>>() {}.getType());
            return feeds == null ? new ArrayList<>() : feeds;
        }
    }

    private void deleteRssFeeds(java.util.function.Predicate<RssData> predicate) throws IOException {
        Path feedsFile = memoriesPath.resolve("feeds.json");
        if (Files.notExists(feedsFile)) {
            return;
        }

        List<RssData> feeds = readRssFeeds();
        if (feeds.removeIf(predicate)) {
            writeRssFeeds(feeds);
        }
    }

    private void writeRssFeeds(List<RssData> feeds) throws IOException {
        Path feedsFile = memoriesPath.resolve("feeds.json");
        try (var writer = Files.newBufferedWriter(feedsFile)) {
            new Gson().toJson(feeds, writer);
        }
    }
}
