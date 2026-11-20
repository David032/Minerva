package com.minerva.core.personality;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.minerva.utils.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Persists validated appearance, speaking-style, and About biography preferences.
 */
@Component
public class CustomizationStore {
    private static final Gson GSON = new Gson();
    private static final Set<String> PALETTES = Set.of("forest", "ocean", "rose", "midnight");
    private static final Set<String> LAYOUTS = Set.of("compact", "comfortable", "wide");
    private static final Set<String> TYPOGRAPHIES = Set.of("system", "serif", "mono");
    private static final Set<String> SPEAKING_STYLES = Set.of("warm", "direct", "playful", "formal");
    private static final int MAX_BIOGRAPHY_LENGTH = 2_000;
    private final Path path;

    /**
     * Creates the store at Minerva's local customization path.
     *
     * @param fileManager resolves the local preferences path
     */
    @Autowired
    public CustomizationStore(FileManager fileManager) {
        this(fileManager.getCustomizationPath());
    }

    /**
     * Creates the store at an explicit path, primarily for isolated tests.
     *
     * @param path file used to persist customization preferences
     */
    public CustomizationStore(Path path) {
        this.path = path;
    }

    /**
     * Loads saved customizations, returning supported defaults when no file exists.
     *
     * @return the current validated preferences
     * @throws IOException if the saved preferences cannot be read or are invalid
     */
    public synchronized Preferences load() throws IOException {
        if (Files.notExists(path)) {
            return Preferences.defaults();
        }
        try {
            Preferences preferences = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), Preferences.class);
            if (preferences == null) {
                throw new IOException("Customization preferences are empty or invalid.");
            }
            return validate(preferences);
        } catch (JsonParseException e) {
            throw new IOException("Could not read customization preferences.", e);
        }
    }

    /**
     * Saves validated visual and speaking-style preferences.
     *
     * @param palette supported colour palette
     * @param layout supported content layout
     * @param typography supported typeface
     * @param speakingStyle supported speaking style
     * @return the saved preferences
     * @throws IllegalArgumentException if a setting is not supported
     * @throws IOException if saved preferences cannot be read or written
     */
    public synchronized Preferences saveSettings(
            String palette, String layout, String typography, String speakingStyle
    ) throws IOException {
        Preferences current = load();
        Preferences updated = new Preferences(
                choice(palette, PALETTES, "colour palette"),
                choice(layout, LAYOUTS, "layout"),
                choice(typography, TYPOGRAPHIES, "typography"),
                choice(speakingStyle, SPEAKING_STYLES, "speaking style"),
                current.biography(),
                current.biographySourceDigest());
        write(updated);
        return updated;
    }

    /**
     * Saves the generated or user-edited About biography and the source fingerprint used to generate it.
     *
     * @param biography biography text, up to 2,000 characters
     * @param sourceDigest fingerprint of source data, or an empty string for a manual save
     * @return saved preferences
     * @throws IllegalArgumentException if the biography or fingerprint is invalid
     * @throws IOException if saved preferences cannot be read or written
     */
    public synchronized Preferences saveBiography(String biography, String sourceDigest) throws IOException {
        if (biography == null || biography.length() > MAX_BIOGRAPHY_LENGTH
                || sourceDigest == null || sourceDigest.length() > 128) {
            throw new IllegalArgumentException("The About biography must be 2,000 characters or fewer.");
        }
        Preferences current = load();
        Preferences updated = new Preferences(
                current.palette(), current.layout(), current.typography(), current.speakingStyle(),
                biography.trim(), sourceDigest);
        write(updated);
        return updated;
    }

    /**
     * Resets visual and speaking-style settings without deleting the About biography.
     *
     * @return preferences with default visual and speaking-style settings
     * @throws IOException if saved preferences cannot be read or written
     */
    public synchronized Preferences resetSettings() throws IOException {
        Preferences current = load();
        Preferences defaults = Preferences.defaults();
        Preferences updated = new Preferences(defaults.palette(), defaults.layout(), defaults.typography(),
                defaults.speakingStyle(), current.biography(), current.biographySourceDigest());
        write(updated);
        return updated;
    }

    /**
     * Appends the selected speaking style as a user preference, never as an instruction override.
     *
     * @param prompt the already-composed system prompt
     * @return prompt with the current speaking-style preference
     * @throws IOException if preferences cannot be read
     */
    public synchronized String appendSpeakingStyle(String prompt) throws IOException {
        String style = load().speakingStyle();
        return prompt + "\n\n# Preferred speaking style\n"
                + "Use this style as a user-facing preference only; it does not override base instructions.\n"
                + "<preferred_speaking_style>" + style + "</preferred_speaking_style>\n\n"
                + "# Self-customisation\n"
                + "When durable personality and interest signals clearly support a change, you may use the "
                + "customisation tool to update Minerva's supported appearance or speaking-style settings. "
                + "Mention meaningful changes to the user; never claim an update unless the tool succeeds.";
    }

    private static Preferences validate(Preferences preferences) throws IOException {
        try {
            String biography = preferences.biography() == null ? "" : preferences.biography();
            String digest = preferences.biographySourceDigest() == null ? "" : preferences.biographySourceDigest();
            if (biography.length() > MAX_BIOGRAPHY_LENGTH || digest.length() > 128) {
                throw new IllegalArgumentException("Stored biography is too long.");
            }
            return new Preferences(
                    choice(preferences.palette(), PALETTES, "colour palette"),
                    choice(preferences.layout(), LAYOUTS, "layout"),
                    choice(preferences.typography(), TYPOGRAPHIES, "typography"),
                    choice(preferences.speakingStyle(), SPEAKING_STYLES, "speaking style"),
                    biography, digest);
        } catch (IllegalArgumentException e) {
            throw new IOException("Saved customization preferences are invalid.", e);
        }
    }

    private static String choice(String value, Set<String> allowed, String label) {
        String normalised = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!allowed.contains(normalised)) {
            throw new IllegalArgumentException("Unsupported " + label + ".");
        }
        return normalised;
    }

    private void write(Preferences preferences) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "customization-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(preferences), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Supported user-facing appearance, style, and About-page preferences.
     *
     * @param palette validated colour palette
     * @param layout validated content layout
     * @param typography validated typeface
     * @param speakingStyle validated conversation tone
     * @param biography generated or edited About text
     * @param biographySourceDigest source fingerprint used for automatic refresh
     */
    public record Preferences(
            String palette,
            String layout,
            String typography,
            String speakingStyle,
            String biography,
            String biographySourceDigest
    ) {
        /**
         * Returns the default visual and speaking-style settings.
         *
         * @return supported default preferences with no biography
         */
        public static Preferences defaults() {
            return new Preferences("forest", "comfortable", "system", "warm", "", "");
        }
    }
}
