package com.minerva.core.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
