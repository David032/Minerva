package com.minerva.skills;

import org.springaicommunity.agent.tools.SkillsTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class SkillImportServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void importsRootSkillFromZip() throws Exception {
        SkillImportService service = service();
        MockMultipartFile archive = archive("example.zip", Map.of(
                "SKILL.md", skill("example", "# Example skill\n"),
                "references/guide.md", "Use this guide."
        ));

        assertEquals(List.of("example"), service.importArchive(archive));
        assertTrue(Files.readString(temporaryDirectory.resolve("skills/example/SKILL.md"))
                .contains("# Example skill"));
        assertEquals("Use this guide.",
                Files.readString(temporaryDirectory.resolve("skills/example/references/guide.md")));
    }

    @Test
    void importsNestedSkillsAndUsesTheirDirectoryNames() throws Exception {
        SkillImportService service = service();
        MockMultipartFile archive = archive("repository.zip", Map.of(
                "repository/skills/calendar/SKILL.md", skill("calendar", "Calendar skill"),
                "repository/skills/research/SKILL.md", skill("research", "Research skill")
        ));

        assertEquals(List.of("calendar", "research"), service.importArchive(archive));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("skills/calendar/SKILL.md")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("skills/research/SKILL.md")));
    }

    @Test
    void importsRootAndNestedSkillsAsSeparateDirectories() throws Exception {
        SkillImportService service = service();
        MockMultipartFile archive = archive("repository.zip", Map.of(
                "SKILL.md", skill("root", "Root skill"),
                "nested/SKILL.md", skill("nested", "Nested skill")
        ));

        assertEquals(List.of("nested", "root"), service.importArchive(archive));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("skills/root/SKILL.md")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("skills/nested/SKILL.md")));
        assertFalse(Files.exists(temporaryDirectory.resolve("skills/root/nested")));
    }

    @Test
    void rejectsTraversalBeforeWritingOutsideTheTemporaryDirectory() throws Exception {
        SkillImportService service = service();
        MockMultipartFile archive = archive("unsafe.zip", Map.of(
                "../outside.txt", "unsafe",
                "safe/SKILL.md", skill("safe", "Safe skill")
        ));

        assertThrows(IllegalArgumentException.class, () -> service.importArchive(archive));
        assertFalse(Files.exists(temporaryDirectory.resolve("outside.txt")));
        assertFalse(Files.exists(temporaryDirectory.resolve("skills/safe")));
    }

    @Test
    void rejectsNameCollisionWithoutPartiallyInstallingOtherSkills() throws Exception {
        SkillImportService service = service();
        Path existing = temporaryDirectory.resolve("skills/taken");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("SKILL.md"), skill("taken", "Keep this version"));
        MockMultipartFile archive = archive("repository.zip", Map.of(
                "repository/first/SKILL.md", skill("first", "First skill"),
                "repository/taken/SKILL.md", skill("taken", "Replacement")
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.importArchive(archive)
        );

        assertTrue(error.getMessage().contains("already installed"));
        assertFalse(Files.exists(temporaryDirectory.resolve("skills/first")));
        assertTrue(Files.readString(existing.resolve("SKILL.md")).contains("Keep this version"));
    }

    @Test
    void rejectsNonHttpsAndPrivateNetworkSources() {
        SkillImportService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.importFromSource("http://example.com/skill.zip"));
        assertThrows(IllegalArgumentException.class,
                () -> service.importFromSource("https://127.0.0.1/skill.zip"));
        assertThrows(IllegalArgumentException.class,
                () -> service.importFromSource("https://100.64.0.1/skill.zip"));
        assertThrows(IllegalArgumentException.class,
                () -> service.importFromSource("https://[fc00::1]/skill.zip"));
    }

    @Test
    void rejectsSkillsWithoutUsableFrontMatter() throws Exception {
        SkillImportService service = service();
        MockMultipartFile archive = archive("invalid.zip", Map.of(
                "SKILL.md", "# Missing name and description\n"
        ));

        assertThrows(IllegalArgumentException.class, () -> service.importArchive(archive));
        assertFalse(Files.exists(temporaryDirectory.resolve("skills")));
    }

    @Test
    void rejectsDuplicateSkillNamesAndUnsafeMetadata() throws Exception {
        SkillImportService service = service();
        MockMultipartFile duplicateNames = archive("duplicates.zip", Map.of(
                "first/SKILL.md", skill("same", "First"),
                "second/SKILL.md", skill("same", "Second")
        ));
        MockMultipartFile unsafeMetadata = archive("unsafe-metadata.zip", Map.of(
                "SKILL.md", "---\nname: unsafe\ndescription: <inject>\n---\nContent"
        ));

        assertThrows(IllegalArgumentException.class, () -> service.importArchive(duplicateNames));
        assertThrows(IllegalArgumentException.class, () -> service.importArchive(unsafeMetadata));
        assertFalse(Files.exists(temporaryDirectory.resolve("skills")));
    }

    @Test
    void reloadsTheAssistantAfterInstallingSkills() throws Exception {
        SkillImportService service = service();
        AtomicBoolean reloaded = new AtomicBoolean();
        service.setSkillChangeListener(() -> {
            SkillsTool.builder()
                    .addSkillsDirectory(temporaryDirectory.resolve("skills").toString())
                    .build();
            reloaded.set(true);
        });

        service.importArchive(archive("example.zip", Map.of(
                "SKILL.md", skill("example", "Example skill")
        )));

        assertTrue(reloaded.get());
    }

    @Test
    void aiImportToolReportsSkillsInstalledByTheSharedService() throws Exception {
        SkillImportService service = mock(SkillImportService.class);
        when(service.importFromSource("https://example.com/skill.zip")).thenReturn(List.of("example"));
        SkillImportTools tools = new SkillImportTools(service);

        assertEquals("Imported skill(s): example", tools.importSkill("https://example.com/skill.zip"));
        verify(service).importFromSource("https://example.com/skill.zip");
    }

    private SkillImportService service() {
        return new SkillImportService(temporaryDirectory.resolve("skills"));
    }

    private MockMultipartFile archive(String filename, Map<String, String> files) throws IOException {
        Map<String, String> orderedFiles = new LinkedHashMap<>(files);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> file : orderedFiles.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", filename, "application/zip", output.toByteArray());
    }

    private String skill(String name, String content) {
        return "---\nname: " + name + "\ndescription: Test skill.\n---\n" + content;
    }
}
