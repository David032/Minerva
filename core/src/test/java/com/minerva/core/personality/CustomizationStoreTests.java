package com.minerva.core.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class CustomizationStoreTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsOnlySupportedSettingsAndKeepsBiographyWhenReset() throws IOException {
        CustomizationStore store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));
        store.saveBiography("A thoughtful assistant.", "source-hash");

        var saved = store.saveSettings("ocean", "wide", "serif", "formal");
        var reset = store.resetSettings();

        assertEquals("ocean", saved.palette());
        assertEquals("wide", saved.layout());
        assertEquals("serif", saved.typography());
        assertEquals("formal", saved.speakingStyle());
        assertEquals("forest", reset.palette());
        assertEquals("comfortable", reset.layout());
        assertEquals("A thoughtful assistant.", reset.biography());
        assertEquals("source-hash", reset.biographySourceDigest());
    }

    @Test
    void rejectsUnlistedAppearanceValuesInsteadOfPersistingArbitraryCss() throws IOException {
        CustomizationStore store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));

        assertThrows(IllegalArgumentException.class,
                () -> store.saveSettings("red; background-image: url(x)", "wide", "system", "warm"));
        assertEquals("forest", store.load().palette());
    }

    @Test
    void appendsSpeakingStyleAsAUserPreference() throws IOException {
        CustomizationStore store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));
        store.saveSettings("forest", "comfortable", "system", "playful");

        String prompt = store.appendSpeakingStyle("Base instructions");

        assertTrue(prompt.contains("<preferred_speaking_style>playful</preferred_speaking_style>"));
        assertTrue(prompt.contains("does not override base instructions"));
    }
}
