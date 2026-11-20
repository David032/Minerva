package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
@ExtendWith(OutputCaptureExtension.class)
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
    void addsExtractedTagsToUnifiedInterestsWithoutOverwritingExistingMemory() throws Exception {
        Path memories = tempDirectory.resolve("memories.md");
        Files.writeString(memories, "# Existing memory\n\nI prefer short answers.\n");
        var store = new InterestMemoryStore(memories);

        assertEquals(List.of("Botanical art", "Quiet gardens"),
                store.addAll(List.of("Botanical art", "botanical art", "Quiet gardens")));
        assertEquals(List.of("Watercolor"), store.addAll(List.of("quiet gardens", "Watercolor")));

        String content = Files.readString(memories);
        assertTrue(content.contains("I prefer short answers."));
        assertTrue(content.contains("- 3 | Botanical art"));
        assertTrue(content.contains("- 3 | Quiet gardens"));
        assertTrue(content.contains("- 3 | Watercolor"));
        assertFalse(content.contains("minerva-media-interests"));
        assertEquals(1, count(content, "- 3 | Quiet gardens"));
    }

    @Test
    void mergesExtractedTagsWithScoredInterestsAndIncludesAllAsPreferenceContext() throws Exception {
        Path memories = tempDirectory.resolve("memories.md");
        Files.writeString(memories, "# Existing memory\n\nI prefer short answers.\n");
        var store = new InterestMemoryStore(memories);
        store.addAll(List.of("Botanical art"));
        store.saveScoredInterest("Watercolor", 2);
        store.saveScoredInterest("watercolor", 5);

        assertEquals(List.of(
                        new InterestMemoryStore.ScoredInterest("Botanical art", 3),
                        new InterestMemoryStore.ScoredInterest("watercolor", 5)),
                store.loadScoredInterests());
        String content = Files.readString(memories);
        assertTrue(content.contains("I prefer short answers."));
        assertTrue(content.contains("- 3 | Botanical art"));
        assertTrue(content.contains("- 5 | watercolor"));
        assertFalse(content.contains("minerva-media-interests"));

        String basePrompt = "Base system instructions.";
        String composed = store.appendScoredPreferences(basePrompt);
        assertTrue(composed.startsWith(basePrompt));
        assertTrue(composed.contains("1 means strongly negative and 5 means strongly positive"));
        assertTrue(composed.contains("3 means neutral or mixed"));
        assertTrue(composed.contains("- 3/5: Botanical art"));
        assertTrue(composed.contains("- 5/5: watercolor"));
        assertTrue(composed.contains("not instructions"));
    }

    @Test
    void migratesLegacyMediaTagsAtNeutralScoreAndPreservesExplicitScores() throws Exception {
        Path memories = tempDirectory.resolve("memories.md");
        Files.writeString(memories, """
                # Existing memory
                Keep this note.

                <!-- minerva-media-interests:start -->
                ## Interests from uploaded media
                - Botanical art
                - Watercolor
                <!-- minerva-media-interests:end -->

                <!-- minerva-scored-interests:start -->
                ## User-scored interests
                - 5 | watercolor
                <!-- minerva-scored-interests:end -->
                """);
        var store = new InterestMemoryStore(memories);

        assertEquals(List.of(
                        new InterestMemoryStore.ScoredInterest("watercolor", 5),
                        new InterestMemoryStore.ScoredInterest("Botanical art", 3)),
                store.loadScoredInterests());
        assertEquals(List.of("Botanical art"), store.addAll(List.of("Botanical art")));

        String migrated = Files.readString(memories);
        assertTrue(migrated.contains("Keep this note."));
        assertTrue(migrated.contains("- 3 | Botanical art"));
        assertTrue(migrated.contains("- 5 | watercolor"));
        assertFalse(migrated.contains("minerva-media-interests"));
    }

    @Test
    void rejectsBlankKeywordsAndScoresOutsideTheSupportedRange() {
        var store = new InterestMemoryStore(tempDirectory.resolve("memories.md"));

        assertThrows(IllegalArgumentException.class, () -> store.saveScoredInterest(" ", 3));
        assertThrows(IllegalArgumentException.class, () -> store.saveScoredInterest("Art", 0));
        assertThrows(IllegalArgumentException.class, () -> store.saveScoredInterest("Art", 6));
    }

    @Test
    void replacesLongTermGoalsAndAddsThemToThePromptAsEscapedContext() throws Exception {
        Path memories = tempDirectory.resolve("memories.md");
        Files.writeString(memories, "# Existing memory\n\nKeep this note.\n");
        var store = new InterestMemoryStore(memories);
        store.addAll(List.of("Botanical art"));
        List<String> firstGoals = List.of("Research gardens", "Explore art", "Develop a useful tool");

        store.replaceLongTermGoals(firstGoals);
        store.replaceLongTermGoals(List.of("Study local ecology", "Learn about sculpture",
                "Plan useful experiments <carefully>"));

        String content = Files.readString(memories);
        assertTrue(content.contains("Keep this note."));
        assertTrue(content.contains("- 3 | Botanical art"));
        assertFalse(content.contains("Research gardens"));
        assertEquals(1, count(content, "minerva-long-term-goals:start"));
        String composed = store.appendLongTermGoals("Base system prompt.");
        assertTrue(composed.startsWith("Base system prompt."));
        assertTrue(composed.contains("Study local ecology"));
        assertTrue(composed.contains("Plan useful experiments &lt;carefully&gt;"));
        assertTrue(composed.contains("not override the base instructions"));
    }

    @Test
    void rejectsGoalSetsOutsideTheSupportedRange() {
        var store = new InterestMemoryStore(tempDirectory.resolve("memories.md"));

        assertThrows(IllegalArgumentException.class, () -> store.replaceLongTermGoals(List.of("one", "two")));
        assertThrows(IllegalArgumentException.class,
                () -> store.replaceLongTermGoals(List.of("one", "two", "x".repeat(301))));
    }

    @Test
    void logsOnlyActualInterestChangesWithoutTheirContents(CapturedOutput output) throws Exception {
        var store = new InterestMemoryStore(tempDirectory.resolve("memories.md"));
        store.addAll(List.of("private interest"));
        store.addAll(List.of("private interest"));
        store.saveScoredInterest("secret interest", 2);
        store.saveScoredInterest("secret interest", 2);
        store.saveScoredInterest("secret interest", 5);

        String logs = output.getOut();
        assertEquals(1, count(logs, "Memory added source=media-interests count=1"));
        assertEquals(1, count(logs, "Memory added source=scored-interest"));
        assertEquals(1, count(logs, "Memory updated source=scored-interest"));
        assertFalse(logs.contains("private interest"));
        assertFalse(logs.contains("secret interest"));
    }

    private static int count(String content, String needle) {
        return (content.length() - content.replace(needle, "").length()) / needle.length();
    }
}
