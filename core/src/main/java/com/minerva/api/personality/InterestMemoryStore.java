package com.minerva.api.personality;

import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class InterestMemoryStore {
    private static final String START = "<!-- minerva-media-interests:start -->";
    private static final String END = "<!-- minerva-media-interests:end -->";
    private final Path path;

    public InterestMemoryStore(FileManager fileManager) {
        this(Path.of(fileManager.getDirectoryPath(Directory.MEMORIES)).resolve("memories.md"));
    }

    InterestMemoryStore(Path path) {
        this.path = path;
    }

    public synchronized void addAll(List<String> tags) throws IOException {
        if (tags.isEmpty()) {
            return;
        }
        String original = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        List<String> allTags = new ArrayList<>();
        int start = original.indexOf(START);
        int end = original.indexOf(END);
        if (start >= 0 && end > start) {
            String managed = original.substring(start + START.length(), end);
            for (String line : managed.split("\\R")) {
                if (line.startsWith("- ")) {
                    allTags.add(line.substring(2).trim());
                }
            }
        }

        var unique = new LinkedHashMap<String, String>();
        for (String tag : allTags) {
            if (!tag.isBlank()) {
                unique.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
            }
        }
        for (String tag : tags) {
            String normalized = tag.replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
            if (!normalized.isEmpty()) {
                unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        String managedBlock = START + "\n## Interests from uploaded media\n"
                + unique.values().stream().map(tag -> "- " + tag).reduce("", (a, b) -> a + b + "\n")
                + END;
        String updated;
        if (start >= 0 && end > start) {
            updated = original.substring(0, start) + managedBlock + original.substring(end + END.length());
        } else {
            updated = original + (original.isBlank() || original.endsWith("\n") ? "" : "\n")
                    + (original.isBlank() ? "" : "\n") + managedBlock + "\n";
        }
        write(updated);
    }

    synchronized byte[] snapshot() throws IOException {
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    synchronized void restore(byte[] snapshot) throws IOException {
        if (snapshot == null) {
            Files.deleteIfExists(path);
        } else {
            write(new String(snapshot, StandardCharsets.UTF_8));
        }
    }

    private void write(String value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "memories-", ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
