package com.minerva.api;

import com.minerva.core.personality.AboutProfileService;
import com.minerva.core.personality.CustomizationStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

/**
 * Displays and edits Minerva's generated About biography.
 */
@Controller
public class AboutController {
    private final AboutProfileService aboutProfileService;
    private final CustomizationStore customizationStore;

    /**
     * Creates the About page controller.
     *
     * @param aboutProfileService loads or refreshes the biography from saved sources
     * @param customizationStore persists biography edits
     */
    public AboutController(
            AboutProfileService aboutProfileService,
            CustomizationStore customizationStore
    ) {
        this.aboutProfileService = aboutProfileService;
        this.customizationStore = customizationStore;
    }

    /**
     * Displays Minerva's About page, refreshing its biography when its sources have changed.
     *
     * @param model view model for the biography and supporting interests/activity/media
     * @return the About page template
     * @throws IOException if a local source cannot be read or the biography cannot be persisted
     */
    @GetMapping("/about")
    public String page(Model model) throws IOException {
        model.addAttribute("about", aboutProfileService.loadPage());
        model.addAttribute("customization", customizationStore.load());
        return "about";
    }

    /**
     * Checks for source changes while the About page is open.
     *
     * @return the latest biography state and source fingerprint
     * @throws IOException if a local source cannot be read or the biography cannot be persisted
     */
    @GetMapping("/api/about")
    @ResponseBody
    public AboutProfileService.AboutPage profile() throws IOException {
        return aboutProfileService.loadPage();
    }

    /**
     * Saves a manual biography edit until one of its source records changes.
     *
     * @param biography edited About text
     * @param model view model for the save result
     * @return the About page template
     * @throws IOException if a biography or source cannot be read or saved
     */
    @PostMapping("/about/biography")
    public String saveBiography(@RequestParam String biography, Model model) throws IOException {
        try {
            String currentDigest = customizationStore.load().biographySourceDigest();
            customizationStore.saveBiography(biography, currentDigest);
            model.addAttribute("success", "About biography saved.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        return page(model);
    }
}
