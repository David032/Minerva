package com.minerva.api;

import com.minerva.core.personality.CustomizationStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Exposes the current validated appearance preferences to the chat page.
 */
@RestController
public class CustomizationController {
    private final CustomizationStore store;

    /**
     * Creates the customization endpoint.
     *
     * @param store loads persisted appearance preferences
     */
    public CustomizationController(CustomizationStore store) {
        this.store = store;
    }

    /**
     * Returns the current settings used to style the chat.
     *
     * @return validated appearance preferences
     * @throws IOException if the saved preferences cannot be read
     */
    @GetMapping("/api/customization")
    public AppearanceSettings settings() throws IOException {
        var preferences = store.load();
        return new AppearanceSettings(
                preferences.palette(), preferences.layout(), preferences.typography());
    }

    /**
     * The appearance settings safe to expose to browser code.
     *
     * @param palette validated colour palette
     * @param layout validated content layout
     * @param typography validated typeface
     */
    public record AppearanceSettings(String palette, String layout, String typography) {
    }
}
