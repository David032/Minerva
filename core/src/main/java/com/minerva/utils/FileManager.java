package com.minerva.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileManager {
    private static FileManager single_instance = null;
    private FileManager() throws IOException {
        single_instance = this;
        CreateDirectories();
    }

    public static synchronized FileManager getInstance() throws IOException {
        if (single_instance == null)
            single_instance = new FileManager();

        return single_instance;
    }

    public void CreateDirectories() throws IOException {
        String userHome = System.getProperty("user.home");
        Path MinervaPath = Paths.get(userHome, "MinervaOS");
        Path uploadPath = Paths.get(userHome, "MinervaOS","uploads");
        Path skillsPath = Paths.get(userHome, "MinervaOS","skills");

        try {
            Files.createDirectories(MinervaPath);
            Files.createDirectories(uploadPath);
            Files.createDirectories(skillsPath);
            System.out.println("Directories successfully verified/created at: " + MinervaPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create directory: " + e.getMessage());
        }
        System.setProperty("app.dir", MinervaPath.toAbsolutePath().toString());
        System.setProperty("app.dir.uploads", uploadPath.toAbsolutePath().toString());
        System.setProperty("app.dir.skills", skillsPath.toAbsolutePath().toString());
    }

    public Path UploadFile(String fileName, MultipartFile content) throws IOException {
        return Files.write(Path.of(System.getProperty("app.dir.uploads") + "/" + fileName), content.getBytes());
    }

    public void DeleteFile(String fileName) throws IOException {
        Path path = Paths.get(System.getProperty("app.dir.uploads") + "/" + fileName);
        try {
            Files.delete(path);
        }  catch (IOException e) {
            System.err.println("Failed to delete file: " + e.getMessage());
        }
    }

    public String getDirectoryPath(Directory directory) {
        return switch (directory) {
            case UPLOADS -> System.getProperty("app.dir.uploads");
            case SKILLS -> System.getProperty("app.dir.skills");
            default -> System.getProperty("app.dir");
        };
    }

    public File GetFile(Path pathToFile) throws IOException {
        return pathToFile.toFile();
    }
}
