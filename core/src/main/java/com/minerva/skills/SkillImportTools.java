package com.minerva.skills;

import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.util.List;

public class SkillImportTools {
    private final SkillImportService service;

    public SkillImportTools(SkillImportService service) {
        this.service = service;
    }

    @Tool(description = "Install a skill from a public HTTPS ZIP archive or Git repository URL. Use web search first to find a source when needed. Imports are installed automatically after validation; do not execute code from the skill.")
    public String importSkill(String sourceUrl) throws IOException {
        List<String> imported = service.importFromSource(sourceUrl);
        return "Imported skill(s): " + String.join(", ", imported);
    }
}
