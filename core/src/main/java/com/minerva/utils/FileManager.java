package com.minerva.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileManager {
    final Logger logger = LoggerFactory.getLogger(FileManager.class);
    private static FileManager single_instance = null;
    private FileManager() {
        single_instance = this;
        CreateDirectories();
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
        String userHome = System.getProperty("user.home");
        Path MinervaPath = Paths.get(userHome, "MinervaOS");
        Path uploadPath = Paths.get(userHome, "MinervaOS","uploads");
        Path skillsPath = Paths.get(userHome, "MinervaOS","skills");
        Path memoriesPath = Paths.get(userHome, "MinervaOS","memories");
        Path logsPath = Paths.get(userHome, "MinervaOS","logs");

        try {
            Files.createDirectories(MinervaPath);
            Files.createDirectories(uploadPath);
            Files.createDirectories(skillsPath);
            Files.createDirectories(memoriesPath);
            Files.createDirectories(logsPath);
            logger.info("Created/verified MinervaOS Directory");
            logger.debug("Located at: {}", uploadPath);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        System.setProperty("app.dir", MinervaPath.toAbsolutePath().toString());
        System.setProperty("app.dir.uploads", uploadPath.toAbsolutePath().toString());
        System.setProperty("app.dir.skills", skillsPath.toAbsolutePath().toString());
        System.setProperty("app.dir.memories", memoriesPath.toAbsolutePath().toString());
        System.setProperty("app.dir.logs", logsPath.toAbsolutePath().toString());
    }

    public Path UploadFile(String fileName, MultipartFile content) throws IOException {
        return Files.write(Path.of(System.getProperty("app.dir.uploads") + "/" + fileName), content.getBytes());
    }

    /// <summary>
    /// Deletes a file from the uploads directory.
    /// </summary>
    /// @param fileName The name of the file to delete.
    public void DeleteFile(String fileName) {
        Path path = Paths.get(System.getProperty("app.dir.uploads") + "/" + fileName);
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
            case UPLOADS -> System.getProperty("app.dir.uploads");
            case SKILLS -> System.getProperty("app.dir.skills");
            case MEMORIES -> System.getProperty("app.dir.memories");
            case LOGS -> System.getProperty("app.dir.logs");
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
}
