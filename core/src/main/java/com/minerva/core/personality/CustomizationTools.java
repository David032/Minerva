package com.minerva.core.personality;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Exposes bounded self-customization settings to Minerva's assistant.
 */
@Component
public class CustomizationTools {
    private final CustomizationStore store;

    /**
     * Creates the assistant customization tools.
     *
     * @param store persists supported preferences
     */
    public CustomizationTools(CustomizationStore store) {
        this.store = store;
    }

    /**
     * Changes Minerva's supported appearance and speaking-style settings.
     *
     * @param palette one of forest, ocean, rose, or midnight
     * @param layout one of compact, comfortable, or wide
     * @param typography one of system, serif, or mono
     * @param speakingStyle one of warm, direct, playful, or formal
     * @return confirmation of the settings applied
     * @throws IllegalArgumentException if any value is unsupported
     * @throws IOException if preferences cannot be persisted
     */
    @Tool(description = "Update Minerva's user-facing customisation when personality, interests, or the user's request support a change. "
            + "Use only palette forest/ocean/rose/midnight, layout compact/comfortable/wide, "
            + "typography system/serif/mono, and speakingStyle warm/direct/playful/formal. "
            + "These settings are immediately visible and user-resettable.")
    public String customize(
            String palette, String layout, String typography, String speakingStyle
    ) throws IOException {
        var saved = store.saveSettings(palette, layout, typography, speakingStyle);
        return "Updated Minerva's appearance and speaking style: "
                + saved.palette() + " palette, " + saved.layout() + " layout, "
                + saved.typography() + " typography, " + saved.speakingStyle() + " speaking style.";
    }
}
