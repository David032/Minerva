package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class PersonalityDataStoreTests {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsComposesAndResetsPromptWithoutChangingBasePrompt() throws Exception {
        Path path = tempDirectory.resolve("personality.md");
        var firstRun = new PersonalityPromptStore(path);
        firstRun.save("I enjoy calm, thoughtful conversations.");

        var afterRestart = new PersonalityPromptStore(path);
        String base = "Unchanged base system instructions.";
        String composed = afterRestart.composeWith(base);

        assertTrue(composed.startsWith(base));
        assertTrue(composed.contains("I enjoy calm, thoughtful conversations."));
        afterRestart.reset();
        assertEquals(base, afterRestart.composeWith(base));
        assertFalse(Files.exists(path));
    }

    @Test
    void addsDeduplicatedTagsToMemoriesWithoutOverwritingExistingMemory() throws Exception {
        Path memories = tempDirectory.resolve("memories.md");
        Files.writeString(memories, "# Existing memory\n\nI prefer short answers.\n");
        var store = new InterestMemoryStore(memories);

        store.addAll(List.of("Botanical art", "botanical art", "Quiet gardens"));
        store.addAll(List.of("quiet gardens", "Watercolor"));

        String content = Files.readString(memories);
        assertTrue(content.contains("I prefer short answers."));
        assertTrue(content.contains("- Botanical art"));
        assertTrue(content.contains("- Quiet gardens"));
        assertTrue(content.contains("- Watercolor"));
        assertEquals(1, count(content, "- Quiet gardens"));
    }

    private static int count(String content, String needle) {
        return (content.length() - content.replace(needle, "").length()) / needle.length();
    }
}
