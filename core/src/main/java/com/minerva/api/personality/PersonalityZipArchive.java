package com.minerva.api.personality;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Validates and reads a bounded ZIP before any personality preferences are changed.
 * CSS files are ignored without being extracted, but still count towards the entry limit.
 */
final class PersonalityZipArchive implements AutoCloseable {
    private static final Limits LIMITS = new Limits(
            1024L * 1024 * 1024, 20L * 1024 * 1024, 2L * 1024 * 1024 * 1024, 10_000, 10_000);
    private final Path temporaryDirectory;
    private final Limits limits;
    private List<MultipartFile> files;

    private PersonalityZipArchive(Path temporaryDirectory, Limits limits) {
        this.temporaryDirectory = temporaryDirectory;
        this.limits = limits;
    }

    /**
     * Validates and stages archive entries under generated temporary filenames.
     *
     * @param archive uploaded ZIP
     * @return a resource holding validated entries in archive order; must be closed
     * @throws IOException if the archive cannot be read
     * @throws IllegalArgumentException if it is empty, unsafe, or exceeds a limit
     */
    static PersonalityZipArchive open(MultipartFile archive) throws IOException {
        return open(archive, null, LIMITS);
    }

    /**
     * Validates an archive using a specified temporary parent directory.
     *
     * @param archive uploaded ZIP
     * @param temporaryParent parent for staging, or null for the system temporary directory
     * @param limits limits used to bound compressed and expanded content
     * @return a resource that deletes its staged files when closed
     * @throws IOException if the archive or temporary files cannot be read or written
     * @throws IllegalArgumentException if it is empty, unsafe, or exceeds a limit
     */
    static PersonalityZipArchive open(MultipartFile archive, Path temporaryParent, Limits limits) throws IOException {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty ZIP archive.");
        }
        if (archive.getSize() > limits.archiveBytes()) {
            throw new IllegalArgumentException("ZIP archives must be 1 GB or smaller.");
        }
        Path directory = temporaryParent == null
                ? Files.createTempDirectory("minerva-personality-")
                : Files.createTempDirectory(temporaryParent, "minerva-personality-");
        var result = new PersonalityZipArchive(directory, limits);
        try {
            Path temporary = directory.resolve("archive.zip");
            try (InputStream input = archive.getInputStream(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > limits.archiveBytes()) {
                        throw new IllegalArgumentException("ZIP archives must be 1 GB or smaller.");
                    }
                    output.write(buffer, 0, count);
                }
            }
            result.files = result.readEntries(temporary);
            Files.delete(temporary);
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                result.close();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Returns the staged entries in archive order.
     *
     * @return validated uploaded files
     */
    List<MultipartFile> files() {
        return files;
    }

    private List<MultipartFile> readEntries(Path archive) throws IOException {
        List<MultipartFile> files = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int entries = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                if (++entries > limits.entries()) {
                    throw new IllegalArgumentException("The ZIP contains too many entries.");
                }
                String name = entry.getName();
                if (!entry.isDirectory() && name.toLowerCase(Locale.ROOT).endsWith(".css")) {
                    continue;
                }
                validateName(name);
                if (!names.add(name)) {
                    throw new IllegalArgumentException("The ZIP contains duplicate paths.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (files.size() >= limits.files()) {
                    throw new IllegalArgumentException("A maximum of " + limits.files()
                            + " files can be uploaded in one ZIP.");
                }
                CRC32 checksum = new CRC32();
                Path staged = temporaryDirectory.resolve("entry-" + entries);
                long size = 0;
                try (InputStream input = zip.getInputStream(entry); var output = Files.newOutputStream(staged)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count;
                        expanded += count;
                        if (size > limits.entryBytes() || expanded > limits.expandedBytes()) {
                            throw new IllegalArgumentException(
                                    "ZIP contents exceed the 20 MB per-file or 2 GB total uncompressed limit.");
                        }
                        checksum.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                    if (size != entry.getSize() || checksum.getValue() != entry.getCrc()) {
                        throw new IllegalArgumentException("The ZIP contains a corrupt file.");
                    }
                }
                files.add(new ArchiveFile(name, staged, size));
            }
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException("The uploaded ZIP is invalid or corrupt.", e);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("The ZIP contains no files.");
        }
        return List.copyOf(files);
    }

    /**
     * Deletes all staged files and their temporary directory.
     *
     * @throws IOException if temporary files cannot be removed
     */
    @Override
    public void close() throws IOException {
        IOException failure = null;
        try (var children = Files.newDirectoryStream(temporaryDirectory)) {
            for (Path child : children) {
                try {
                    Files.deleteIfExists(child);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            Files.deleteIfExists(temporaryDirectory);
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
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

    /**
     * Bounds the archive, individual files, expanded content, and entry counts.
     *
     * @param archiveBytes maximum compressed bytes
     * @param entryBytes maximum uncompressed bytes per file
     * @param expandedBytes maximum uncompressed bytes across files
     * @param entries maximum entries including directories
     * @param files maximum non-directory files
     */
    record Limits(long archiveBytes, long entryBytes, long expandedBytes, int entries, int files) {
    }

    private record ArchiveFile(String filename, Path path, long size) implements MultipartFile {
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
            return size == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
