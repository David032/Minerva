package com.minerva.api.personality;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Validates and reads a bounded ZIP before any personality preferences are changed.
 */
final class PersonalityZipArchive {
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_FILES = 50;

    /**
     * Reads regular archive entries without extracting paths to the filesystem.
     *
     * @param archive uploaded ZIP
     * @return validated entries in archive order
     * @throws IOException if the archive cannot be read
     * @throws IllegalArgumentException if it is empty, unsafe, or exceeds a limit
     */
    static List<MultipartFile> read(MultipartFile archive) throws IOException {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty ZIP archive.");
        }
        if (archive.getSize() > MAX_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("ZIP archives must be 100 MB or smaller.");
        }
        Path temporary = Files.createTempFile("minerva-personality-", ".zip");
        try {
            try (InputStream input = archive.getInputStream(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ARCHIVE_BYTES) {
                        throw new IllegalArgumentException("ZIP archives must be 100 MB or smaller.");
                    }
                    output.write(buffer, 0, count);
                }
            }
            return readEntries(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static List<MultipartFile> readEntries(Path archive) throws IOException {
        List<MultipartFile> files = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int entries = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("The ZIP contains too many entries.");
                }
                String name = entry.getName();
                validateName(name);
                if (!names.add(name)) {
                    throw new IllegalArgumentException("The ZIP contains duplicate paths.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (files.size() >= MAX_FILES) {
                    throw new IllegalArgumentException("A maximum of 50 files can be uploaded in one ZIP.");
                }
                CRC32 checksum = new CRC32();
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    long size = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count;
                        expanded += count;
                        if (size > MAX_ENTRY_BYTES || expanded > MAX_EXPANDED_BYTES) {
                            throw new IllegalArgumentException(
                                    "ZIP contents exceed the 20 MB per-file or 100 MB total uncompressed limit.");
                        }
                        checksum.update(buffer, 0, count);
                        content.write(buffer, 0, count);
                    }
                    if (size != entry.getSize() || checksum.getValue() != entry.getCrc()) {
                        throw new IllegalArgumentException("The ZIP contains a corrupt file.");
                    }
                }
                files.add(new ArchiveFile(name, content.toByteArray()));
            }
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException("The uploaded ZIP is invalid or corrupt.", e);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("The ZIP contains no files.");
        }
        return List.copyOf(files);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || name.contains(":") || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("The ZIP contains an unsafe file path.");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("The ZIP contains an unsafe file path.");
            }
        }
    }

    private record ArchiveFile(String filename, byte[] content) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.write(destination.toPath(), content);
        }
    }
}
