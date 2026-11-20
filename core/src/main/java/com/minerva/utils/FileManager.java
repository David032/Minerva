package com.minerva.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileManager {
    final Logger logger = LoggerFactory.getLogger(FileManager.class);
    private static FileManager single_instance = null;
    private final Path minervaPath;
    private final Path uploadPath;
    private final Path libraryPath;
    private final Path skillsPath;
    private final Path memoriesPath;
    private final Path logsPath;

    private FileManager() {
        String configuredDataDirectory = System.getenv("MINERVA_DATA_DIR");
        minervaPath = configuredDataDirectory == null || configuredDataDirectory.isBlank()
                ? Paths.get(System.getProperty("user.home"), "MinervaOS")
                : Paths.get(configuredDataDirectory);
        uploadPath = minervaPath.resolve("uploads");
        libraryPath = minervaPath.resolve("library");
        skillsPath = minervaPath.resolve("skills");
        memoriesPath = minervaPath.resolve("memories");
        logsPath = minervaPath.resolve("logs");
        createDirectories(true);
    }

    FileManager(Path root) {
        minervaPath = root;
        uploadPath = root.resolve("uploads");
        libraryPath = root.resolve("library");
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
        createDirectory(minervaPath, "data root");
        migrateLegacyData();
        createDirectory(uploadPath, "uploads");
        createDirectory(libraryPath, "library");
        createDirectory(skillsPath, "skills");
        createDirectory(memoriesPath, "memories");
        createDirectory(logsPath, "logs");
        if (publishSystemProperties) {
            System.setProperty("app.dir", minervaPath.toAbsolutePath().toString());
            System.setProperty("app.dir.uploads", uploadPath.toAbsolutePath().toString());
            System.setProperty("app.dir.skills", skillsPath.toAbsolutePath().toString());
            System.setProperty("app.dir.memories", memoriesPath.toAbsolutePath().toString());
            System.setProperty("app.dir.logs", logsPath.toAbsolutePath().toString());
        }
    }

    private void createDirectory(Path path, String name) {
        boolean alreadyExists = Files.isDirectory(path);
        try {
            Files.createDirectories(path);
            logger.info("Minerva {} directory {}: {}", name,
                    alreadyExists ? "already exists" : "created", path.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create Minerva {} directory at {}", name, path.toAbsolutePath(), e);
            throw new IllegalStateException("Could not create Minerva " + name + " directory at "
                    + path.toAbsolutePath(), e);
        }
    }

    private void migrateLegacyData() {
        Path legacyRoot = minervaPath.resolve("app").resolve("MinervaOS");
        if (!Files.isDirectory(legacyRoot)) {
            return;
        }

        try (var entries = Files.list(legacyRoot)) {
            for (Path entry : entries.toList()) {
                if (entry.getFileName().toString().startsWith(".legacy-migration-")) {
                    continue;
                }
                Path destination = minervaPath.resolve(entry.getFileName());
                if (Files.exists(destination)) {
                    logger.warn("Keeping existing Minerva data at {}; legacy data remains at {}",
                            destination.toAbsolutePath(), entry.toAbsolutePath());
                    continue;
                }

                Path staging = Files.createTempDirectory(minervaPath, ".legacy-migration-");
                try {
                    Path stagedEntry = staging.resolve(entry.getFileName());
                    copyTree(entry, stagedEntry);
                    Files.move(stagedEntry, destination);
                    logger.info("Migrated legacy Minerva data from {} to {}",
                            entry.toAbsolutePath(), destination.toAbsolutePath());
                } catch (IOException e) {
                    logger.error("Failed to migrate legacy Minerva data from {} to {}",
                            entry.toAbsolutePath(), destination.toAbsolutePath(), e);
                    throw new IllegalStateException("Could not migrate legacy Minerva data from "
                            + entry.toAbsolutePath() + " to " + destination.toAbsolutePath(), e);
                } finally {
                    try {
                        deleteTree(staging);
                    } catch (IOException e) {
                        logger.error("Failed to remove Minerva migration staging directory {}",
                                staging.toAbsolutePath(), e);
                        throw new IllegalStateException("Could not remove Minerva migration staging directory at "
                                + staging.toAbsolutePath(), e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to inspect legacy Minerva data at {}", legacyRoot.toAbsolutePath(), e);
            throw new IllegalStateException("Could not inspect legacy Minerva data at "
                    + legacyRoot.toAbsolutePath(), e);
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Symbolic links are not allowed in legacy data: " + file);
                }
                Files.copy(file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
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
            logger.error("Failed to delete uploaded file at {}", path.toAbsolutePath(), e);
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
            case LIBRARY -> libraryPath.toAbsolutePath().toString();
            case SKILLS -> skillsPath.toAbsolutePath().toString();
            case MEMORIES -> memoriesPath.toAbsolutePath().toString();
            case LOGS -> logsPath.toAbsolutePath().toString();
        };
    }

    public Path getPersonalityPromptPath() {
        return minervaPath.resolve("personality.md");
    }

    /**
     * Gets the local heartbeat instruction file path.
     *
     * @return path to {@code heartbeat.md} in Minerva's data root
     */
    public Path getHeartbeatFilePath() {
        return minervaPath.resolve("heartbeat.md");
    }

    /**
     * Creates the application default heartbeat when personality and interest data are available.
     *
     * <p>An existing heartbeat file is never replaced.</p>
     *
     * @throws IOException if the default resource or heartbeat file cannot be read or written
     */
    public void ensureDefaultHeartbeatFile() throws IOException {
        Path heartbeatPath = getHeartbeatFilePath();
        if (Files.exists(heartbeatPath)) {
            return;
        }
        Path personalityPath = getPersonalityPromptPath();
        if (!Files.isRegularFile(personalityPath)
                || Files.readString(personalityPath, StandardCharsets.UTF_8).isBlank()) {
            return;
        }
        Path memoriesPath = this.memoriesPath.resolve("memories.md");
        if (!Files.isRegularFile(memoriesPath)) {
            return;
        }
        String memories = Files.readString(memoriesPath, StandardCharsets.UTF_8);
        if (!memories.contains("<!-- minerva-media-interests:start -->")
                && !memories.contains("<!-- minerva-scored-interests:start -->")) {
            return;
        }

        try (InputStream resource = FileManager.class.getResourceAsStream("/heartbeat/default-heartbeat.md")) {
            if (resource == null) {
                throw new IOException("Default heartbeat resource was not found.");
            }
            Files.createDirectories(minervaPath);
            try {
                Files.write(heartbeatPath, resource.readAllBytes(),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                logger.info("Created default heartbeat instructions at {}", heartbeatPath.toAbsolutePath());
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another startup or heartbeat tick created the file first.
            }
        }
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
