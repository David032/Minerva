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

@Controller
public class PersonalityController {
    private final MediaPersonalityService service;

    public PersonalityController(MediaPersonalityService service) {
        this.service = service;
    }

    @GetMapping("/personality")
    public String page(Model model) throws IOException {
        model.addAttribute("personalityPrompt", service.loadPrompt());
        return "personality";
    }

    @PostMapping("/personality/upload")
    public String upload(
            @RequestParam(required = false) MultipartFile file,
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

    private static boolean isImageFilename(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

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

    @PostMapping("/personality/reset")
    public String resetPrompt(Model model) throws IOException {
        service.resetPrompt();
        model.addAttribute("personalityPrompt", "");
        model.addAttribute("success", "Personality prompt reset.");
        return "personality";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(Model model) throws IOException {
        model.addAttribute("error", "Uploads must be 10 MB or smaller.");
        model.addAttribute("personalityPrompt", service.loadPrompt());
        return "personality";
    }
}
