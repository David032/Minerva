package com.minerva.api.personality;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.List;

/**
 * Renders the personality customization pages and handles their form submissions.
 */
@Controller
public class PersonalityController {
    private final MediaPersonalityService service;

    /**
     * Creates the controller backed by the personality workflow service.
     *
     * @param service performs media analysis and persists personality preferences
     */
    public PersonalityController(MediaPersonalityService service) {
        this.service = service;
    }

    /**
     * Displays the personality prompt and media upload forms.
     *
     * @param model view model for the personality page
     * @return the personality template
     * @throws IOException if the saved prompt cannot be loaded
     */
    @GetMapping("/personality")
    public String page(Model model) throws IOException {
        model.addAttribute("personalityPrompt", service.loadPrompt());
        return "personality";
    }

    /**
     * Processes a single document/image submission or a batch of HTML documents.
     *
     * @param file optional single PDF, HTML, or image upload
     * @param htmlFiles optional batch of HTML uploads
     * @param imageUrl optional image URL
     * @param comment optional image-analysis comment
     * @param guidance optional PDF or HTML preference guidance
     * @param model view model for the upload result
     * @return the personality template with success or failure feedback
     * @throws IOException if the saved prompt cannot be loaded
     */
    @PostMapping("/personality/upload")
    public String upload(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) List<MultipartFile> htmlFiles,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(defaultValue = "") String comment,
            @RequestParam(defaultValue = "") String guidance,
            Model model
    ) throws IOException {
        try {
            if (guidance.length() > 2_000 || comment.length() > 2_000) {
                throw new IllegalArgumentException("Guidance must be 2,000 characters or fewer.");
            }
            if (imageUrl != null && imageUrl.length() > 2_048) {
                throw new IllegalArgumentException("Image URLs must be 2,048 characters or fewer.");
            }
            boolean hasHtmlBatch = htmlFiles != null && !htmlFiles.isEmpty();
            if (hasHtmlBatch) {
                if (file != null && !file.isEmpty() || imageUrl != null && !imageUrl.isBlank()) {
                    throw new IllegalArgumentException("Submit either a single file or image URL, or an HTML batch, not both.");
                }
                var result = service.processHtmlBatch(htmlFiles, guidance);
                model.addAttribute("personalityPrompt", result.personalityPrompt());
                if (result.successfulFileCount() > 0) {
                    model.addAttribute("success", "Processed " + result.successfulFileCount()
                            + " HTML file(s). Saved " + result.savedTagCount()
                            + " interest tags and analyzed " + result.analyzedMediaCount() + " media item(s).");
                }
                if (!result.failures().isEmpty()) {
                    model.addAttribute("error", formatBatchFailures(result.failures()));
                }
                return "personality";
            }
            boolean imageUpload = file != null && !file.isEmpty()
                    && (file.getContentType() != null && file.getContentType().toLowerCase().startsWith("image/")
                    || isImageFilename(file.getOriginalFilename()));
            var result = (imageUrl != null && !imageUrl.isBlank()) || imageUpload
                    ? service.processImage(file, imageUrl, comment)
                    : service.process(file, guidance);
            model.addAttribute("success", "Analysis complete. Saved " + result.savedTagCount()
                    + " interest tags and analyzed " + result.analyzedMediaCount() + " media item(s).");
            model.addAttribute("personalityPrompt", result.personalityPrompt());
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("personalityPrompt", service.loadPrompt());
        }
        return "personality";
    }

    /**
     * Displays all currently saved user-scored interests.
     *
     * @param model view model for the scored-interest page
     * @return the scored-interest template
     * @throws IOException if saved interests cannot be loaded
     */
    @GetMapping("/personality/interests")
    public String interests(Model model) throws IOException {
        model.addAttribute("scoredInterests", service.loadScoredInterests());
        return "interests";
    }

    /**
     * Adds a scored interest or updates the score for an existing keyword.
     *
     * @param keyword the user's keyword or tag
     * @param score submitted score, expected to be an integer from 1 (strongly negative) to 5 (strongly positive)
     * @param model view model for feedback and the current interest list
     * @return the scored-interest template with the operation result
     * @throws IOException if saved interests cannot be reloaded after the update
     */
    @PostMapping("/personality/interests")
    public String saveInterest(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String score,
            Model model
    ) throws IOException {
        try {
            int parsedScore;
            try {
                parsedScore = Integer.parseInt(score);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Interest scores must be whole numbers from 1 to 5.", e);
            }
            var saved = service.saveScoredInterest(keyword, parsedScore);
            model.addAttribute("success", "Saved \"" + saved.keyword() + "\" with a score of " + saved.score() + "/5.");
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("scoredInterests", service.loadScoredInterests());
        return "interests";
    }

    private static String formatBatchFailures(List<MediaPersonalityService.FileFailure> failures) {
        return "Some HTML files could not be processed: " + failures.stream()
                .map(failure -> failure.filename() + ": " + failure.message())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private static boolean isImageFilename(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    /**
     * Saves an edited personality prompt.
     *
     * @param personalityPrompt the new personality preferences
     * @param model view model for feedback and the current prompt
     * @return the personality template
     * @throws IOException if the prompt cannot be saved or reloaded
     */
    @PostMapping("/personality/prompt")
    public String savePrompt(@RequestParam String personalityPrompt, Model model) throws IOException {
        try {
            service.savePrompt(personalityPrompt);
            model.addAttribute("success", "Personality prompt saved.");
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("personalityPrompt", service.loadPrompt());
        return "personality";
    }

    /**
     * Resets the saved personality prompt.
     *
     * @param model view model for the reset confirmation
     * @return the personality template
     * @throws IOException if the prompt cannot be reset
     */
    @PostMapping("/personality/reset")
    public String resetPrompt(Model model) throws IOException {
        service.resetPrompt();
        model.addAttribute("personalityPrompt", "");
        model.addAttribute("success", "Personality prompt reset.");
        return "personality";
    }

    /**
     * Reports servlet-level upload size failures.
     *
     * @param model view model for the error and current prompt
     * @return the personality template
     * @throws IOException if the prompt cannot be loaded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(Model model) throws IOException {
        model.addAttribute("error", "Uploads must be 10 MB or smaller.");
        model.addAttribute("personalityPrompt", service.loadPrompt());
        return "personality";
    }
}
