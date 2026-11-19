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
 * Renders the personality customisation pages and handles their form submissions.
 */
@Controller
public class PersonalityController {
    private final MediaPersonalityService service;
    private final RemoteArticleFetcher articleFetcher;

    /**
     * Creates the controller backed by the personality workflow service.
     *
     * @param service performs media analysis and persists personality preferences
     * @param articleFetcher retrieves and extracts public HTML articles
     */
    public PersonalityController(MediaPersonalityService service, RemoteArticleFetcher articleFetcher) {
        this.service = service;
        this.articleFetcher = articleFetcher;
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
     * Processes an article, single document or image, batch, or ZIP submission.
     *
     * @param file optional single PDF, HTML, or image upload
     * @param htmlFiles optional batch of HTML uploads
     * @param imageFiles optional batch of image uploads
     * @param zipFile optional ZIP of HTML pages and images
     * @param imageUrl optional image URL
     * @param comment optional image-analysis comment
     * @param guidance optional PDF or HTML preference guidance
     * @param articleUrl optional public article URL
     * @param articleComment optional preference guidance about the article
     * @param model view model for the upload result
     * @return the personality template with success or failure feedback
     * @throws IOException if the saved prompt cannot be loaded
     */
    @PostMapping("/personality/upload")
    public String upload(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) List<MultipartFile> htmlFiles,
            @RequestParam(required = false) List<MultipartFile> imageFiles,
            @RequestParam(required = false) MultipartFile zipFile,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(defaultValue = "") String comment,
            @RequestParam(defaultValue = "") String guidance,
            @RequestParam(required = false) String articleUrl,
            @RequestParam(defaultValue = "") String articleComment,
            Model model
    ) throws IOException {
        try {
            if (guidance.length() > 2_000 || comment.length() > 2_000) {
                throw new IllegalArgumentException("Guidance must be 2,000 characters or fewer.");
            }
            if (articleComment.length() > 2_000) {
                throw new IllegalArgumentException("Article comments must be 2,000 characters or fewer.");
            }
            if (imageUrl != null && imageUrl.length() > 2_048) {
                throw new IllegalArgumentException("Image URLs must be 2,048 characters or fewer.");
            }
            if (articleUrl != null && articleUrl.length() > 2_048) {
                throw new IllegalArgumentException("Article URLs must be 2,048 characters or fewer.");
            }
            boolean hasHtmlBatch = htmlFiles != null && !htmlFiles.isEmpty();
            boolean hasImageBatch = imageFiles != null && !imageFiles.isEmpty();
            boolean hasArticleUrl = articleUrl != null && !articleUrl.isBlank();
            boolean hasZip = zipFile != null;
            if (hasZip) {
                if (hasHtmlBatch || hasImageBatch || hasArticleUrl || file != null && !file.isEmpty()
                        || imageUrl != null && !imageUrl.isBlank()) {
                    throw new IllegalArgumentException("Submit a ZIP separately from other files or URLs.");
                }
                var result = service.processZip(zipFile, guidance);
                model.addAttribute("personalityPrompt", result.personalityPrompt());
                if (result.successfulFileCount() > 0) {
                    model.addAttribute("success", "Processed " + result.successfulFileCount()
                            + " ZIP file(s). Saved " + result.savedTagCount()
                            + " interest tags and analysed " + result.analyzedMediaCount() + " media item(s).");
                }
                if (!result.failures().isEmpty()) {
                    model.addAttribute("error", formatBatchFailures(result.failures()));
                }
                return "personality";
            }
            if (hasHtmlBatch && hasImageBatch) {
                throw new IllegalArgumentException("Submit either an HTML batch or an image batch, not both.");
            }
            if (hasArticleUrl) {
                if (hasHtmlBatch || hasImageBatch || file != null && !file.isEmpty()
                        || imageUrl != null && !imageUrl.isBlank()) {
                    throw new IllegalArgumentException("Submit an article URL separately from files or image URLs.");
                }
                var result = service.processArticle(articleFetcher.fetch(articleUrl), articleComment);
                model.addAttribute("success", "Article analysis complete. Personality preferences were updated.");
                model.addAttribute("personalityPrompt", result.personalityPrompt());
                return "personality";
            }
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
            if (hasImageBatch) {
                if (file != null && !file.isEmpty() || imageUrl != null && !imageUrl.isBlank()) {
                    throw new IllegalArgumentException("Submit either an image batch or a single file or image URL, not both.");
                }
                var result = service.processImageBatch(imageFiles, comment);
                model.addAttribute("personalityPrompt", result.personalityPrompt());
                if (result.successfulFileCount() > 0) {
                    model.addAttribute("success", "Processed " + result.successfulFileCount()
                            + " image(s). Saved " + result.savedTagCount()
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
        return "Some files could not be processed: " + failures.stream()
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
     * Reports requests that exceed the server-configured aggregate upload size.
     *
     * @param model view model for the error and current prompt
     * @return the personality template
     * @throws IOException if the prompt cannot be loaded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(Model model) throws IOException {
        model.addAttribute("error", "The total upload request exceeds the server's configured size limit.");
        model.addAttribute("personalityPrompt", service.loadPrompt());
        return "personality";
    }

}
