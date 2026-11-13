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
            @RequestParam(defaultValue = "") String guidance,
            Model model
    ) throws IOException {
        try {
            if (guidance.length() > 2_000) {
                throw new IllegalArgumentException("Guidance must be 2,000 characters or fewer.");
            }
            var result = service.process(file, guidance);
            model.addAttribute("success", "Analysis complete. Saved " + result.savedTagCount()
                    + " interest tags and analyzed " + result.analyzedMediaCount() + " referenced media item(s).");
            model.addAttribute("personalityPrompt", result.personalityPrompt());
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("personalityPrompt", service.loadPrompt());
        }
        return "personality";
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
