package com.minerva.skills;

import com.minerva.api.skills.SkillPortalController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@Tag("ContinuousIntegration")
class SkillPortalIntegrationTests {
    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SkillImportService service = new SkillImportService(temporaryDirectory.resolve("skills"));
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(new SkillPortalController(service))
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void rendersTheThymeleafSkillPortal() throws Exception {
        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(view().name("skills"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Import a skill")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Public HTTPS ZIP URL")));
    }

    @Test
    void uploadsAndStoresSkillFilesUnderTheConfiguredSkillsDirectory() throws Exception {
        mockMvc.perform(multipart("/skills/upload")
                        .file(skillArchive()))
                .andExpect(status().isOk())
                .andExpect(view().name("skills"))
                .andExpect(model().attribute("success", "Imported skill(s): example"));

        assertTrue(Files.readString(temporaryDirectory.resolve("skills/example/SKILL.md"))
                .contains("# Example skill"));
        assertEquals("Reference content",
                Files.readString(temporaryDirectory.resolve("skills/example/references/guide.md")));
    }

    private MockMultipartFile skillArchive() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "SKILL.md",
                    "---\nname: example\ndescription: Test skill.\n---\n# Example skill\n");
            addEntry(zip, "references/guide.md", "Reference content");
        }
        return new MockMultipartFile("file", "example.zip", "application/zip", output.toByteArray());
    }

    private void addEntry(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
