package com.minerva.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.IO.print;

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
        Path path = Paths.get(userHome, "MinervaOS","uploads");

        try {
            Files.createDirectories(path);
            System.out.println("Directory successfully verified/created at: " + path.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create directory: " + e.getMessage());
        }
        System.setProperty("app.dir.uploads", path.toAbsolutePath().toString());
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
}
