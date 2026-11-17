package com.minerva.api.skills;

import com.minerva.skills.SkillImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
public class SkillPortalController {
    private final SkillImportService service;

    public SkillPortalController(SkillImportService service) {
        this.service = service;
    }

    @GetMapping("/skills")
    public String page() {
        return "skills";
    }

    @PostMapping("/skills/upload")
    public String upload(@RequestParam(required = false) MultipartFile file, Model model) {
        try {
            addSuccess(model, service.importArchive(file));
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "skills";
    }

    @PostMapping("/skills/import")
    public String importFromSource(@RequestParam(required = false) String sourceUrl, Model model) {
        try {
            addSuccess(model, service.importFromSource(sourceUrl));
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "skills";
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(Model model) {
        model.addAttribute("error", "ZIP archives must be 10 MB or smaller.");
        return "skills";
    }

    private void addSuccess(Model model, List<String> names) {
        model.addAttribute("success", "Imported skill(s): " + String.join(", ", names));
    }
}
