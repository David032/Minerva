package com.minerva.core.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
class CustomizationToolsTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appliesSupportedAssistantSettings() throws IOException {
        var store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));
        var tools = new CustomizationTools(store);

        String result = tools.customize("rose", "compact", "mono", "playful");

        assertEquals("Updated Minerva's appearance and speaking style: rose palette, compact layout, "
                + "mono typography, playful speaking style.", result);
        assertEquals("rose", store.load().palette());
    }

    @Test
    void rejectsUnsupportedAssistantSettings() {
        var tools = new CustomizationTools(new CustomizationStore(temporaryDirectory.resolve("customization.json")));

        assertThrows(IllegalArgumentException.class,
                () -> tools.customize("custom-css", "wide", "system", "warm"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gothic", "cyberpunk", "futuristic", "occult", "grimdark", "dark"})
    void allowsAssistantToApplyNewColourPalettes(String palette) throws IOException {
        var store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));
        var tools = new CustomizationTools(store);

        tools.customize(palette, "comfortable", "system", "warm");

        assertEquals(palette, store.load().palette());
    }
}
