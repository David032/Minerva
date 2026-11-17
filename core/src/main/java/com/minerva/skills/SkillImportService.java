package com.minerva.skills;

import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.springaicommunity.agent.utils.MarkdownParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SkillImportService {
    private static final int MAX_ARCHIVE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 100L * 1024 * 1024;
    private static final long MAX_REPOSITORY_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 2_000;
    private static final int MAX_SKILLS = 50;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final long GIT_TIMEOUT_SECONDS = 90;

    private final Path skillsDirectory;
    private final HttpClient httpClient;
    private SkillChangeListener skillChangeListener = () -> { };

    public SkillImportService() {
        this(Path.of(FileManager.getInstance().getDirectoryPath(Directory.SKILLS)));
    }

    SkillImportService(Path skillsDirectory) {
        this.skillsDirectory = skillsDirectory.toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public synchronized List<String> importArchive(MultipartFile archive) throws IOException {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("Choose a ZIP archive to import.");
        }
        if (archive.getSize() > MAX_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("ZIP archives must be 10 MB or smaller.");
        }
        try (InputStream input = archive.getInputStream()) {
            return importArchive(input);
        }
    }

    public synchronized List<String> importFromSource(String source) throws IOException {
        URI uri = validateSource(source);
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("The HTTPS URL must identify a ZIP archive or Git repository.");
        }
        if (path.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            byte[] archive = downloadArchive(uri);
            return importArchive(new ByteArrayInputStream(archive));
        }
        return importGitRepository(uri);
    }

    public synchronized void setSkillChangeListener(SkillChangeListener listener) {
        skillChangeListener = listener == null ? () -> { } : listener;
    }

    private List<String> importArchive(InputStream input) throws IOException {
        byte[] archive = readBounded(input, MAX_ARCHIVE_BYTES, "ZIP archives must be 10 MB or smaller.");
        Path extracted = Files.createTempDirectory("minerva-skill-archive-");
        try {
            extractArchive(archive, extracted);
            return installSkills(extracted);
        } finally {
            deleteTree(extracted);
        }
    }

    private List<String> importGitRepository(URI uri) throws IOException {
        Path temporaryRoot = Files.createTempDirectory("minerva-skill-repository-");
        try {
            Path globalGitConfig = temporaryRoot.resolve("gitconfig");
            Files.createFile(globalGitConfig);
            Path checkout = temporaryRoot.resolve("checkout");
            ProcessBuilder builder = new ProcessBuilder(
                    "git",
                    "-c", "http.followRedirects=false",
                    "clone",
                    "--depth=1",
                    "--no-recurse-submodules",
                    "--",
                    uri.toString(),
                    checkout.toString()
            );
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
            builder.environment().put("GIT_CONFIG_GLOBAL", globalGitConfig.toString());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.environment().put("GIT_LFS_SKIP_SMUDGE", "1");

            Process process = builder.start();
            try {
                if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("The Git repository download took too long.");
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("The Git repository download was interrupted.", e);
            }
            if (process.exitValue() != 0) {
                throw new IOException("Could not clone the HTTPS Git repository.");
            }

            ensureRepositorySize(checkout);
            return installSkills(checkout);
        } finally {
            deleteTree(temporaryRoot);
        }
    }

    private byte[] downloadArchive(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The ZIP download was interrupted.", e);
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("The ZIP URL returned HTTP " + response.statusCode() + ".");
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > MAX_ARCHIVE_BYTES) {
                throw new IllegalArgumentException("ZIP archives must be 10 MB or smaller.");
            }
            return readBounded(body, MAX_ARCHIVE_BYTES, "ZIP archives must be 10 MB or smaller.");
        }
    }

    private URI validateSource(String source) throws IOException {
        if (source == null || source.isBlank() || source.length() > 2_048) {
            throw new IllegalArgumentException("Enter an HTTPS URL for a ZIP archive or Git repository.");
        }
        URI uri;
        try {
            uri = URI.create(source.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Enter a valid HTTPS URL.", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Only public HTTPS ZIP and Git repository URLs are supported.");
        }

        try {
            String host = uri.getHost();
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isNonPublicAddress(address)) {
                    throw new IllegalArgumentException("URLs pointing to local or private network addresses are not allowed.");
                }
            }
        } catch (IOException e) {
            throw new IOException("Could not resolve the remote host.", e);
        }
        return uri;
    }

    private boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            return first == 0
                    || first == 100 && second >= 64 && second <= 127
                    || first == 192 && second == 0 && (third == 0 || third == 2)
                    || first == 198 && (second == 18 || second == 19 || second == 51 && third == 100)
                    || first == 203 && second == 0 && third == 113
                    || first >= 224;
        }
        return (bytes[0] & 0xfe) == 0xfc;
    }

    private void extractArchive(byte[] archive, Path destination) throws IOException {
        Set<String> entries = new HashSet<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("The ZIP contains too many files.");
                }
                Path target = resolveArchiveEntry(destination, entry.getName());
                String normalizedName = destination.relativize(target).toString();
                if (!entries.add(normalizedName)) {
                    throw new IllegalArgumentException("The ZIP contains duplicate file paths.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    zip.closeEntry();
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[8_192];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > MAX_EXTRACTED_BYTES) {
                            throw new IllegalArgumentException("The uncompressed ZIP contents exceed 100 MB.");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private Path resolveArchiveEntry(Path root, String entryName) {
        if (entryName == null
                || entryName.isBlank()
                || entryName.indexOf('\\') >= 0
                || entryName.indexOf(':') >= 0
                || entryName.startsWith("/")
                || entryName.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("The ZIP contains an unsafe file path.");
        }
        for (String segment : entryName.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("The ZIP contains an unsafe file path.");
            }
        }
        Path target = root.resolve(entryName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("The ZIP contains an unsafe file path.");
        }
        return target;
    }

    private List<String> installSkills(Path sourceRoot) throws IOException {
        List<Path> skillDirectories = discoverSkillDirectories(sourceRoot);
        if (skillDirectories.isEmpty()) {
            throw new IllegalArgumentException("No skill directories containing SKILL.md were found.");
        }
        if (skillDirectories.size() > MAX_SKILLS) {
            throw new IllegalArgumentException("The source contains too many skills.");
        }

        List<Skill> skills = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Path directory : skillDirectories) {
            String name = readSkillName(directory.resolve("SKILL.md"));
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The source contains duplicate skill names.");
            }
            if (Files.exists(skillsDirectory.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("A skill named '" + name + "' is already installed.");
            }
            skills.add(new Skill(name, directory));
        }
        skills.sort(java.util.Comparator.comparing(Skill::name));

        Files.createDirectories(skillsDirectory);
        Path staging = Files.createTempDirectory(skillsDirectory, ".skill-import-");
        List<Path> installed = new ArrayList<>();
        try {
            for (Skill skill : skills) {
                copyTree(skill.sourceDirectory(), staging.resolve(skill.name()), skillDirectories);
            }
            for (Skill skill : skills) {
                Path target = skillsDirectory.resolve(skill.name());
                moveNewDirectory(staging.resolve(skill.name()), target);
                installed.add(target);
            }
            skillChangeListener.reload();
            return skills.stream().map(Skill::name).toList();
        } catch (IOException | RuntimeException e) {
            for (Path path : installed) {
                deleteTree(path);
            }
            throw e;
        } finally {
            deleteTree(staging);
        }
    }

    private List<Path> discoverSkillDirectories(Path sourceRoot) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            private int entries;

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("The source contains too many files.");
                }
                if (!directory.equals(sourceRoot) && directory.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (attributes.isSymbolicLink()) {
                    throw new IllegalArgumentException("Skills containing symbolic links are not supported.");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("The source contains too many files.");
                }
                if (attributes.isSymbolicLink()) {
                    throw new IllegalArgumentException("Skills containing symbolic links are not supported.");
                }
                if (file.getFileName().toString().equals("SKILL.md") && attributes.isRegularFile()) {
                    found.add(file.getParent());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                throw error;
            }
        });

        found.sort(java.util.Comparator.comparing(Path::toString));
        return found;
    }

    private void copyTree(Path source, Path destination, List<Path> skillDirectories) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IllegalArgumentException("Skills containing symbolic links are not supported.");
                }
                if (!directory.equals(source) && directory.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!directory.equals(source) && skillDirectories.contains(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                    throw new IllegalArgumentException("Skills may contain regular files only.");
                }
                Files.copy(file, destination.resolve(source.relativize(file)), LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void ensureRepositorySize(Path checkout) throws IOException {
        long totalBytes = 0;
        try (var paths = Files.walk(checkout)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Git repositories containing symbolic links are not supported.");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    totalBytes += Files.size(path);
                    if (totalBytes > MAX_REPOSITORY_BYTES) {
                        throw new IllegalArgumentException("The Git repository exceeds 100 MB.");
                    }
                }
            }
        }
    }

    private void moveNewDirectory(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    private byte[] readBounded(InputStream input, int limit, String error) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > limit) {
                throw new IllegalArgumentException(error);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String validateSkillName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("SKILL.md must contain a valid name in its front matter.");
        }
        return name;
    }

    private String readSkillName(Path skillMarkdown) throws IOException {
        if (Files.size(skillMarkdown) > 1024 * 1024) {
            throw new IllegalArgumentException("SKILL.md files must be 1 MB or smaller.");
        }
        var metadata = new MarkdownParser(Files.readString(skillMarkdown, StandardCharsets.UTF_8)).getFrontMatter();
        Object rawName = metadata.get("name");
        if (!(rawName instanceof String name)) {
            throw new IllegalArgumentException("SKILL.md must contain a valid name in its front matter.");
        }
        validateSkillName(name);
        Object rawDescription = metadata.get("description");
        if (!(rawDescription instanceof String description)
                || description.isBlank()
                || description.length() > 500
                || description.chars().anyMatch(ch -> ch < 0x20 || ch == '<' || ch == '>' || ch == '&')) {
            throw new IllegalArgumentException(
                    "SKILL.md must contain a plain-text description of 500 characters or fewer.");
        }
        for (var entry : metadata.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z0-9_-]+")
                    || entry.getValue().toString().chars()
                    .anyMatch(ch -> ch < 0x20 || ch == '<' || ch == '>' || ch == '&')) {
                throw new IllegalArgumentException("SKILL.md front matter contains unsupported metadata.");
            }
        }
        return name;
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record Skill(String name, Path sourceDirectory) {
    }

    @FunctionalInterface
    public interface SkillChangeListener {
        void reload() throws IOException;
    }

}
